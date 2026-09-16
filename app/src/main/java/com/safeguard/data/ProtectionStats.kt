package com.safeguard.data

data class ProtectionStats(
    val blockedToday: Int = 0,
    val blockedThisWeek: Int = 0,
    val blockedThisMonth: Int = 0,
    val totalBlocked: Int = 0,
    val adultCount: Int = 0,
    val pornographyCount: Int = 0,
    val explicitCount: Int = 0,
    val nsfwCount: Int = 0,
    val otherCount: Int = 0
)

data class BlockedEvent(
    val id: String,
    val domain: String,
    val category: String,
    val timestamp: Long,
    val reason: String = "Blocked adult / inappropriate content"
)
