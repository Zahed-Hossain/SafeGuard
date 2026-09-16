package com.safeguard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    @Query("SELECT * FROM whitelist_domains ORDER BY createdAt DESC")
    fun getAllWhitelist(): Flow<List<WhitelistDomain>>

    @Query("SELECT * FROM whitelist_domains ORDER BY createdAt DESC")
    suspend fun getAllWhitelistList(): List<WhitelistDomain>

    @Query("SELECT COUNT(*) FROM whitelist_domains")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM whitelist_domains")
    suspend fun getCountSync(): Int

    @Query("SELECT * FROM whitelist_domains WHERE domain = :domain LIMIT 1")
    suspend fun findByDomain(domain: String): WhitelistDomain?

    @Query("SELECT EXISTS(SELECT 1 FROM whitelist_domains WHERE domain = :domain LIMIT 1)")
    suspend fun isWhitelisted(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(whitelistDomain: WhitelistDomain): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(whitelistDomains: List<WhitelistDomain>): List<Long>

    @Update
    suspend fun update(whitelistDomain: WhitelistDomain)

    @Delete
    suspend fun delete(whitelistDomain: WhitelistDomain)

    @Query("DELETE FROM whitelist_domains WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM whitelist_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM whitelist_domains")
    suspend fun clearAll()
}
