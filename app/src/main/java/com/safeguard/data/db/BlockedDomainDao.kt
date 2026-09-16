package com.safeguard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Query("SELECT * FROM blocked_domains ORDER BY createdAt DESC")
    fun getAllBlockedDomains(): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains ORDER BY createdAt DESC")
    suspend fun getAllBlockedDomainsList(): List<BlockedDomain>

    @Query("SELECT * FROM blocked_domains WHERE source = :source ORDER BY createdAt DESC")
    fun getBlockedDomainsBySource(source: String): Flow<List<BlockedDomain>>

    @Query("SELECT * FROM blocked_domains WHERE source = :source ORDER BY createdAt DESC")
    suspend fun getBlockedDomainsListBySource(source: String): List<BlockedDomain>

    @Query("SELECT COUNT(*) FROM blocked_domains")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_domains")
    suspend fun getTotalCountSync(): Int

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE source = :source")
    fun getCountBySource(source: String): Flow<Int>

    @Query("SELECT * FROM blocked_domains WHERE domain = :domain LIMIT 1")
    suspend fun findByDomain(domain: String): BlockedDomain?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: BlockedDomain): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(domains: List<BlockedDomain>): List<Long>

    @Update
    suspend fun update(domain: BlockedDomain)

    @Delete
    suspend fun delete(domain: BlockedDomain)

    @Query("DELETE FROM blocked_domains WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM blocked_domains WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /**
     * Atomically replaces domains for a given source (e.g. REMOTE) without leaving
     * the database in an empty or inconsistent state.
     */
    @Transaction
    suspend fun replaceDomainsForSource(source: String, newDomains: List<BlockedDomain>) {
        deleteBySource(source)
        insertAll(newDomains)
    }

    @Query("DELETE FROM blocked_domains")
    suspend fun clearAll()
}
