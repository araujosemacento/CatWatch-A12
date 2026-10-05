package com.catwatch.detector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cat_events")
data class CatEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val filePath: String,
    val confidence: Float
)