package com.safeguard

import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.BlockRule
import com.safeguard.blocklist.BlocklistManager
import com.safeguard.blocklist.DnsHandler
import com.safeguard.blocklist.DnsProcessingResult
import com.safeguard.blocklist.DomainMatcher
import com.safeguard.blocklist.SafeSearchManager
import com.safeguard.vpn.DefaultVpnFilterEngine
import com.safeguard.vpn.TlsSniExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for Part 4:
 * - HTTPS Domain Classification & Normalization
 * - Privacy-Preserving TLS SNI Inspection (No Decryption)
 * - Encrypted DNS (DoH/DoT) Bypass Reduction & Firefox Canary Domain
 * - IPv4 and IPv6 Dual-Stack Filtering (Type A 0.0.0.0 & Type AAAA ::)
 * - SafeSearch Enforcement (Google, Bing, DuckDuckGo, Yahoo)
 * - QUIC / HTTP/3 (UDP 443) Handling
 */
class HttpsAndNetworkHardeningTest {

    private lateinit var blocklistManager: BlocklistManager
    private lateinit var dnsHandler: DnsHandler
    private lateinit var filterEngine: DefaultVpnFilterEngine

    @Before
    fun setUp() {
        blocklistManager = BlocklistManager()
        dnsHandler = DnsHandler(blocklistManager, safeSearchEnabled = true)
        filterEngine = DefaultVpnFilterEngine()
        filterEngine.initialize(blocklistManager, safeSearchEnabled = true)
    }

    // =========================================================================
    // 1. HTTPS DOMAIN CLASSIFICATION & NORMALIZATION
    // =========================================================================

    @Test
    fun testHttpsDomainClassification() {
        // Block rule for test domain
        blocklistManager.addCustomRule(BlockRule(pattern = "known-adult-domain.example", category = BlockedCategory.ADULT))

        // Verifying normalization handles https:// schemes
        assertEquals("known-adult-domain.example", DomainMatcher.normalize("https://known-adult-domain.example"))
        assertEquals("known-adult-domain.example", DomainMatcher.normalize("https://known-adult-domain.example/"))
        assertEquals("known-adult-domain.example", DomainMatcher.normalize("https://known-adult-domain.example:443/login?user=test#top"))

        // Check that checkDomain blocks HTTPS URLs accurately
        val httpsResult = blocklistManager.checkDomain("https://known-adult-domain.example")
        assertTrue("Expected https://known-adult-domain.example to be blocked", httpsResult.isBlocked)
        assertEquals(BlockedCategory.ADULT, httpsResult.category)

        // Subdomain check
        val subResult = blocklistManager.checkDomain("https://sub.known-adult-domain.example/video/123")
        assertTrue("Expected subdomains of adult domain to be blocked", subResult.isBlocked)

        // Allowed domain must not be blocked
        val allowedResult = blocklistManager.checkDomain("https://allowed.test")
        assertFalse(allowedResult.isBlocked)
        assertTrue(allowedResult.isWhitelisted)
    }

    // =========================================================================
    // 2. PRIVACY-PRESERVING TLS SNI EXTRACTION (NO PAYLOAD DECRYPTION)
    // =========================================================================

    @Test
    fun testTlsSniExtractionFromClientHello() {
        // Construct a genuine TLS ClientHello handshake packet containing SNI "blocked.test"
        val clientHello = buildTlsClientHello(sniHost = "blocked.test")

        val extractedDomain = TlsSniExtractor.extractSni(clientHello)
        assertEquals("blocked.test", extractedDomain)

        // Check that the extracted domain is classified as blocked
        val check = blocklistManager.checkDomain(extractedDomain!!)
        assertTrue("Extracted SNI domain should be blocked", check.isBlocked)
    }

    @Test
    fun testTlsSniExtractionWithAllowedDomain() {
        val clientHello = buildTlsClientHello(sniHost = "allowed.test")
        val extractedDomain = TlsSniExtractor.extractSni(clientHello)
        assertEquals("allowed.test", extractedDomain)

        val check = blocklistManager.checkDomain(extractedDomain!!)
        assertFalse("Extracted allowed SNI domain should not be blocked", check.isBlocked)
    }

