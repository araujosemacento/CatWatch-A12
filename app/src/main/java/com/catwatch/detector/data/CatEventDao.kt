package com.catwatch.detector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CatEventDao {
    @Insert
    suspend fun insert(event: CatEventEntity): Long

    @Query("SELECT * FROM cat_events ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllEventsPaged(limit: Int, offset: Int): List<CatEventEntity>

    @Query("DELETE FROM cat_events WHERE timestamp < :threshold")
    suspend fun deleteOldEvents(threshold: Long)
}