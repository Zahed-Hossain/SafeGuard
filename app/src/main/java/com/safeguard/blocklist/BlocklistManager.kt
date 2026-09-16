package com.safeguard.blocklist

import com.safeguard.blocklist.repository.BlocklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * BlocklistManager coordinates domain filtering rules, whitelist overrides,
 * category classifications, and high-performance O(k) domain evaluations.
 */
class BlocklistManager(
    val domainFilter: DomainFilter = DomainFilter(),
    val repository: BlocklistRepository? = null
) {

    private val _rules = MutableStateFlow<List<BlockRule>>(emptyList())
    val rules: StateFlow<List<BlockRule>> = _rules.asStateFlow()

    private val adultKeywords = listOf(
        "porn", "xxx", "sex", "adult", "erotic", "nsfw",
        "cam4", "chaturbate", "xvideos", "xnxx", "redtube",
        "pornhub", "youporn", "stripchat", "onlyfans", "livejasmin"
    )

    private val gamblingKeywords = listOf(
        "casino", "bet365", "gambling", "poker", "sportsbet", "stake"
    )

    init {
        loadDefaultRules()
    }

    private fun loadDefaultRules() {
        val initialRules = mutableListOf<BlockRule>()

        adultKeywords.forEach { keyword ->
            initialRules.add(
                BlockRule(
                    pattern = keyword,
                    category = BlockedCategory.ADULT,
                    isExactMatch = false,
                    enabled = true
                )
            )
        }

        gamblingKeywords.forEach { keyword ->
            initialRules.add(
                BlockRule(
                    pattern = keyword,
                    category = BlockedCategory.GAMBLING,
                    isExactMatch = false,
                    enabled = true
                )
            )
        }

        _rules.value = initialRules
    }

    /**
     * Primary domain evaluation API for Part 3.
     * Evaluates domain against Whitelist (highest priority), then Blocklist database.
     * Returns structured [FilterResult] with decision (ALLOW or BLOCK).
     */
    fun checkDomain(domain: String): FilterResult {
        return domainFilter.evaluate(domain)
    }

    /**
     * Backward-compatible evaluation function used across Part 1 & Part 2.
     * Supports category toggles and whitelist override.
     */
    fun isDomainBlocked(
        domain: String,
        blockAdult: Boolean = true,
        blockGambling: Boolean = true,
        blockMalware: Boolean = true
    ): BlockCheckResult {
        val cleanDomain = DomainMatcher.normalize(domain)

        // 1. Check Whitelist (Highest Priority)
        if (domainFilter.isWhitelisted(cleanDomain)) {
            return BlockCheckResult(isBlocked = false)
        }

        // 2. Check DomainFilter database (O(k) hierarchy check)
        val filterResult = domainFilter.evaluate(cleanDomain)
        if (filterResult.isBlocked) {
            val category = filterResult.category ?: BlockedCategory.ADULT
            val isAdultCategory = when (category) {
                BlockedCategory.ADULT,
                BlockedCategory.PORNOGRAPHY,
                BlockedCategory.EXPLICIT,
                BlockedCategory.NSFW,
                BlockedCategory.ADULT_STREAMING,
                BlockedCategory.ADULT_COMMUNITY,
                BlockedCategory.ADULT_DATING,
                BlockedCategory.INAPPROPRIATE -> true
                else -> false
            }

            val categoryEnabled = when {
                isAdultCategory -> blockAdult
                category == BlockedCategory.GAMBLING -> blockGambling
                category == BlockedCategory.MALWARE -> blockMalware
                else -> true
            }

            if (categoryEnabled) {
                return BlockCheckResult(
                    isBlocked = true,
                    matchedRule = BlockRule(
                        pattern = filterResult.matchedRule ?: cleanDomain,
                        category = category
                    ),
                    category = category,
                    reason = filterResult.reason
                )
            }
        }

        // 3. Check legacy keyword rules if not caught by domain hierarchy
        for (rule in _rules.value) {
            if (!rule.enabled) continue

            if (rule.category == BlockedCategory.ADULT && !blockAdult) continue
            if (rule.category == BlockedCategory.GAMBLING && !blockGambling) continue
            if (rule.category == BlockedCategory.MALWARE && !blockMalware) continue

            val matched = if (rule.isExactMatch) {
                cleanDomain == rule.pattern
            } else {
                cleanDomain.contains(rule.pattern)
            }

            if (matched) {
                return BlockCheckResult(
                    isBlocked = true,
                    matchedRule = rule,
                    category = rule.category,
                    reason = "Blocked by SafeGuard ${rule.category.displayName} filter"
                )
            }
        }

        return BlockCheckResult(isBlocked = false)
    }

    fun addWhitelist(domain: String, isExactOnly: Boolean = false) {
        domainFilter.addWhitelist(domain, isExactOnly)
    }

    fun addToWhitelist(domain: String, isExactOnly: Boolean = false) {
        addWhitelist(domain, isExactOnly)
    }

    fun removeWhitelist(domain: String) {
        domainFilter.removeWhitelist(domain)
    }

    fun isWhitelisted(domain: String): Boolean {
        return domainFilter.isWhitelisted(domain)
    }

    fun addRule(domain: String, category: BlockedCategory = BlockedCategory.ADULT, isExactMatch: Boolean = false) {
        addCustomRule(BlockRule(pattern = domain, category = category, isExactMatch = isExactMatch))
    }

    fun addCustomRule(rule: BlockRule) {
        _rules.value = _rules.value + rule
        domainFilter.addBlockRule(rule.pattern, rule.category, rule.isExactMatch)
    }

    fun addCustomRule(pattern: String, category: BlockedCategory, isExactMatch: Boolean = false) {
        addCustomRule(BlockRule(pattern = pattern, category = category, isExactMatch = isExactMatch))
    }

    fun removeRule(pattern: String) {
        _rules.value = _rules.value.filterNot { it.pattern == pattern }
        domainFilter.removeBlockRule(pattern)
    }

    fun getRulesCount(): Int = _rules.value.size + domainFilter.getBlocklistSize()
}