    @Test
    fun testTlsSniNonTlsPacketSafety() {
        // Non-TLS packet (e.g. plain HTTP GET or random bytes)
        val httpGet = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray(Charsets.US_ASCII)
        assertNull(TlsSniExtractor.extractSni(httpGet))

        // Truncated packet (< 44 bytes)
        val truncated = byteArrayOf(0x16, 0x03, 0x01, 0x00, 0x10)
        assertNull(TlsSniExtractor.extractSni(truncated))
    }

    // =========================================================================
    // 3. ENCRYPTED DNS / FIREFOX CANARY DOMAIN TEST
    // =========================================================================

    @Test
    fun testFirefoxCanaryDomainReturnsNxDomain() {
        // Firefox queries "use-application-dns.net" upon start.
        // Returning NXDOMAIN tells Firefox network filtering is present and disables DoH bypass.
        val query = buildDnsQueryPacket(transactionId = 0x8899, domain = DnsHandler.FIREFOX_CANARY_DOMAIN)

        val result = dnsHandler.processDnsPayload(query)
        assertTrue("Expected CanaryResponse result", result is DnsProcessingResult.CanaryResponse)

        val canary = result as DnsProcessingResult.CanaryResponse
        assertEquals(DnsHandler.FIREFOX_CANARY_DOMAIN, canary.domain)

        // Verify DNS response flags have RCODE = 3 (NXDOMAIN)
        val resp = canary.dnsResponsePayload
        val flags = ((resp[2].toInt() and 0xFF) shl 8) or (resp[3].toInt() and 0xFF)
        val rcode = flags and 0x0F
        assertEquals("RCODE should be NXDOMAIN (3)", 3, rcode)
    }

    // =========================================================================
    // 4. SAFESEARCH ENFORCEMENT ARCHITECTURE
    // =========================================================================

    @Test
    fun testSafeSearchGoogleRedirect() {
        val queryIpv4 = buildDnsQueryPacket(transactionId = 0x1001, domain = "www.google.com", qType = DnsHandler.TYPE_A)
        val resultIpv4 = dnsHandler.processDnsPayload(queryIpv4)

        assertTrue(resultIpv4 is DnsProcessingResult.SafeSearchRedirected)
        val safeSearchResult = resultIpv4 as DnsProcessingResult.SafeSearchRedirected
        assertEquals("forcesafesearch.google.com", safeSearchResult.target)

        // Verify response payload contains Google SafeSearch IPv4 216.239.38.120
        val payload = safeSearchResult.dnsResponsePayload
        val len = payload.size
        // Last 4 bytes of A record answer should be 216.239.38.120
        assertEquals(216.toByte(), payload[len - 4])
        assertEquals(239.toByte(), payload[len - 3])
        assertEquals(38.toByte(), payload[len - 2])
        assertEquals(120.toByte(), payload[len - 1])
    }

    @Test
    fun testSafeSearchBingRedirect() {
        val query = buildDnsQueryPacket(transactionId = 0x1002, domain = "bing.com", qType = DnsHandler.TYPE_A)
        val result = dnsHandler.processDnsPayload(query)

        assertTrue(result is DnsProcessingResult.SafeSearchRedirected)
        val bingResult = result as DnsProcessingResult.SafeSearchRedirected
        assertEquals("strict.bing.com", bingResult.target)

        val payload = bingResult.dnsResponsePayload
        val len = payload.size
        // 204.79.197.220
        assertEquals(204.toByte(), payload[len - 4])
        assertEquals(79.toByte(), payload[len - 3])
        assertEquals(197.toByte(), payload[len - 2])
        assertEquals(220.toByte(), payload[len - 1])
    }

    @Test
    fun testSafeSearchDuckDuckGoRedirect() {
        val query = buildDnsQueryPacket(transactionId = 0x1003, domain = "duckduckgo.com", qType = DnsHandler.TYPE_A)
        val result = dnsHandler.processDnsPayload(query)

        assertTrue(result is DnsProcessingResult.SafeSearchRedirected)
        val ddgResult = result as DnsProcessingResult.SafeSearchRedirected
        assertEquals("safe.duckduckgo.com", ddgResult.target)
    }

