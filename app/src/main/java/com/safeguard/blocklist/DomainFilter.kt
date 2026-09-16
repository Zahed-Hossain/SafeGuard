package com.safeguard.blocklist

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

enum class FilterDecision {
    ALLOW,
    BLOCK
}

data class FilterResult(
    val decision: FilterDecision,
    val domain: String,
    val matchedRule: String? = null,
    val category: BlockedCategory? = null,
    val reason: String = "",
    val isWhitelisted: Boolean = false
) {
    val isBlocked: Boolean get() = decision == FilterDecision.BLOCK
    val isAllowed: Boolean get() = decision == FilterDecision.ALLOW
}

/**
 * DomainFilter implements an ultra-fast, hierarchical domain filtering engine.
 *
 * Architecture:
 * 1. High-priority Whitelist: Whitelist entries always override blocklist entries.
 * 2. Multi-category Blocklist: Categorized rules with exact and wildcard/subdomain matching.
 * 3. O(k) Lookup Structure: Lookups operate in constant time relative to the number of domain labels
 *    (O(k) where k is number of labels, typically 2-4), rather than linearly scanning thousands of rules.
 * 4. Built-in developer test domains:
 *    - blocked.test -> BLOCK
 *    - adult-test.local -> BLOCK
 *    - example-blocked.test -> BLOCK
 *    - allowed.test -> ALLOW
 */
class DomainFilter {

    data class FilterEntry(
        val pattern: String,
        val category: BlockedCategory,
        val isExactOnly: Boolean = false
    )

    // Whitelist data structures:
    // exactWhitelist: matches only the exact domain
    private val exactWhitelist = CopyOnWriteArraySet<String>()
    // suffixWhitelist: matches the domain and all its subdomains (*.domain)
    private val suffixWhitelist = CopyOnWriteArraySet<String>()

    // Blocklist data structures:
    // Keyed by normalized domain for O(1) hash map lookup
    private val exactBlocklist = ConcurrentHashMap<String, FilterEntry>()
    private val suffixBlocklist = ConcurrentHashMap<String, FilterEntry>()

    init {
        loadDefaultDatabase()
    }

    /**
     * Initializes the built-in database with standard adult filtering categories
     * and safe developer test domains.
     */
    fun loadDefaultDatabase() {
        // Safe developer test domains as requested
        addBlockRule("blocked.test", BlockedCategory.ADULT, isExactOnly = false)
        addBlockRule("adult-test.local", BlockedCategory.PORNOGRAPHY, isExactOnly = false)
        addBlockRule("example-blocked.test", BlockedCategory.EXPLICIT, isExactOnly = false)
        addWhitelist("allowed.test", isExactOnly = false)

        // Category: ADULT
        listOf(
            "adult.test", "adultcontent.test", "mature-content.test"
        ).forEach { addBlockRule(it, BlockedCategory.ADULT) }

        // Category: PORNOGRAPHY
        listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
            "tube8.com", "brazzers.com", "beeg.com", "spankbang.com", "porn.com"
        ).forEach { addBlockRule(it, BlockedCategory.PORNOGRAPHY) }

        // Category: EXPLICIT
        listOf(
            "badjojo.com", "heavy-r.com", "efukt.com", "tnaflix.com", "empflix.com"
        ).forEach { addBlockRule(it, BlockedCategory.EXPLICIT) }

        // Category: NSFW
        listOf(
            "rule34.xxx", "gelbooru.com", "danbooru.donmai.us", "e-hentai.org", "nhentai.net"
        ).forEach { addBlockRule(it, BlockedCategory.NSFW) }

        // Category: ADULT_STREAMING
        listOf(
            "chaturbate.com", "cam4.com", "stripchat.com", "livejasmin.com",
            "bongacams.com", "myfreecams.com", "camsoda.com", "flirt4free.com"
        ).forEach { addBlockRule(it, BlockedCategory.ADULT_STREAMING) }

        // Category: ADULT_COMMUNITY
        listOf(
            "literotica.com", "lushstories.com", "asstr.org", "fetlife.com"
        ).forEach { addBlockRule(it, BlockedCategory.ADULT_COMMUNITY) }

        // Category: ADULT_DATING
        listOf(
            "ashley-madison.com", "adultfriendfinder.com", "passion.com", "alt.com", "fling.com"
        ).forEach { addBlockRule(it, BlockedCategory.ADULT_DATING) }

