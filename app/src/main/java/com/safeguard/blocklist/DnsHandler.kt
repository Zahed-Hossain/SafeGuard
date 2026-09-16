package com.safeguard.blocklist

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Result of inspecting and processing a DNS query packet.
 */
sealed class DnsProcessingResult {
    data class Blocked(
        val domain: String,
        val category: BlockedCategory?,
        val dnsResponsePayload: ByteArray,
        val reason: String
    ) : DnsProcessingResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Blocked) return false
            return domain == other.domain && category == other.category &&
                    dnsResponsePayload.contentEquals(other.dnsResponsePayload)
        }
        override fun hashCode(): Int = domain.hashCode()
    }

    data class SafeSearchRedirected(
        val domain: String,
        val target: String,
        val dnsResponsePayload: ByteArray
    ) : DnsProcessingResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SafeSearchRedirected) return false
            return domain == other.domain && target == other.target &&
                    dnsResponsePayload.contentEquals(other.dnsResponsePayload)
        }
        override fun hashCode(): Int = domain.hashCode()
    }

    data class CanaryResponse(
        val domain: String,
        val dnsResponsePayload: ByteArray
    ) : DnsProcessingResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CanaryResponse) return false
            return domain == other.domain && dnsResponsePayload.contentEquals(other.dnsResponsePayload)
        }
        override fun hashCode(): Int = domain.hashCode()
    }

    data class Allowed(
        val domain: String,
        val isWhitelisted: Boolean = false
    ) : DnsProcessingResult()

    data class Malformed(
        val error: String
    ) : DnsProcessingResult()
}

data class ParsedDnsQuestion(
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val questionEndOffset: Int
)

data class ParsedDnsHeader(
    val transactionId: Int,
    val flags: Int,
    val isQuery: Boolean,
    val questionCount: Int
)

/**
 * Robust DNS packet parser, domain extractor, and response generator.
 *
 * Adheres strictly to RFC 1035:
 * - Bounds checking on every read to protect against malformed or truncated packets.
 * - Pointer loop mitigation (max jump limit) when parsing domain names.
 * - Generates standard, compliant DNS blocked responses (sinkhole 0.0.0.0 or NXDOMAIN).
 * - Generates full IPv4/UDP loopback packets for the VPN TUN interface.
 */