    @Test
    fun testSafeSearchDisabledRestoresNormalQuery() {
        dnsHandler.safeSearchEnabled = false
        val query = buildDnsQueryPacket(transactionId = 0x1004, domain = "www.google.com", qType = DnsHandler.TYPE_A)
        val result = dnsHandler.processDnsPayload(query)

        // When SafeSearch is disabled, google.com is evaluated normally (allowed)
        assertTrue("Should be Allowed when SafeSearch is disabled", result is DnsProcessingResult.Allowed)
    }

    // =========================================================================
    // 5. IPV4 & IPV6 DUAL-STACK DNS SINKHOLE
    // =========================================================================

    @Test
    fun testIpv4DnsSinkhole() {
        val queryA = buildDnsQueryPacket(transactionId = 0x2001, domain = "blocked.test", qType = DnsHandler.TYPE_A)
        val result = dnsHandler.processDnsPayload(queryA)

        assertTrue(result is DnsProcessingResult.Blocked)
        val blocked = result as DnsProcessingResult.Blocked
        val payload = blocked.dnsResponsePayload
        val len = payload.size

        // Type A sinkhole returns 0.0.0.0 (4 zero bytes at end of A answer)
        assertEquals(0.toByte(), payload[len - 4])
        assertEquals(0.toByte(), payload[len - 3])
        assertEquals(0.toByte(), payload[len - 2])
        assertEquals(0.toByte(), payload[len - 1])
    }

    @Test
    fun testIpv6DnsSinkhole() {
        val queryAAAA = buildDnsQueryPacket(transactionId = 0x2002, domain = "blocked.test", qType = DnsHandler.TYPE_AAAA)
        val result = dnsHandler.processDnsPayload(queryAAAA)

        assertTrue(result is DnsProcessingResult.Blocked)
        val blocked = result as DnsProcessingResult.Blocked
        val payload = blocked.dnsResponsePayload
        val len = payload.size

        // Type AAAA sinkhole returns :: (16 zero bytes at end of AAAA answer)
        for (i in (len - 16) until len) {
            assertEquals("IPv6 sinkhole byte must be 0", 0.toByte(), payload[i])
        }
    }

    @Test
    fun testBuildIpv6UdpResponsePacket() {
        // Construct mock IPv6 UDP packet: 40 bytes IPv6 + 8 bytes UDP + dummy payload
        val originalIpv6 = ByteArray(56)
        originalIpv6[0] = 0x60.toByte() // IPv6
        originalIpv6[6] = 17.toByte()   // UDP

        // Src IPv6: 2001:db8::1
        originalIpv6[8] = 0x20; originalIpv6[9] = 0x01
        originalIpv6[23] = 0x01

        // Dst IPv6: 2001:db8::2
        originalIpv6[24] = 0x20; originalIpv6[25] = 0x01
        originalIpv6[39] = 0x02

        // Src Port: 54321 (0xD431), Dst Port: 53 (0x0035)
        originalIpv6[40] = 0xD4.toByte(); originalIpv6[41] = 0x31.toByte()
        originalIpv6[42] = 0x00.toByte(); originalIpv6[43] = 0x35.toByte()

        val dnsResponsePayload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val responsePacket = dnsHandler.buildIpv6UdpResponsePacket(originalIpv6, dnsResponsePayload)
        assertNotNull(responsePacket)
        responsePacket!!

        // Verifying swapped IPv6 addresses:
        // New Src should be 2001:db8::2 (original Dst)
        assertEquals(0x02.toByte(), responsePacket[23])
        // New Dst should be 2001:db8::1 (original Src)
        assertEquals(0x01.toByte(), responsePacket[39])

        // Verifying swapped UDP Ports:
        // New Src Port should be 53
        assertEquals(0, responsePacket[40].toInt())
        assertEquals(53, responsePacket[41].toInt())
        // New Dst Port should be 54321
        assertEquals(0xD4.toByte(), responsePacket[42])
        assertEquals(0x31.toByte(), responsePacket[43])

        // Verifying UDP Checksum is calculated and non-zero
        val checksum = ((responsePacket[46].toInt() and 0xFF) shl 8) or (responsePacket[47].toInt() and 0xFF)
        assertTrue("IPv6 UDP checksum must be computed and non-zero", checksum != 0)
    }

