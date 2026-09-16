package com.safeguard.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import com.safeguard.blocklist.BlocklistManager
import com.safeguard.blocklist.DnsHandler
import com.safeguard.blocklist.DnsProcessingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

interface VpnFilterEngine {
    fun initialize(blocklistManager: BlocklistManager, safeSearchEnabled: Boolean = true)
    fun setSafeSearchEnabled(enabled: Boolean)
    fun startFiltering(vpnInterface: ParcelFileDescriptor, onDomainBlocked: (String) -> Unit)
    fun stopFiltering()
    fun isRunning(): Boolean
}

/**
 * High-performance, privacy-preserving packet inspection engine for SafeGuard.
 *
 * Implements:
 * 1. IPv4 and IPv6 Dual-Stack Support.
 * 2. DNS-layer inspection and sinkholing (IPv4 0.0.0.0 and IPv6 ::).
 * 3. Modern HTTPS SNI (Server Name Indication) inspection for TCP 443 without payload decryption.
 * 4. SafeSearch DNS enforcement for Google, Bing, DuckDuckGo, and Yahoo.
 * 5. Firefox DoH Canary Domain (use-application-dns.net) NXDOMAIN signaling to disable browser DoH.
 * 6. Port 853 (DNS-over-TLS / Private DNS) interception to prevent encrypted DNS bypass.
 * 7. QUIC / HTTP/3 fallback handling (RFC 9000) over UDP 443.
 *
 * PRIVACY MANDATE:
 * Absolutely NO TLS MITM, NO HTTPS decryption, NO root certificate installation,
 * and zero inspection of passwords, cookies, private messages, or banking data.
 */
class DefaultVpnFilterEngine : VpnFilterEngine {

    companion object {
        private const val TAG = "DefaultVpnFilterEngine"

        private const val PROTOCOL_TCP = 6
        private const val PROTOCOL_UDP = 17

        private const val PORT_DNS = 53
        private const val PORT_HTTPS = 443
        private const val PORT_DOT = 853
    }

    private var filterJob: Job? = null
    private var blocklistManager: BlocklistManager? = null
    private var dnsHandler: DnsHandler? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var running = false

    override fun initialize(blocklistManager: BlocklistManager, safeSearchEnabled: Boolean) {
        this.blocklistManager = blocklistManager
        this.dnsHandler = DnsHandler(blocklistManager, safeSearchEnabled)
    }

    override fun setSafeSearchEnabled(enabled: Boolean) {
        dnsHandler?.safeSearchEnabled = enabled
    }

    override fun startFiltering(
        vpnInterface: ParcelFileDescriptor,
        onDomainBlocked: (String) -> Unit
    ) {
        if (running) return
        running = true

        val handler = dnsHandler ?: blocklistManager?.let { DnsHandler(it) }.also { dnsHandler = it }

        filterJob = scope.launch {
            try {
                val inputStream = FileInputStream(vpnInterface.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
                val buffer = ByteArray(32767)

                while (isActive && running) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        processPacket(buffer, length, outputStream, handler, onDomainBlocked)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                // Expected when VPN interface is closed or interrupted
                Log.d(TAG, "VPN stream ended: ${e.message}")
            } finally {
                running = false
            }
        }
    }

    /**
     * Inspects IP packets (IPv4 and IPv6) and routes them through DNS filtering,
     * TLS SNI inspection, and protocol-hardening policies.
     */
    fun processPacket(
        buffer: ByteArray,
        length: Int,
        outputStream: FileOutputStream?,
        handler: DnsHandler?,
        onDomainBlocked: (String) -> Unit
    ) {
        if (length < 20 || handler == null) return

        val ipVersion = (buffer[0].toInt() shr 4) and 0x0F

        when (ipVersion) {
            4 -> processIpv4Packet(buffer, length, outputStream, handler, onDomainBlocked)
            6 -> processIpv6Packet(buffer, length, outputStream, handler, onDomainBlocked)
            else -> {
                // Ignore unsupported IP versions
            }
        }
    }

    /**
     * Processes IPv4 packets.
     */
    private fun processIpv4Packet(
        buffer: ByteArray,
        length: Int,
        outputStream: FileOutputStream?,
        handler: DnsHandler,
        onDomainBlocked: (String) -> Unit
    ) {
        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return

        val protocol = buffer[9].toInt() and 0xFF
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)

        when (protocol) {
            PROTOCOL_UDP -> {
                when (dstPort) {
                    PORT_DNS -> {
                        // Standard UDP DNS Query on port 53
                        handleDnsQueryIpv4(buffer, length, ihl, outputStream, handler, onDomainBlocked)
                    }
                    PORT_DOT -> {
                        // Port 853 DoT query: Drop to force fallback to standard filtered DNS
                        Log.d(TAG, "Dropped IPv4 DoT port 853 query to prevent private DNS bypass")
                    }
                    PORT_HTTPS -> {
                        // QUIC / HTTP/3 over UDP port 443
                        // Dropping UDP 443 forces standard browsers to fall back to TCP HTTPS (RFC 9000),
                        // ensuring that adult domain policies are consistently enforced via DNS and TLS SNI.
                        Log.d(TAG, "Preventing QUIC bypass over UDP 443; forcing TCP HTTPS fallback")
                    }
                }
            }
            PROTOCOL_TCP -> {
                when (dstPort) {
                    PORT_DOT -> {
                        // Port 853 TCP DoT: Drop to force fallback to system / VPN DNS
                        Log.d(TAG, "Dropped IPv4 TCP DoT port 853 connection to prevent bypass")
                    }
                    PORT_HTTPS -> {
                        // Inspect TLS ClientHello for SNI hostname without decrypting payload
                        inspectTlsClientHello(buffer, length, ihl, onDomainBlocked)
                    }
                }
            }
        }
    }

