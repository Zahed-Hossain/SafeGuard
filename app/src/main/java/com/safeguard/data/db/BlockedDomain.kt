package com.safeguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_domains",
    indices = [
        Index(value = ["domain"], unique = true),
        Index(value = ["source"]),
        Index(value = ["category"])
    ]
)
data class BlockedDomain(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domain: String,
    val category: String,
    val source: String, // "BUILTIN", "LOCAL", "REMOTE", "CUSTOM"
    val createdAt: Long = System.currentTimeMillis()
)
