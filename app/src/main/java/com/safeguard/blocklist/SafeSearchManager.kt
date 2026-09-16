package com.safeguard.blocklist

import java.util.Locale

/**
 * Data class representing a SafeSearch redirect mapping.
 */
data class SafeSearchRedirect(
    val canonicalDomain: String,
    val ipv4Address: ByteArray,
    val ipv6Address: ByteArray = ByteArray(16)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SafeSearchRedirect) return false
        return canonicalDomain == other.canonicalDomain &&
                ipv4Address.contentEquals(other.ipv4Address) &&
                ipv6Address.contentEquals(other.ipv6Address)
    }
    override fun hashCode(): Int = canonicalDomain.hashCode()
}

/**
 * SafeSearch Enforcement Architecture for SafeGuard.
 *
 * Implements strict SafeSearch DNS alias routing for major search engines:
 * - Google (forcesafesearch.google.com -> 216.239.38.120 / 2001:4860:4806::78)
 * - Bing (strict.bing.com -> 204.79.197.220 / 2620:1ec:c11::200)
 * - DuckDuckGo (safe.duckduckgo.com -> 52.142.124.215)
 * - Yahoo (safesearch.yahoo.com -> 216.239.38.120)
 *
 * Privacy Guarantee:
 * Zero search queries, keywords, or history are inspected or collected.
 * SafeSearch is enforced strictly at the DNS resolution layer by mapping
 * search engine hostnames to their respective safe IP VIPs.
 */
object SafeSearchManager {

    // Google SafeSearch VIPs (forcesafesearch.google.com)
    val GOOGLE_SAFESEARCH_IPV4: ByteArray = byteArrayOf(216.toByte(), 239.toByte(), 38, 120) // 216.239.38.120
    val GOOGLE_SAFESEARCH_IPV6: ByteArray = byteArrayOf(
        0x20.toByte(), 0x01.toByte(), 0x48.toByte(), 0x60.toByte(),
        0x48.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x78.toByte()
    ) // 2001:4860:4806::78

    // Bing SafeSearch VIPs (strict.bing.com)
    val BING_SAFESEARCH_IPV4: ByteArray = byteArrayOf(204.toByte(), 79, 197.toByte(), 220.toByte()) // 204.79.197.220
    val BING_SAFESEARCH_IPV6: ByteArray = byteArrayOf(
        0x26.toByte(), 0x20.toByte(), 0x01.toByte(), 0xec.toByte(),
        0x0c.toByte(), 0x11.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte()
    ) // 2620:1ec:c11::200

    // DuckDuckGo SafeSearch VIP (safe.duckduckgo.com)
    val DUCKDUCKGO_SAFESEARCH_IPV4: ByteArray = byteArrayOf(52, 142.toByte(), 124, 215.toByte()) // 52.142.124.215

    // Yahoo SafeSearch VIP (routed via safe Yahoo search VIP)
    val YAHOO_SAFESEARCH_IPV4: ByteArray = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)

    enum class SearchEngine {
        GOOGLE,
        BING,
        DUCKDUCKGO,
        YAHOO
    }

    /**
     * Determines if a normalized domain is a search engine domain supported by SafeSearch.
     */
    fun identifySearchEngine(domain: String): SearchEngine? {
        val clean = DomainMatcher.normalize(domain).lowercase(Locale.ROOT)
        val host = DomainMatcher.stripWww(clean)

        return when {
            isGoogleDomain(host) -> SearchEngine.GOOGLE
            isBingDomain(host) -> SearchEngine.BING
            isDuckDuckGoDomain(host) -> SearchEngine.DUCKDUCKGO
            isYahooDomain(host) -> SearchEngine.YAHOO
            else -> null
        }
    }

    private fun isGoogleDomain(host: String): Boolean {
        if (host == "google.com" || host.endsWith(".google.com")) return true
        if (host.startsWith("google.") || host.contains(".google.")) return true
        return false
    }

    private fun isBingDomain(host: String): Boolean {
        return host == "bing.com" || host.endsWith(".bing.com")
    }

    private fun isDuckDuckGoDomain(host: String): Boolean {
        return host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")
    }

    private fun isYahooDomain(host: String): Boolean {
        return host == "search.yahoo.com" || host == "yahoo.com" || host.endsWith(".search.yahoo.com")
    }

    /**
     * Returns SafeSearch redirect information if the domain is a supported search engine.
     */
    fun getSafeSearchRedirect(domain: String): SafeSearchRedirect? {
        val engine = identifySearchEngine(domain) ?: return null

        return when (engine) {
            SearchEngine.GOOGLE -> SafeSearchRedirect(
                canonicalDomain = "forcesafesearch.google.com",
                ipv4Address = GOOGLE_SAFESEARCH_IPV4,
                ipv6Address = GOOGLE_SAFESEARCH_IPV6
            )
            SearchEngine.BING -> SafeSearchRedirect(
                canonicalDomain = "strict.bing.com",
                ipv4Address = BING_SAFESEARCH_IPV4,
                ipv6Address = BING_SAFESEARCH_IPV6
            )
            SearchEngine.DUCKDUCKGO -> SafeSearchRedirect(
                canonicalDomain = "safe.duckduckgo.com",
                ipv4Address = DUCKDUCKGO_SAFESEARCH_IPV4
            )
            SearchEngine.YAHOO -> SafeSearchRedirect(
                canonicalDomain = "safesearch.yahoo.com",
                ipv4Address = YAHOO_SAFESEARCH_IPV4
            )
        }
    }

    /**
     * Returns the SafeSearch IP address bytes for the given domain and DNS query type.
     * Returns null if domain is not a supported search engine or no matching record type.
     */
    fun getSafeSearchIp(domain: String, qType: Int): ByteArray? {
        val redirect = getSafeSearchRedirect(domain) ?: return null
        return if (qType == DnsHandler.TYPE_AAAA) {
            val isNonZero = redirect.ipv6Address.any { it != 0.toByte() }
            if (isNonZero) redirect.ipv6Address else null
        } else {
            redirect.ipv4Address
        }
    }
}