    // =========================================================================
    // HELPER BUILDERS
    // =========================================================================

    private fun buildDnsQueryPacket(transactionId: Int, domain: String, qType: Int = DnsHandler.TYPE_A): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((transactionId shr 8) and 0xFF)
        out.write(transactionId and 0xFF)
        out.write(0x01); out.write(0x00) // Standard query
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x00) // ANCOUNT = 0
        out.write(0x00); out.write(0x00) // NSCOUNT = 0
        out.write(0x00); out.write(0x00) // ARCOUNT = 0

        for (part in domain.split('.')) {
            out.write(part.length)
            out.write(part.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00)

        out.write((qType shr 8) and 0xFF)
        out.write(qType and 0xFF)
        out.write(0x00); out.write(0x01) // QCLASS = IN

        return out.toByteArray()
    }

    private fun buildTlsClientHello(sniHost: String): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. TLS Record Header
        out.write(0x16) // Handshake
        out.write(0x03); out.write(0x03) // TLS 1.2 record version

        val handshakeOut = ByteArrayOutputStream()

        // 2. ClientHello Handshake
        handshakeOut.write(0x01) // ClientHello
        // Handshake length placeholder (3 bytes)
        val clientHelloBody = ByteArrayOutputStream()

        // Client version (TLS 1.2 = 0x0303)
        clientHelloBody.write(0x03); clientHelloBody.write(0x03)

        // Random (32 bytes)
        repeat(32) { clientHelloBody.write(0xAA) }

        // Session ID length: 0
        clientHelloBody.write(0x00)

        // Cipher suites length: 2, cipher suite: 0x002F
        clientHelloBody.write(0x00); clientHelloBody.write(0x02)
        clientHelloBody.write(0x00); clientHelloBody.write(0x2F)

        // Compression methods length: 1, method: 0x00
        clientHelloBody.write(0x01)
        clientHelloBody.write(0x00)

        // Extensions
        val extOut = ByteArrayOutputStream()

        // Extension: server_name (0x0000)
        extOut.write(0x00); extOut.write(0x00)

        val hostBytes = sniHost.toByteArray(Charsets.US_ASCII)
        val sniDataOut = ByteArrayOutputStream()
        // Server Name List Length
        val listLen = hostBytes.size + 3
        sniDataOut.write((listLen shr 8) and 0xFF)
        sniDataOut.write(listLen and 0xFF)
        // Name Type: host_name (0x00)
        sniDataOut.write(0x00)
        // Host name length
        sniDataOut.write((hostBytes.size shr 8) and 0xFF)
        sniDataOut.write(hostBytes.size and 0xFF)
        sniDataOut.write(hostBytes)

        val sniData = sniDataOut.toByteArray()
        // Extension length
        extOut.write((sniData.size shr 8) and 0xFF)
        extOut.write(sniData.size and 0xFF)
        extOut.write(sniData)

        val allExt = extOut.toByteArray()
        // Extensions length
        clientHelloBody.write((allExt.size shr 8) and 0xFF)
        clientHelloBody.write(allExt.size and 0xFF)
        clientHelloBody.write(allExt)

        val chBytes = clientHelloBody.toByteArray()
        // Write 3-byte length
        handshakeOut.write(0x00)
        handshakeOut.write((chBytes.size shr 8) and 0xFF)
        handshakeOut.write(chBytes.size and 0xFF)
        handshakeOut.write(chBytes)

        val hsBytes = handshakeOut.toByteArray()
        // Record length (2 bytes)
        out.write((hsBytes.size shr 8) and 0xFF)
        out.write(hsBytes.size and 0xFF)
        out.write(hsBytes)

        return out.toByteArray()
    }
}