        // Category: INAPPROPRIATE
        listOf(
            "inappropriate-test.local", "unsafe-preview.test"
        ).forEach { addBlockRule(it, BlockedCategory.INAPPROPRIATE) }
    }

    /**
     * Determines whether [rawDomain] should be ALLOWED or BLOCKED.
     *
     * Flow:
     * 1. Normalize domain (strip schemes, paths, trailing dots, convert to lowercase, handle IDN/punycode).
     * 2. Check Whitelist (exact and hierarchy). If matched -> ALLOW.
     * 3. Check Blocklist (exact and hierarchy). If matched -> BLOCK.
     * 4. If no rules match -> ALLOW.
     */
    fun evaluate(rawDomain: String): FilterResult {
        val domain = DomainMatcher.normalize(rawDomain)
        if (domain.isEmpty()) {
            return FilterResult(
                decision = FilterDecision.ALLOW,
                domain = rawDomain,
                reason = "Empty domain"
            )
        }

        // ==========================================
        // 1. Check Whitelist (Highest Priority)
        // ==========================================
        if (exactWhitelist.contains(domain)) {
            return FilterResult(
                decision = FilterDecision.ALLOW,
                domain = domain,
                matchedRule = domain,
                isWhitelisted = true,
                reason = "Domain is explicitly whitelisted (exact match)"
            )
        }

        val hierarchy = DomainMatcher.getDomainHierarchy(domain)
        for (parent in hierarchy) {
            if (suffixWhitelist.contains(parent)) {
                return FilterResult(
                    decision = FilterDecision.ALLOW,
                    domain = domain,
                    matchedRule = parent,
                    isWhitelisted = true,
                    reason = "Domain is allowed by whitelisted suffix: $parent"
                )
            }
        }

        // ==========================================
        // 2. Check Blocklist
        // ==========================================
        // Check exact match first
        val exactMatch = exactBlocklist[domain]
        if (exactMatch != null) {
            return FilterResult(
                decision = FilterDecision.BLOCK,
                domain = domain,
                matchedRule = exactMatch.pattern,
                category = exactMatch.category,
                reason = "Blocked by SafeGuard ${exactMatch.category.displayName} filter (exact)"
            )
        }

        // Check suffix/subdomain hierarchy (O(k) where k is number of domain labels)
        for (parent in hierarchy) {
            val suffixMatch = suffixBlocklist[parent]
            if (suffixMatch != null) {
                return FilterResult(
                    decision = FilterDecision.BLOCK,
                    domain = domain,
                    matchedRule = suffixMatch.pattern,
                    category = suffixMatch.category,
                    reason = "Blocked by SafeGuard ${suffixMatch.category.displayName} filter (*.$parent)"
                )
            }
        }

        // ==========================================
        // 3. Default: ALLOW
        // ==========================================
        return FilterResult(
            decision = FilterDecision.ALLOW,
            domain = domain,
            reason = "No matching blocklist entry"
        )
    }

    /**
     * Adds a domain to the blocklist.
     *
     * @param pattern Domain or wildcard (e.g. "example.com" or "*.example.com")
     * @param category The adult or inappropriate category
     * @param isExactOnly If true, matches only the exact domain, not subdomains. Default is false.
     */
    fun addBlockRule(
        pattern: String,
        category: BlockedCategory,
        isExactOnly: Boolean = false
    ) {
        val clean = DomainMatcher.normalize(pattern)
        if (clean.isEmpty()) return

        val entry = FilterEntry(clean, category, isExactOnly)
        if (isExactOnly) {
            exactBlocklist[clean] = entry
        } else {
            suffixBlocklist[clean] = entry
        }
    }

    /**
     * Removes a domain from the blocklist.
     */
    fun removeBlockRule(pattern: String) {
        val clean = DomainMatcher.normalize(pattern)
        if (clean.isEmpty()) return

        exactBlocklist.remove(clean)
        suffixBlocklist.remove(clean)
    }

    /**
     * Adds a domain to the whitelist (highest priority).
     *
     * @param pattern Domain to allow (e.g. "sub.example.com")
     * @param isExactOnly If true, only allows exact domain. Default is false (allows domain and subdomains).
     */
    fun addWhitelist(pattern: String, isExactOnly: Boolean = false) {
        val clean = DomainMatcher.normalize(pattern)
        if (clean.isEmpty()) return

        if (isExactOnly) {
            exactWhitelist.add(clean)
        } else {
            suffixWhitelist.add(clean)
        }
    }

    /**
     * Removes a domain from the whitelist.
     */
    fun removeWhitelist(pattern: String) {
        val clean = DomainMatcher.normalize(pattern)
        if (clean.isEmpty()) return

        exactWhitelist.remove(clean)
        suffixWhitelist.remove(clean)
    }

    fun isWhitelisted(domain: String): Boolean {
        val clean = DomainMatcher.normalize(domain)
        if (exactWhitelist.contains(clean)) return true
        val hierarchy = DomainMatcher.getDomainHierarchy(clean)
        return hierarchy.any { suffixWhitelist.contains(it) }
    }

    fun clearAll() {
        exactWhitelist.clear()
        suffixWhitelist.clear()
        exactBlocklist.clear()
        suffixBlocklist.clear()
    }

    fun getBlocklistSize(): Int = exactBlocklist.size + suffixBlocklist.size
    fun getWhitelistSize(): Int = exactWhitelist.size + suffixWhitelist.size
}
