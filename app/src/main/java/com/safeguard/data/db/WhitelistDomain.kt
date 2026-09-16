package com.safeguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist_domains",
    indices = [
        Index(value = ["domain"], unique = true)
    ]
)
data class WhitelistDomain(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domain: String,
    val createdAt: Long = System.currentTimeMillis()
)