class DnsHandler(
    private val blocklistManager: BlocklistManager,
    var safeSearchEnabled: Boolean = true
) {

    companion object {
        private const val TAG = "DnsHandler"
        const val DNS_PORT = 53

        // Firefox DoH Canary Domain (RFC / Mozilla standard: returning NXDOMAIN signals
        // that network-level filtering is active and tells Firefox to disable DoH)
        const val FIREFOX_CANARY_DOMAIN = "use-application-dns.net"

        // DNS Query Types
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        const val TYPE_HTTPS = 65

        // DNS Return Codes
        const val RCODE_NOERROR = 0
        const val RCODE_NXDOMAIN = 3
    }

    /**
     * Processes a raw DNS payload (the UDP payload of a DNS packet).
     */
    fun processDnsPayload(data: ByteArray, offset: Int = 0, length: Int = data.size): DnsProcessingResult {
        if (length < 12) {
            return DnsProcessingResult.Malformed("DNS packet too short (${length} bytes, minimum 12)")
        }

        val header = parseHeader(data, offset)
            ?: return DnsProcessingResult.Malformed("Unable to parse DNS header")

        if (!header.isQuery) {
            return DnsProcessingResult.Malformed("Packet is a DNS response, not a query")
        }

        if (header.questionCount <= 0) {
            return DnsProcessingResult.Malformed("DNS query has 0 questions")
        }

        val question = parseQuestion(data, offset + 12, offset + length)
            ?: return DnsProcessingResult.Malformed("Unable to parse DNS question")

        val normalizedDomain = DomainMatcher.normalize(question.domain)
        if (normalizedDomain.isEmpty()) {
            return DnsProcessingResult.Malformed("Parsed domain is empty or invalid")
        }

        // 1. Firefox Canary Domain Check (Disables browser DoH bypass)
        if (normalizedDomain.equals(FIREFOX_CANARY_DOMAIN, ignoreCase = true)) {
            val nxResponse = createNxDomainResponse(data, offset, header, question)
            return DnsProcessingResult.CanaryResponse(
                domain = normalizedDomain,
                dnsResponsePayload = nxResponse
            )
        }

        // 2. SafeSearch Enforcement Check
        if (safeSearchEnabled) {
            val redirect = SafeSearchManager.getSafeSearchRedirect(normalizedDomain)
            if (redirect != null) {
                val ipBytes = if (question.qType == TYPE_AAAA) {
                    redirect.ipv6Address
                } else {
                    redirect.ipv4Address
                }

                // If non-zero address available for query type, synthesize SafeSearch response
                val isNonZero = ipBytes.any { it != 0.toByte() }
                if (isNonZero) {
                    val safeSearchPayload = createAddressResponse(
                        data = data,
                        offset = offset,
                        header = header,
                        question = question,
                        addressBytes = ipBytes
                    )
                    return DnsProcessingResult.SafeSearchRedirected(
                        domain = normalizedDomain,
                        target = redirect.canonicalDomain,
                        dnsResponsePayload = safeSearchPayload
                    )
                }
            }
        }

        // 3. Check Whitelist & Blocklist via BlocklistManager / DomainFilter
        val filterResult = blocklistManager.checkDomain(normalizedDomain)

        return if (filterResult.isBlocked) {
            val blockedResponse = createBlockedResponse(
                data = data,
                offset = offset,
                header = header,
                question = question
            )
            DnsProcessingResult.Blocked(
                domain = normalizedDomain,
                category = filterResult.category,
                dnsResponsePayload = blockedResponse,
                reason = filterResult.reason
            )
        } else {
            DnsProcessingResult.Allowed(
                domain = normalizedDomain,
                isWhitelisted = filterResult.isWhitelisted
            )
        }
    }

    /**
     * Parses the 12-byte DNS header safely.
     */
    fun parseHeader(data: ByteArray, offset: Int): ParsedDnsHeader? {
        if (data.size < offset + 12) return null

        val id = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        val flags = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
        val isQuery = (flags and 0x8000) == 0
        val qdCount = ((data[offset + 4].toInt() and 0xFF) shl 8) or (data[offset + 5].toInt() and 0xFF)

        return ParsedDnsHeader(
            transactionId = id,
            flags = flags,
            isQuery = isQuery,
            questionCount = qdCount
        )
    }

    /**
     * Safely parses the DNS question section, extracting the query domain name.
     * Guards against buffer overruns and pointer loops.
     */
    fun parseQuestion(data: ByteArray, startOffset: Int, endOffset: Int): ParsedDnsQuestion? {
        var pos = startOffset
        val domainParts = mutableListOf<String>()
        var jumped = false
        var jumps = 0
        val maxJumps = 5
        var questionEndPos = -1

        try {
            while (pos < endOffset) {
                val len = data[pos].toInt() and 0xFF

                if (len == 0) {
                    pos++
                    if (!jumped) questionEndPos = pos
                    break
                }

                // Check for DNS compression pointer (starts with 11xxxxxx)
                if ((len and 0xC0) == 0xC0) {
                    if (pos + 1 >= endOffset) return null
                    if (!jumped) {
                        questionEndPos = pos + 2
                        jumped = true
                    }
                    val pointerOffset = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    pos = pointerOffset
                    jumps++
                    if (jumps > maxJumps || pos >= endOffset) {
                        return null // Circular pointer loop or out of bounds
                    }
                    continue
                }

                // Standard label
                pos++
                if (pos + len > endOffset) return null // Label exceeds packet boundaries

                val labelBytes = ByteArray(len)
                System.arraycopy(data, pos, labelBytes, 0, len)
                domainParts.add(String(labelBytes, Charsets.US_ASCII))
                pos += len
            }

            val finalEndOffset = if (questionEndPos != -1) questionEndPos else pos

            // Ensure QTYPE and QCLASS (4 bytes) exist
            if (finalEndOffset + 4 > endOffset) return null

            val qType = ((data[finalEndOffset].toInt() and 0xFF) shl 8) or
                    (data[finalEndOffset + 1].toInt() and 0xFF)
            val qClass = ((data[finalEndOffset + 2].toInt() and 0xFF) shl 8) or
                    (data[finalEndOffset + 3].toInt() and 0xFF)

            val domain = domainParts.joinToString(".")
            return ParsedDnsQuestion(
                domain = domain,
                qType = qType,
                qClass = qClass,
                questionEndOffset = finalEndOffset + 4
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing DNS question", e)
            return null
        }
    }

    /**
     * Synthesizes an NXDOMAIN response (e.g. for Firefox Canary domain use-application-dns.net
     * to disable browser DoH and signal network filtering).
     */
    fun createNxDomainResponse(
        data: ByteArray,
        offset: Int,
        header: ParsedDnsHeader,
        question: ParsedDnsQuestion
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Transaction ID
        out.write((header.transactionId shr 8) and 0xFF)
        out.write(header.transactionId and 0xFF)

        // 2. Flags: QR=1 (response), AA=1, RD=from query, RA=1, RCODE=NXDOMAIN (3)
        val rdBit = header.flags and 0x0100
        val flags = 0x8180 or rdBit or RCODE_NXDOMAIN
        out.write((flags shr 8) and 0xFF)
        out.write(flags and 0xFF)

        // 3. Counts: QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)

        // 4. Echo Question Section from query
        val questionLength = question.questionEndOffset - (offset + 12)
        if (questionLength > 0 && offset + 12 + questionLength <= data.size) {
            out.write(data, offset + 12, questionLength)
        }

        return out.toByteArray()
    }

    /**
     * Synthesizes an address response (e.g. for SafeSearch IP redirection).
     */
    fun createAddressResponse(
        data: ByteArray,
        offset: Int,
        header: ParsedDnsHeader,
        question: ParsedDnsQuestion,
        addressBytes: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Transaction ID
        out.write((header.transactionId shr 8) and 0xFF)
        out.write(header.transactionId and 0xFF)

        // 2. Flags: QR=1, AA=1, RD, RA, RCODE=NOERROR (0)
        val rdBit = header.flags and 0x0100
        val flags = 0x8180 or rdBit or RCODE_NOERROR
        out.write((flags shr 8) and 0xFF)
        out.write(flags and 0xFF)

        // 3. Counts: QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)

        // 4. Echo Question Section from query
        val questionLength = question.questionEndOffset - (offset + 12)
        if (questionLength > 0 && offset + 12 + questionLength <= data.size) {
            out.write(data, offset + 12, questionLength)
        }

        // 5. Answer Section
        // Pointer to QNAME at offset 12 (0xC00C)
        out.write(0xC0); out.write(0x0C)

        // TYPE
        out.write((question.qType shr 8) and 0xFF)
        out.write(question.qType and 0xFF)

        // CLASS - IN (1)
        out.write(0x00); out.write(0x01)

        // TTL - 300 seconds
        out.write(0x00); out.write(0x00); out.write(0x01); out.write(0x2C)

        // RDLENGTH
        out.write((addressBytes.size shr 8) and 0xFF)
        out.write(addressBytes.size and 0xFF)

        // RDATA
        out.write(addressBytes)

        return out.toByteArray()
    }

    /**
     * Synthesizes a standard RFC-compliant DNS response that blocks access to the domain.
     * For A queries: Returns 0.0.0.0 sinkhole.
     * For AAAA queries: Returns :: sinkhole (16 bytes of 0).
     * For other queries: Returns NXDOMAIN (RCODE = 3).
     */
    fun createBlockedResponse(
        data: ByteArray,
        offset: Int,
        header: ParsedDnsHeader,
        question: ParsedDnsQuestion
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Transaction ID (2 bytes)
        out.write((header.transactionId shr 8) and 0xFF)
        out.write(header.transactionId and 0xFF)

        // 2. Flags (2 bytes)
        // Standard response: QR=1 (response), AA=1, RD=from query, RA=1
        val rdBit = header.flags and 0x0100
        val isSinkhole = (question.qType == TYPE_A || question.qType == TYPE_AAAA)
        val rcode = if (isSinkhole) RCODE_NOERROR else RCODE_NXDOMAIN
        val flags = 0x8180 or rdBit or rcode
        out.write((flags shr 8) and 0xFF)
        out.write(flags and 0xFF)

        // 3. Counts
        // QDCOUNT = 1
        out.write(0x00); out.write(0x01)
        // ANCOUNT = 1 if sinkhole, 0 if NXDOMAIN
        if (isSinkhole) {
            out.write(0x00); out.write(0x01)
        } else {
            out.write(0x00); out.write(0x00)
        }
        // NSCOUNT = 0
        out.write(0x00); out.write(0x00)
        // ARCOUNT = 0
        out.write(0x00); out.write(0x00)

        // 4. Echo Question Section from query
        val questionLength = question.questionEndOffset - (offset + 12)
        if (questionLength > 0 && offset + 12 + questionLength <= data.size) {
            out.write(data, offset + 12, questionLength)
        }

        // 5. Answer Section (if sinkhole)
        if (isSinkhole) {
            // Pointer to QNAME at offset 12 (0xC00C)
            out.write(0xC0); out.write(0x0C)

            // TYPE (2 bytes)
            out.write((question.qType shr 8) and 0xFF)
            out.write(question.qType and 0xFF)

            // CLASS (2 bytes) - IN (1)
            out.write(0x00); out.write(0x01)

            // TTL (4 bytes) - 300 seconds
            out.write(0x00); out.write(0x00); out.write(0x01); out.write(0x2C)

            if (question.qType == TYPE_A) {
                // RDLENGTH = 4
                out.write(0x00); out.write(0x04)
                // RDATA: 0.0.0.0
                out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00)
            } else if (question.qType == TYPE_AAAA) {
                // RDLENGTH = 16
                out.write(0x00); out.write(0x10)
                // RDATA: :: (16 zeros)
                repeat(16) { out.write(0x00) }
            }
        }

        return out.toByteArray()
    }

    /**
     * Resolves an allowed DNS query via standard upstream DNS (e.g. Cloudflare 1.1.1.3).
     * Protected against timeouts and socket errors.
     */
    fun resolveUpstream(
        dnsQuery: ByteArray,
        upstreamIp: String = "1.1.1.3",
        timeoutMs: Int = 2500
    ): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }
            val address = InetAddress.getByName(upstreamIp)
            val sendPacket = DatagramPacket(dnsQuery, dnsQuery.size, address, DNS_PORT)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(1500)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseBytes = ByteArray(receivePacket.length)
            System.arraycopy(receiveBuffer, 0, responseBytes, 0, receivePacket.length)
            responseBytes
        } catch (e: Exception) {
            Log.w(TAG, "Upstream DNS resolution failed for $upstreamIp: ${e.message}")
            null
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Builds a full IPv4/UDP packet containing a synthesized DNS response,
     * suitable for writing directly back to the VPN TUN interface.
     *
     * Swaps source and destination IP and ports, and calculates proper IPv4 checksum.
     */
    fun buildIpv4UdpResponsePacket(
        originalIpPacket: ByteArray,
        ipHeaderLen: Int,
        dnsResponsePayload: ByteArray
    ): ByteArray? {
        if (originalIpPacket.size < ipHeaderLen + 8) return null

        val udpHeaderOffset = ipHeaderLen
        val totalLen = ipHeaderLen + 8 + dnsResponsePayload.size
        val responsePacket = ByteArray(totalLen)

        // Copy IP Header
        System.arraycopy(originalIpPacket, 0, responsePacket, 0, ipHeaderLen)

        // Update Total Length in IP header (bytes 2 and 3)
        responsePacket[2] = ((totalLen shr 8) and 0xFF).toByte()
        responsePacket[3] = (totalLen and 0xFF).toByte()

        // Swap Source and Destination IP
        // Original Source IP is at offset 12..15, Destination IP at 16..19
        System.arraycopy(originalIpPacket, 16, responsePacket, 12, 4)
        System.arraycopy(originalIpPacket, 12, responsePacket, 16, 4)

        // Recompute IP Checksum (set to 0 first)
        responsePacket[10] = 0
        responsePacket[11] = 0
        val ipChecksum = computeIpChecksum(responsePacket, 0, ipHeaderLen)
        responsePacket[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        responsePacket[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header (8 bytes)
        // Swap Source Port and Destination Port
        // Original Src Port: udpHeaderOffset..udpHeaderOffset+1
        // Original Dst Port: udpHeaderOffset+2..udpHeaderOffset+3
        responsePacket[udpHeaderOffset] = originalIpPacket[udpHeaderOffset + 2]
        responsePacket[udpHeaderOffset + 1] = originalIpPacket[udpHeaderOffset + 3]
        responsePacket[udpHeaderOffset + 2] = originalIpPacket[udpHeaderOffset]
        responsePacket[udpHeaderOffset + 3] = originalIpPacket[udpHeaderOffset + 1]

        // UDP Length (8 + payload length)
        val udpLength = 8 + dnsResponsePayload.size
        responsePacket[udpHeaderOffset + 4] = ((udpLength shr 8) and 0xFF).toByte()
        responsePacket[udpHeaderOffset + 5] = (udpLength and 0xFF).toByte()

        // UDP Checksum (0 is valid in IPv4 UDP RFC 768)
        responsePacket[udpHeaderOffset + 6] = 0
        responsePacket[udpHeaderOffset + 7] = 0

        // Copy DNS Payload
        System.arraycopy(dnsResponsePayload, 0, responsePacket, udpHeaderOffset + 8, dnsResponsePayload.size)

        return responsePacket
    }

    /**
     * Builds a full IPv6/UDP packet containing a synthesized DNS response,
     * suitable for writing directly back to the VPN TUN interface.
     *
     * Swaps source and destination IPv6 addresses and ports, and calculates the
     * mandatory IPv6 UDP checksum using the IPv6 pseudo-header (RFC 2460 / RFC 8200).
     */
    fun buildIpv6UdpResponsePacket(
        originalIpPacket: ByteArray,
        dnsResponsePayload: ByteArray
    ): ByteArray? {
        val ipv6HeaderLen = 40
        if (originalIpPacket.size < ipv6HeaderLen + 8) return null

        val udpHeaderOffset = ipv6HeaderLen
        val udpLength = 8 + dnsResponsePayload.size
        val totalLen = ipv6HeaderLen + udpLength
        val responsePacket = ByteArray(totalLen)

        // Copy first 4 bytes (Version 6, Traffic Class, Flow Label)
        System.arraycopy(originalIpPacket, 0, responsePacket, 0, 4)

        // Payload Length in IPv6 header (2 bytes) = udpLength
        responsePacket[4] = ((udpLength shr 8) and 0xFF).toByte()
        responsePacket[5] = (udpLength and 0xFF).toByte()

        // Next Header: 17 (UDP)
        responsePacket[6] = 17.toByte()

        // Hop Limit: 64
        responsePacket[7] = 64.toByte()

        // Swap Source and Destination IPv6 addresses
        // Original Source IP is at 8..23, Destination IP is at 24..39
        System.arraycopy(originalIpPacket, 24, responsePacket, 8, 16)
        System.arraycopy(originalIpPacket, 8, responsePacket, 24, 16)

        // Swap UDP Source and Destination Ports
        responsePacket[udpHeaderOffset] = originalIpPacket[udpHeaderOffset + 2]
        responsePacket[udpHeaderOffset + 1] = originalIpPacket[udpHeaderOffset + 3]
        responsePacket[udpHeaderOffset + 2] = originalIpPacket[udpHeaderOffset]
        responsePacket[udpHeaderOffset + 3] = originalIpPacket[udpHeaderOffset + 1]

        // UDP Length
        responsePacket[udpHeaderOffset + 4] = ((udpLength shr 8) and 0xFF).toByte()
        responsePacket[udpHeaderOffset + 5] = (udpLength and 0xFF).toByte()

        // UDP Checksum initialized to 0
        responsePacket[udpHeaderOffset + 6] = 0
        responsePacket[udpHeaderOffset + 7] = 0

        // Copy DNS response payload
        System.arraycopy(dnsResponsePayload, 0, responsePacket, udpHeaderOffset + 8, dnsResponsePayload.size)

        // Compute mandatory IPv6 UDP checksum
        val checksum = computeIpv6UdpChecksum(
            srcIp = responsePacket, srcIpOffset = 8,
            dstIp = responsePacket, dstIpOffset = 24,
            udpPacket = responsePacket, udpOffset = udpHeaderOffset, udpLen = udpLength
        )
        responsePacket[udpHeaderOffset + 6] = ((checksum shr 8) and 0xFF).toByte()
        responsePacket[udpHeaderOffset + 7] = (checksum and 0xFF).toByte()

        return responsePacket
    }

    private fun computeIpv6UdpChecksum(
        srcIp: ByteArray, srcIpOffset: Int,
        dstIp: ByteArray, dstIpOffset: Int,
        udpPacket: ByteArray, udpOffset: Int, udpLen: Int
    ): Int {
        var sum = 0L

        // 1. Pseudo-header: Source IPv6 (16 bytes, 8 words)
        for (i in 0 until 16 step 2) {
            val word = ((srcIp[srcIpOffset + i].toInt() and 0xFF) shl 8) or
                    (srcIp[srcIpOffset + i + 1].toInt() and 0xFF)
            sum += word
        }

        // 2. Pseudo-header: Destination IPv6 (16 bytes, 8 words)
        for (i in 0 until 16 step 2) {
            val word = ((dstIp[dstIpOffset + i].toInt() and 0xFF) shl 8) or
                    (dstIp[dstIpOffset + i + 1].toInt() and 0xFF)
            sum += word
        }

        // 3. Upper Layer Packet Length (32-bit int)
        sum += (udpLen and 0xFFFF)

        // 4. Next Header (32-bit int: 17 for UDP)
        sum += 17

        // 5. UDP Header and Payload
        var i = 0
        while (i < udpLen) {
            val high = udpPacket[udpOffset + i].toInt() and 0xFF
            val low = if (i + 1 < udpLen) udpPacket[udpOffset + i + 1].toInt() and 0xFF else 0
            val word = (high shl 8) or low
            sum += word
            i += 2
        }

        // Fold 32-bit sum to 16-bit
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        var result = (sum.inv().toInt()) and 0xFFFF
        if (result == 0) {
            result = 0xFFFF
        }
        return result
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val high = data[i].toInt() and 0xFF
            val low = if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            val word = (high shl 8) or low
            sum += word
            i += 2
        }
        while (sum > 0xFFFF) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }
}
