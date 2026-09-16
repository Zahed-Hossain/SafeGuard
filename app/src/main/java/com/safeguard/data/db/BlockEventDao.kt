package com.safeguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<BlockEvent>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<BlockEvent>>

    @Query("SELECT COUNT(*) FROM block_events")
    fun getTotalEventsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_events")
    suspend fun getTotalEventsCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: BlockEvent): Long

    @Query("DELETE FROM block_events")
    suspend fun clearAll()
}
