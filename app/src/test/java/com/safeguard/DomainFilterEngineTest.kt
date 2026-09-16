package com.safeguard

import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.BlocklistManager
import com.safeguard.blocklist.DnsHandler
import com.safeguard.blocklist.DnsProcessingResult
import com.safeguard.blocklist.DomainFilter
import com.safeguard.blocklist.DomainMatcher
import com.safeguard.blocklist.FilterDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class DomainFilterEngineTest {

    private lateinit var domainFilter: DomainFilter
    private lateinit var blocklistManager: BlocklistManager
    private lateinit var dnsHandler: DnsHandler

    @Before
    fun setUp() {
        domainFilter = DomainFilter()
        blocklistManager = BlocklistManager()
        dnsHandler = DnsHandler(blocklistManager)
    }

    // =========================================================================
    // 1. DOMAIN NORMALIZATION & MATCHING (DomainMatcher)
    // =========================================================================

    @Test
    fun testDomainNormalization() {
        // Scheme stripping
        assertEquals("example.com", DomainMatcher.normalize("http://example.com"))
        assertEquals("example.com", DomainMatcher.normalize("https://example.com"))
        assertEquals("example.com", DomainMatcher.normalize("ftp://example.com/"))
        assertEquals("example.com", DomainMatcher.normalize("//example.com"))

        // Uppercase to lowercase
        assertEquals("example.com", DomainMatcher.normalize("EXAMPLE.COM"))
        assertEquals("sub.example.com", DomainMatcher.normalize("HTTPS://SUB.EXAMPLE.COM/path"))

        // Trailing dot removal
        assertEquals("example.com", DomainMatcher.normalize("example.com."))
        assertEquals("example.com", DomainMatcher.normalize("https://example.com..."))

        // Port & query stripping
        assertEquals("example.com", DomainMatcher.normalize("http://example.com:8080/search?q=adult#top"))

        // Wildcard prefix stripping
        assertEquals("example.com", DomainMatcher.normalize("*.example.com"))
        assertEquals("example.com", DomainMatcher.normalize(".example.com"))

        // WWW helper
        assertEquals("example.com", DomainMatcher.stripWww("www.example.com"))
        assertEquals("sub.example.com", DomainMatcher.stripWww("sub.example.com"))
    }

    @Test
    fun testPunycodeNormalization() {
        // IDN / Punycode conversion for internationalized domains
        val ascii = DomainMatcher.normalize("münchen.de")
        assertEquals("xn--mnchen-3ya.de", ascii)
    }

    @Test
    fun testDomainMatchingHierarchyAndSubdomains() {
        // Subdomain and wildcard matching
        assertTrue(DomainMatcher.matches("example.com", "example.com"))
        assertTrue(DomainMatcher.matches("www.example.com", "example.com"))
        assertTrue(DomainMatcher.matches("video.example.com", "example.com"))
        assertTrue(DomainMatcher.matches("sub.example.com", "example.com"))
        assertTrue(DomainMatcher.matches("a.b.c.example.com", "example.com"))

        // Different domain / TLD should not match
        assertFalse(DomainMatcher.matches("example.org", "example.com"))
        assertFalse(DomainMatcher.matches("google.com", "example.com"))
    }

    @Test
    fun testEvilSubdomainPrevention() {
        // CRITICAL SECURITY RULE: "example.com.evil.com" must NEVER match "example.com"
        assertFalse(DomainMatcher.matches("example.com.evil.com", "example.com"))
        assertFalse(DomainMatcher.matches("notexample.com", "example.com"))
        assertFalse(DomainMatcher.matches("fakeexample.com", "example.com"))
    }

    // =========================================================================
    // 2. WHITELIST PRIORITY OVER BLOCKLIST (DomainFilter)
    // =========================================================================

    @Test
    fun testWhitelistOverridesBlocklist() {
        val filter = DomainFilter()
        filter.clearAll()

        // Block parent domain
        filter.addBlockRule("example.com", BlockedCategory.ADULT)

        // Whitelist specific subdomain
        filter.addWhitelist("sub.example.com")

        // example.com -> BLOCK
        assertEquals(FilterDecision.BLOCK, filter.evaluate("example.com").decision)

        // www.example.com -> BLOCK
        assertEquals(FilterDecision.BLOCK, filter.evaluate("www.example.com").decision)

        // video.example.com -> BLOCK
        assertEquals(FilterDecision.BLOCK, filter.evaluate("video.example.com").decision)

        // sub.example.com -> ALLOW (Whitelist override!)
        val subResult = filter.evaluate("sub.example.com")
        assertEquals(FilterDecision.ALLOW, subResult.decision)
        assertTrue(subResult.isWhitelisted)

        // subdomains of whitelisted domain -> ALLOW
        val nestedSubResult = filter.evaluate("api.sub.example.com")
        assertEquals(FilterDecision.ALLOW, nestedSubResult.decision)
        assertTrue(nestedSubResult.isWhitelisted)

        // example.org -> ALLOW
        assertEquals(FilterDecision.ALLOW, filter.evaluate("example.org").decision)
    }

    // =========================================================================
    // 3. SAFE DEVELOPER TEST DOMAINS
    // =========================================================================

    @Test
    fun testDeveloperTestDomains() {
        // blocked.test -> BLOCK
        val blockedResult = domainFilter.evaluate("blocked.test")
        assertEquals(FilterDecision.BLOCK, blockedResult.decision)

        // www.blocked.test -> BLOCK
        val wwwBlockedResult = domainFilter.evaluate("www.blocked.test")
        assertEquals(FilterDecision.BLOCK, wwwBlockedResult.decision)

        // sub.blocked.test -> BLOCK
        val subBlockedResult = domainFilter.evaluate("sub.blocked.test")
        assertEquals(FilterDecision.BLOCK, subBlockedResult.decision)

        // adult-test.local -> BLOCK
        assertEquals(FilterDecision.BLOCK, domainFilter.evaluate("adult-test.local").decision)

        // example-blocked.test -> BLOCK
        assertEquals(FilterDecision.BLOCK, domainFilter.evaluate("example-blocked.test").decision)

        // allowed.test -> ALLOW
        val allowedResult = domainFilter.evaluate("allowed.test")
        assertEquals(FilterDecision.ALLOW, allowedResult.decision)
        assertTrue(allowedResult.isWhitelisted)
    }

    // =========================================================================
    // 4. ADULT CATEGORIES VERIFICATION
    // =========================================================================

    @Test
    fun testRequiredAdultCategories() {
        val filter = DomainFilter()
        filter.clearAll()

        filter.addBlockRule("adult-site.test", BlockedCategory.ADULT)
        filter.addBlockRule("porn-site.test", BlockedCategory.PORNOGRAPHY)
        filter.addBlockRule("explicit-site.test", BlockedCategory.EXPLICIT)
        filter.addBlockRule("nsfw-site.test", BlockedCategory.NSFW)
        filter.addBlockRule("streaming-site.test", BlockedCategory.ADULT_STREAMING)
        filter.addBlockRule("community-site.test", BlockedCategory.ADULT_COMMUNITY)
        filter.addBlockRule("dating-site.test", BlockedCategory.ADULT_DATING)
        filter.addBlockRule("inappropriate-site.test", BlockedCategory.INAPPROPRIATE)

        assertEquals(BlockedCategory.ADULT, filter.evaluate("adult-site.test").category)
        assertEquals(BlockedCategory.PORNOGRAPHY, filter.evaluate("porn-site.test").category)
        assertEquals(BlockedCategory.EXPLICIT, filter.evaluate("explicit-site.test").category)
        assertEquals(BlockedCategory.NSFW, filter.evaluate("nsfw-site.test").category)
        assertEquals(BlockedCategory.ADULT_STREAMING, filter.evaluate("streaming-site.test").category)
        assertEquals(BlockedCategory.ADULT_COMMUNITY, filter.evaluate("community-site.test").category)
        assertEquals(BlockedCategory.ADULT_DATING, filter.evaluate("dating-site.test").category)
        assertEquals(BlockedCategory.INAPPROPRIATE, filter.evaluate("inappropriate-site.test").category)
    }

    // =========================================================================
    // 5. DNS FILTERING & PACKET PARSING (DnsHandler)
    // =========================================================================

    @Test
    fun testDnsHandlerBlocksQuery() {
        // Build a raw DNS query for "blocked.test"
        val queryPacket = buildDnsQueryPacket(transactionId = 0x1234, domain = "blocked.test")

        val result = dnsHandler.processDnsPayload(queryPacket)
        assertTrue("Expected Blocked result", result is DnsProcessingResult.Blocked)

        val blockedResult = result as DnsProcessingResult.Blocked
        assertEquals("blocked.test", blockedResult.domain)
        assertNotNull(blockedResult.dnsResponsePayload)

        // Verify response header has Transaction ID 0x1234 and Response flag (QR=1)
        val resp = blockedResult.dnsResponsePayload
        val respId = ((resp[0].toInt() and 0xFF) shl 8) or (resp[1].toInt() and 0xFF)
        assertEquals(0x1234, respId)

        val flags = ((resp[2].toInt() and 0xFF) shl 8) or (resp[3].toInt() and 0xFF)
        assertTrue("QR bit should be set in response", (flags and 0x8000) != 0)
    }

    @Test
    fun testDnsHandlerAllowsQuery() {
        val queryPacket = buildDnsQueryPacket(transactionId = 0x5678, domain = "allowed.test")

        val result = dnsHandler.processDnsPayload(queryPacket)
        assertTrue("Expected Allowed result", result is DnsProcessingResult.Allowed)

        val allowedResult = result as DnsProcessingResult.Allowed
        assertEquals("allowed.test", allowedResult.domain)
        assertTrue(allowedResult.isWhitelisted)
    }

    @Test
    fun testDnsHandlerProtectsAgainstMalformedPackets() {
        // 1. Packet too short (< 12 bytes)
        val shortPacket = byteArrayOf(0x01, 0x02, 0x03)
        val shortResult = dnsHandler.processDnsPayload(shortPacket)
        assertTrue(shortResult is DnsProcessingResult.Malformed)

        // 2. DNS Response treated as Query
        val responseFlagsPacket = ByteArray(16)
        responseFlagsPacket[2] = 0x80.toByte() // QR = 1 (Response)
        responseFlagsPacket[5] = 0x01.toByte() // QDCOUNT = 1
        val respResult = dnsHandler.processDnsPayload(responseFlagsPacket)
        assertTrue(respResult is DnsProcessingResult.Malformed)

        // 3. Question with label length exceeding packet boundary
        val corruptPacket = ByteArray(18)
        corruptPacket[5] = 0x01.toByte() // QDCOUNT = 1
        corruptPacket[12] = 50.toByte()  // label len = 50, but packet only has 18 bytes total!
        val corruptResult = dnsHandler.processDnsPayload(corruptPacket)
        assertTrue(corruptResult is DnsProcessingResult.Malformed)
    }

    @Test
    fun testBuildIpv4UdpLoopbackPacket() {
        // Create mock IPv4 UDP DNS query packet (20 bytes IP + 8 bytes UDP + 12 bytes DNS)
        val ipPacket = ByteArray(40)
        ipPacket[0] = 0x45.toByte() // IPv4, IHL = 5 (20 bytes)
        ipPacket[9] = 17.toByte()   // Protocol = UDP

        // Src IP = 10.1.10.1
        ipPacket[12] = 10; ipPacket[13] = 1; ipPacket[14] = 10; ipPacket[15] = 1
        // Dst IP = 1.1.1.3
        ipPacket[16] = 1; ipPacket[17] = 1; ipPacket[18] = 1; ipPacket[19] = 3

        // Src Port = 54321
        ipPacket[20] = 0xD4.toByte(); ipPacket[21] = 0x31.toByte()
        // Dst Port = 53
        ipPacket[22] = 0x00.toByte(); ipPacket[23] = 0x35.toByte()

        val dummyDnsResponse = ByteArray(16) { 0 }

        val responsePacket = dnsHandler.buildIpv4UdpResponsePacket(
            originalIpPacket = ipPacket,
            ipHeaderLen = 20,
            dnsResponsePayload = dummyDnsResponse
        )

        assertNotNull(responsePacket)
        responsePacket!!

        // Verifying swapped IPs: new Src IP should be 1.1.1.3, new Dst IP should be 10.1.10.1
        assertEquals(1, responsePacket[12].toInt())
        assertEquals(1, responsePacket[13].toInt())
        assertEquals(1, responsePacket[14].toInt())
        assertEquals(3, responsePacket[15].toInt())

        assertEquals(10, responsePacket[16].toInt())
        assertEquals(1, responsePacket[17].toInt())
        assertEquals(10, responsePacket[18].toInt())
        assertEquals(1, responsePacket[19].toInt())

        // Verifying swapped Ports: new Src Port should be 53, new Dst Port should be 54321
        assertEquals(0, responsePacket[20].toInt())
        assertEquals(53, responsePacket[21].toInt())
        assertEquals(0xD4.toByte(), responsePacket[22])
        assertEquals(0x31.toByte(), responsePacket[23])
    }

    /**
     * Helper to construct a standard DNS query payload for testing.
     */
    private fun buildDnsQueryPacket(transactionId: Int, domain: String): ByteArray {
        val out = ByteArrayOutputStream()

        // Transaction ID
        out.write((transactionId shr 8) and 0xFF)
        out.write(transactionId and 0xFF)

        // Flags: Standard query, recursion desired (0x0100)
        out.write(0x01); out.write(0x00)

        // QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)

        // Question: QNAME
        for (part in domain.split('.')) {
            out.write(part.length)
            out.write(part.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00) // End of QNAME

        // QTYPE = A (1), QCLASS = IN (1)
        out.write(0x00); out.write(0x01)
        out.write(0x00); out.write(0x01)

        return out.toByteArray()
    }
}
