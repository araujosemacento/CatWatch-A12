package com.catwatch.detector.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CatEventEntity): Long

    @Query("SELECT * FROM cat_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<CatEventEntity>>

    @Query("SELECT * FROM cat_events ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllEventsPaged(limit: Int, offset: Int): List<CatEventEntity>

    @Query("SELECT * FROM cat_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEventsByDateRange(startTime: Long, endTime: Long): Flow<List<CatEventEntity>>

    @Delete
    suspend fun deleteEvent(event: CatEventEntity)

    @Query("DELETE FROM cat_events WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<Long>)

    @Query("DELETE FROM cat_events WHERE timestamp < :threshold")
    suspend fun deleteOldEvents(threshold: Long)
}