    /**
     * Processes IPv6 packets.
     */
    private fun processIpv6Packet(
        buffer: ByteArray,
        length: Int,
        outputStream: FileOutputStream?,
        handler: DnsHandler,
        onDomainBlocked: (String) -> Unit
    ) {
        val ipv6HeaderLen = 40
        if (length < ipv6HeaderLen + 8) return

        val nextHeader = buffer[6].toInt() and 0xFF
        val srcPort = ((buffer[ipv6HeaderLen].toInt() and 0xFF) shl 8) or (buffer[ipv6HeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ipv6HeaderLen + 2].toInt() and 0xFF) shl 8) or (buffer[ipv6HeaderLen + 3].toInt() and 0xFF)

        when (nextHeader) {
            PROTOCOL_UDP -> {
                when (dstPort) {
                    PORT_DNS -> {
                        handleDnsQueryIpv6(buffer, length, ipv6HeaderLen, outputStream, handler, onDomainBlocked)
                    }
                    PORT_DOT -> {
                        Log.d(TAG, "Dropped IPv6 DoT port 853 query to prevent private DNS bypass")
                    }
                    PORT_HTTPS -> {
                        Log.d(TAG, "Preventing IPv6 QUIC bypass over UDP 443; forcing TCP HTTPS fallback")
                    }
                }
            }
            PROTOCOL_TCP -> {
                when (dstPort) {
                    PORT_DOT -> {
                        Log.d(TAG, "Dropped IPv6 TCP DoT port 853 connection to prevent bypass")
                    }
                    PORT_HTTPS -> {
                        inspectTlsClientHello(buffer, length, ipv6HeaderLen, onDomainBlocked)
                    }
                }
            }
        }
    }

    /**
     * Handles IPv4 DNS queries on UDP 53.
     */
    private fun handleDnsQueryIpv4(
        buffer: ByteArray,
        length: Int,
        ihl: Int,
        outputStream: FileOutputStream?,
        handler: DnsHandler,
        onDomainBlocked: (String) -> Unit
    ) {
        val dnsPayloadOffset = ihl + 8
        val dnsPayloadLength = length - dnsPayloadOffset
        if (dnsPayloadLength < 12) return

        when (val result = handler.processDnsPayload(buffer, dnsPayloadOffset, dnsPayloadLength)) {
            is DnsProcessingResult.Blocked -> {
                Log.i(TAG, "SafeGuard BLOCKED IPv4 domain: ${result.domain} (${result.category?.displayName})")
                onDomainBlocked(result.domain)

                val responsePacket = handler.buildIpv4UdpResponsePacket(
                    originalIpPacket = buffer,
                    ipHeaderLen = ihl,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.SafeSearchRedirected -> {
                Log.d(TAG, "SafeGuard SafeSearch redirected: ${result.domain} -> ${result.target}")
                val responsePacket = handler.buildIpv4UdpResponsePacket(
                    originalIpPacket = buffer,
                    ipHeaderLen = ihl,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.CanaryResponse -> {
                Log.d(TAG, "SafeGuard Canary response for: ${result.domain} (Disabling DoH)")
                val responsePacket = handler.buildIpv4UdpResponsePacket(
                    originalIpPacket = buffer,
                    ipHeaderLen = ihl,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.Allowed -> {
                Log.d(TAG, "SafeGuard ALLOWED IPv4 domain: ${result.domain}")
                resolveAndForwardUpstream(
                    buffer, dnsPayloadOffset, dnsPayloadLength, ihl, isIpv6 = false, outputStream, handler
                )
            }
            is DnsProcessingResult.Malformed -> {
                Log.w(TAG, "Malformed IPv4 DNS packet: ${result.error}")
            }
        }
    }

    /**
     * Handles IPv6 DNS queries on UDP 53.
     */
    private fun handleDnsQueryIpv6(
        buffer: ByteArray,
        length: Int,
        ipv6HeaderLen: Int,
        outputStream: FileOutputStream?,
        handler: DnsHandler,
        onDomainBlocked: (String) -> Unit
    ) {
        val dnsPayloadOffset = ipv6HeaderLen + 8
        val dnsPayloadLength = length - dnsPayloadOffset
        if (dnsPayloadLength < 12) return

        when (val result = handler.processDnsPayload(buffer, dnsPayloadOffset, dnsPayloadLength)) {
            is DnsProcessingResult.Blocked -> {
                Log.i(TAG, "SafeGuard BLOCKED IPv6 domain: ${result.domain} (${result.category?.displayName})")
                onDomainBlocked(result.domain)

                val responsePacket = handler.buildIpv6UdpResponsePacket(
                    originalIpPacket = buffer,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.SafeSearchRedirected -> {
                Log.d(TAG, "SafeGuard IPv6 SafeSearch redirected: ${result.domain} -> ${result.target}")
                val responsePacket = handler.buildIpv6UdpResponsePacket(
                    originalIpPacket = buffer,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.CanaryResponse -> {
                Log.d(TAG, "SafeGuard IPv6 Canary response: ${result.domain}")
                val responsePacket = handler.buildIpv6UdpResponsePacket(
                    originalIpPacket = buffer,
                    dnsResponsePayload = result.dnsResponsePayload
                )
                writeResponse(outputStream, responsePacket)
            }
            is DnsProcessingResult.Allowed -> {
                Log.d(TAG, "SafeGuard ALLOWED IPv6 domain: ${result.domain}")
                resolveAndForwardUpstream(
                    buffer, dnsPayloadOffset, dnsPayloadLength, ipv6HeaderLen, isIpv6 = true, outputStream, handler
                )
            }
            is DnsProcessingResult.Malformed -> {
                Log.w(TAG, "Malformed IPv6 DNS packet: ${result.error}")
            }
        }
    }

    /**
     * Inspects TCP port 443 packets for unencrypted TLS Server Name Indication (SNI).
     * Does NOT decrypt payload or install certificates.
     */
    private fun inspectTlsClientHello(
        buffer: ByteArray,
        length: Int,
        ipHeaderLen: Int,
        onDomainBlocked: (String) -> Unit
    ) {
        if (length < ipHeaderLen + 20) return

        val tcpHeaderOffset = ipHeaderLen
        val dataOffset = (buffer[tcpHeaderOffset + 12].toInt() shr 4) and 0x0F
        val tcpHeaderLen = dataOffset * 4
        val tcpPayloadOffset = ipHeaderLen + tcpHeaderLen
        val tcpPayloadLen = length - tcpPayloadOffset

        if (tcpPayloadLen <= 0) return

        val sniHost = TlsSniExtractor.extractSni(buffer, tcpPayloadOffset, tcpPayloadLen)
        if (sniHost != null) {
            val filterResult = blocklistManager?.checkDomain(sniHost)
            if (filterResult?.isBlocked == true) {
                Log.i(TAG, "SafeGuard TLS SNI BLOCKED domain: $sniHost (${filterResult.category?.displayName})")
                onDomainBlocked(sniHost)
                // Dropping this initial TLS ClientHello packet prevents the TLS handshake
                // from completing, blocking HTTPS access to the adult domain cleanly and reliably.
            }
        }
    }

    private fun resolveAndForwardUpstream(
        buffer: ByteArray,
        dnsPayloadOffset: Int,
        dnsPayloadLength: Int,
        ipHeaderLen: Int,
        isIpv6: Boolean,
        outputStream: FileOutputStream?,
        handler: DnsHandler
    ) {
        if (outputStream == null) return
        scope.launch {
            val queryBytes = ByteArray(dnsPayloadLength)
            System.arraycopy(buffer, dnsPayloadOffset, queryBytes, 0, dnsPayloadLength)
            val upstreamResponse = handler.resolveUpstream(queryBytes)
            if (upstreamResponse != null) {
                val responsePacket = if (isIpv6) {
                    handler.buildIpv6UdpResponsePacket(buffer, upstreamResponse)
                } else {
                    handler.buildIpv4UdpResponsePacket(buffer, ipHeaderLen, upstreamResponse)
                }
                writeResponse(outputStream, responsePacket)
            }
        }
    }

    private fun writeResponse(outputStream: FileOutputStream?, packet: ByteArray?) {
        if (outputStream == null || packet == null) return
        try {
            synchronized(outputStream) {
                outputStream.write(packet)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write response packet to TUN interface", e)
        }
    }

    override fun stopFiltering() {
        running = false
        filterJob?.cancel()
        filterJob = null
    }

    override fun isRunning(): Boolean = running
}
