package com.safeguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BlockEvent records safety event statistics without compromising privacy.
 * To adhere strictly to privacy guidelines, the domain itself is never stored in plain text;
 * only a one-way cryptographic hash (SHA-256) or category is retained for audit/stats.
 */
@Entity(
    tableName = "block_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"])
    ]
)
data class BlockEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val domainHash: String
)
