package com.catwatch.detector.data

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class CatEventRepository(
    private val context: Context,
    private val dao: CatEventDao
) {
    fun getAllEvents(): Flow<List<CatEventEntity>> = dao.getAllEvents()
    
    suspend fun insertEvent(event: CatEventEntity) = withContext(Dispatchers.IO) {
        dao.insert(event)
    }

    fun getEventsToday(): Flow<List<CatEventEntity>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val now = System.currentTimeMillis()
        return dao.getEventsByDateRange(startOfToday, now)
    }

    fun getEventsLast24h(): Flow<List<CatEventEntity>> {
        val now = System.currentTimeMillis()
        val past24h = now - (24 * 60 * 60 * 1000L)
        return dao.getEventsByDateRange(past24h, now)
    }

    fun getEventsYesterday(): Flow<List<CatEventEntity>> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfYesterday = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfYesterday = calendar.timeInMillis

        return dao.getEventsByDateRange(startOfYesterday, endOfYesterday)
    }

    fun getEventsByRange(start: Long, end: Long): Flow<List<CatEventEntity>> {
        return dao.getEventsByDateRange(start, end)
    }

    suspend fun deleteEvent(event: CatEventEntity) = withContext(Dispatchers.IO) {
        deletePhysicalFileAndNotify(event.filePath)
        dao.deleteEvent(event)
    }

    suspend fun deleteEventsBatch(events: List<CatEventEntity>) = withContext(Dispatchers.IO) {
        val ids = events.map { it.id }
        val paths = events.map { it.filePath }
        
        paths.forEach { path ->
            deletePhysicalFileAndNotify(path)
        }
        dao.deleteEventsByIds(ids)
    }
    
    private fun deletePhysicalFileAndNotify(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (e: Exception) {
            Log.e("CatEventRepository", "Erro ao excluir arquivo físico: $path", e)
        }
    }
}