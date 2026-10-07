package com.catwatch.detector.core

import android.util.Log
import com.catwatch.detector.data.CatEventDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StoragePurgeManager(
    private val dao: CatEventDao,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Purga registros e arquivos com idade superior a [daysToKeep] (padrão: 30 dias).
     */
    suspend fun purgeOldEvents(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val thresholdMs = currentTimeProvider() - (daysToKeep * 24 * 60 * 60 * 1000L)
        try {
            dao.deleteOldEvents(thresholdMs)
            Log.i("StoragePurgeManager", "Purga de eventos antigos anterior a $daysToKeep dias concluída.")
        } catch (e: Exception) {
            Log.e("StoragePurgeManager", "Erro ao purgar eventos antigos", e)
        }
    }

    /**
     * Verifica o diretório [storageDir] e remove arquivos soltos que excedem [daysToKeep] dias.
     */
    suspend fun purgeOrphanFiles(storageDir: File, daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        if (!storageDir.exists() || !storageDir.isDirectory) return@withContext

        val thresholdMs = currentTimeProvider() - (daysToKeep * 24 * 60 * 60 * 1000L)
        val files = storageDir.listFiles() ?: return@withContext

        var deletedCount = 0
        for (file in files) {
            if (file.lastModified() < thresholdMs) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        if (deletedCount > 0) {
            Log.i("StoragePurgeManager", "Purga de $deletedCount arquivos antigos soltos concluída.")
        }
    }
}
