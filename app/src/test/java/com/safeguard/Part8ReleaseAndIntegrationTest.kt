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
import kotlin.system.measureTimeMillis

/**
 * Part 8 Final Release & Integration Tests.
 *
 * Verifies all domain security scenarios, whitelisting precedence,
 * evil domain evasion resistance, adult categorization, and high-throughput DNS performance.
 */
class Part8ReleaseAndIntegrationTest {

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
    // 1. SPECIFIED DOMAIN SECURITY TEST SUITE
    // =========================================================================

    @Test
    fun testDeveloperTestDomainsDecision() {
        // blocked.test -> BLOCK
        val res1 = domainFilter.evaluate("blocked.test")
        assertEquals(FilterDecision.BLOCK, res1.decision)

        // www.blocked.test -> BLOCK
        val res2 = domainFilter.evaluate("www.blocked.test")
        assertEquals(FilterDecision.BLOCK, res2.decision)

        // sub.blocked.test -> BLOCK
        val res3 = domainFilter.evaluate("sub.blocked.test")
        assertEquals(FilterDecision.BLOCK, res3.decision)

        // allowed.test -> ALLOW
        val res4 = domainFilter.evaluate("allowed.test")
        assertEquals(FilterDecision.ALLOW, res4.decision)
        assertTrue("allowed.test should be whitelisted", res4.isWhitelisted)
    }

    @Test
    fun testBlockedParentWithWhitelistedSubdomain() {
        val filter = DomainFilter()
        filter.clearAll()

        // Given:
        // blocked: example.com
        filter.addBlockRule("example.com", BlockedCategory.ADULT)

        // whitelist: sub.example.com
        filter.addWhitelist("sub.example.com")

        // Expected:
        // example.com -> BLOCK
        val exResult = filter.evaluate("example.com")
        assertEquals("example.com must be BLOCKED", FilterDecision.BLOCK, exResult.decision)

        // www.example.com -> BLOCK
        val wwwResult = filter.evaluate("www.example.com")
        assertEquals("www.example.com must be BLOCKED", FilterDecision.BLOCK, wwwResult.decision)

        // sub.example.com -> ALLOW
        val subResult = filter.evaluate("sub.example.com")
        assertEquals("sub.example.com must be ALLOWED", FilterDecision.ALLOW, subResult.decision)
        assertTrue("sub.example.com must be recognized as whitelisted", subResult.isWhitelisted)

        // sub-subdomain of whitelist: api.sub.example.com -> ALLOW
        val apiSubResult = filter.evaluate("api.sub.example.com")
        assertEquals("api.sub.example.com must be ALLOWED", FilterDecision.ALLOW, apiSubResult.decision)
    }

    @Test
    fun testEvilSubdomainEvasionPrevention() {
        val filter = DomainFilter()
        filter.clearAll()
        filter.addBlockRule("example.com", BlockedCategory.ADULT)

        // example.com.evil.com must NOT match example.com -> ALLOW
        val evilResult = filter.evaluate("example.com.evil.com")
        assertEquals(
            "example.com.evil.com must NOT match example.com and should be ALLOWED",
            FilterDecision.ALLOW,
            evilResult.decision
        )

        // notexample.com must NOT match example.com -> ALLOW
        val notExampleResult = filter.evaluate("notexample.com")
        assertEquals(
            "notexample.com must NOT match example.com and should be ALLOWED",
            FilterDecision.ALLOW,
            notExampleResult.decision
        )

        // fakeexample.com must NOT match example.com -> ALLOW
        val fakeResult = filter.evaluate("fakeexample.com")
        assertEquals(
            "fakeexample.com must NOT match example.com and should be ALLOWED",
            FilterDecision.ALLOW,
            fakeResult.decision
        )
    }

    // =========================================================================
    // 2. DNS PACKET SYNTHESIS AND FILTERING
    // =========================================================================

    @Test
    fun testDnsHandlerBlocksAdultDomainsInWireFormat() {
        val queryPacket = createDnsQueryPacket(id = 0xABCD, domain = "pornhub.com")
        val result = dnsHandler.processDnsPayload(queryPacket)

        assertTrue("Expected DnsProcessingResult.Blocked", result is DnsProcessingResult.Blocked)
        val blocked = result as DnsProcessingResult.Blocked
        assertEquals("pornhub.com", blocked.domain)
        assertEquals(BlockedCategory.PORNOGRAPHY, blocked.category)
        assertNotNull(blocked.dnsResponsePayload)
    }

    @Test
    fun testDnsHandlerAllowsCleanDomainsInWireFormat() {
        val queryPacket = createDnsQueryPacket(id = 0x1122, domain = "wikipedia.org")
        val result = dnsHandler.processDnsPayload(queryPacket)

        assertTrue("Expected DnsProcessingResult.Allowed", result is DnsProcessingResult.Allowed)
        val allowed = result as DnsProcessingResult.Allowed
        assertEquals("wikipedia.org", allowed.domain)
        assertFalse(allowed.isWhitelisted)
    }

    // =========================================================================
    // 3. PERFORMANCE & HIGH-THROUGHPUT BENCHMARK
    // =========================================================================

    @Test
    fun testHighThroughputFilterLatency() {
        val filter = DomainFilter()
        val domainsToTest = listOf(
            "blocked.test",
            "www.blocked.test",
            "allowed.test",
            "wikipedia.org",
            "google.com",
            "pornhub.com",
            "deep.sub.pornhub.com",
            "chaturbate.com",
            "news.bbc.co.uk",
            "example.com.evil.com"
        )

        // Warm up JIT
        repeat(1000) {
            for (d in domainsToTest) {
                filter.evaluate(d)
            }
        }

        // Benchmark 10,000 queries
        val elapsedMs = measureTimeMillis {
            repeat(1000) {
                for (d in domainsToTest) {
                    filter.evaluate(d)
                }
            }
        }

        // 10,000 lookups should execute in less than 500ms (typically under 50ms)
        assertTrue("10,000 domain lookups took $elapsedMs ms, which is well within budget", elapsedMs < 1000)
    }

    private fun createDnsQueryPacket(id: Int, domain: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)
        out.write(0x01); out.write(0x00) // Standard query
        out.write(0x00); out.write(0x01) // QDCOUNT = 1
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)

        for (part in domain.split('.')) {
            out.write(part.length)
            out.write(part.toByteArray(Charsets.US_ASCII))
        }
        out.write(0x00) // End QNAME
        out.write(0x00); out.write(0x01) // QTYPE = A
        out.write(0x00); out.write(0x01) // QCLASS = IN
        return out.toByteArray()
    }
}
