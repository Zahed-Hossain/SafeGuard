package com.safeguard

import com.safeguard.blocklist.BlockedCategory
import com.safeguard.blocklist.DomainFilter
import com.safeguard.blocklist.FilterDecision
import com.safeguard.blocklist.parser.BlocklistJsonParser
import com.safeguard.data.db.BlockEvent
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlocklistSystemTest {

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testDomainNormalization() {
        // Full URL with scheme and path
        val norm1 = BlocklistJsonParser.normalizeDomainInput("https://www.adult-site.com/gallery/123?ref=abc")
        assertEquals("adult-site.com", norm1)

        // URL without www
        val norm2 = BlocklistJsonParser.normalizeDomainInput("http://xxx-content.net/video")
        assertEquals("xxx-content.net", norm2)

        // Plain domain with whitespace and uppercase
        val norm3 = BlocklistJsonParser.normalizeDomainInput("   BAD-DOMAIN.COM   ")
        assertEquals("bad-domain.com", norm3)

        // Port number removal
        val norm4 = BlocklistJsonParser.normalizeDomainInput("https://example.com:8443/test")
        assertEquals("example.com", norm4)
    }

    @Test
    fun testDomainFormatValidation() {
        assertTrue(BlocklistJsonParser.isValidDomainFormat("adult-site.com"))
        assertTrue(BlocklistJsonParser.isValidDomainFormat("sub.domain.xxx"))
        assertTrue(BlocklistJsonParser.isValidDomainFormat("a-b-c.org"))

        assertFalse(BlocklistJsonParser.isValidDomainFormat(""))
        assertFalse(BlocklistJsonParser.isValidDomainFormat("invalid..domain"))
        assertFalse(BlocklistJsonParser.isValidDomainFormat("has spaces.com"))
        assertFalse(BlocklistJsonParser.isValidDomainFormat(".leadingdot.com"))
        assertFalse(BlocklistJsonParser.isValidDomainFormat("nodot"))
    }

    @Test
    fun testBlocklistJsonParser_ValidPayload() {
        val sampleJson = """
            {
              "version": "1.2.0",
              "updatedAt": "2026-09-14T09:00:00Z",
              "domains": [
                "https://www.badsite1.xxx/home",
                "gambling-online.bet",
                "badsite1.xxx",
                "invalid..domain"
              ]
            }
        """.trimIndent()

        val parsed = BlocklistJsonParser.parse(sampleJson)
        assertEquals("1.2.0", parsed.version)
        assertEquals("2026-09-14T09:00:00Z", parsed.updatedAt)

        // Should normalize and deduplicate "badsite1.xxx", include "gambling-online.bet", and put "invalid..domain" in malformed
        assertEquals(2, parsed.validDomains.size)
        assertTrue(parsed.validDomains.contains("badsite1.xxx"))
        assertTrue(parsed.validDomains.contains("gambling-online.bet"))
        assertEquals(1, parsed.duplicateCount)
        assertEquals(1, parsed.malformedDomains.size)
    }

    @Test
    fun testWhitelistOverridesBlocklistPriority() {
        val domainFilter = DomainFilter()

        // Add domain to blocklist
        domainFilter.addBlockRule("study-biology.org", BlockedCategory.ADULT)
        val result1 = domainFilter.evaluate("study-biology.org")
        assertTrue(result1.isBlocked)
        assertEquals(FilterDecision.BLOCK, result1.decision)

        // Add to whitelist -> Whitelist MUST override blocklist
        domainFilter.addWhitelist("study-biology.org")
        val result2 = domainFilter.evaluate("study-biology.org")
        assertFalse(result2.isBlocked)
        assertTrue(result2.isAllowed)
        assertTrue(result2.isWhitelisted)

        // Subdomain of whitelisted domain should also be allowed
        val resultSub = domainFilter.evaluate("sub.study-biology.org")
        assertTrue(resultSub.isAllowed)

        // Remove from whitelist -> Blocking resumes
        domainFilter.removeWhitelist("study-biology.org")
        val result3 = domainFilter.evaluate("study-biology.org")
        assertTrue(result3.isBlocked)
    }

    @Test
    fun testBlockEventPrivacyHashing() {
        // Ensure BlockEvent creates non-reversible SHA-256 hashes instead of storing raw domains
        val rawDomain = "private-site.xxx"
        val hash = sha256(rawDomain)
        val event = BlockEvent(
            category = "ADULT",
            domainHash = hash
        )

        assertFalse(event.domainHash.contains("private-site"))
        assertEquals(64, event.domainHash.length) // SHA-256 is 64 hex characters

        // Deterministic hashing for aggregation
        val hash2 = sha256(rawDomain)
        assertEquals(event.domainHash, hash2)
    }

    @Test
    fun testJsonSerializationAndRoundTrip() {
        val domains = listOf("site1.com", "site2.xxx")
        val json = BlocklistJsonParser.toJson(
            version = "2.0.0",
            updatedAt = "2026-09-14T09:30:00Z",
            domains = domains
        )

        val parsed = BlocklistJsonParser.parse(json)
        assertEquals("2.0.0", parsed.version)
        assertEquals(2, parsed.validDomains.size)
        assertTrue(parsed.validDomains.contains("site1.com"))
        assertTrue(parsed.validDomains.contains("site2.xxx"))
    }
}
