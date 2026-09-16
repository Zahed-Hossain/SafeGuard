package com.safeguard.blocklist

enum class BlockedCategory(val displayName: String, val description: String) {
    ADULT("Adult Content", "General adult, age-restricted and mature content"),
    PORNOGRAPHY("Pornography", "Hardcore and softcore pornographic material"),
    EXPLICIT("Explicit Content", "Sexually explicit and vulgar material"),
    NSFW("Not Safe For Work", "Sexually suggestive and NSFW content"),
    ADULT_STREAMING("Adult Streaming", "Live adult webcams and streaming video sites"),
    ADULT_COMMUNITY("Adult Community", "Adult forums, image boards, and communities"),
    ADULT_DATING("Adult Dating", "Casual encounter and adult dating services"),
    INAPPROPRIATE("Inappropriate", "General inappropriate or harmful adult content"),
    GAMBLING("Gambling", "Online betting, casino, and lottery platforms"),
    MALWARE("Malware & Phishing", "Known malicious sites, spyware, and phishing domains"),
    CUSTOM("Custom Rule", "User-defined restriction rule")
}

data class BlockRule(
    val pattern: String,
    val category: BlockedCategory,
    val isExactMatch: Boolean = false,
    val enabled: Boolean = true
)

data class BlockCheckResult(
    val isBlocked: Boolean,
    val matchedRule: BlockRule? = null,
    val category: BlockedCategory? = null,
    val reason: String = ""
)
