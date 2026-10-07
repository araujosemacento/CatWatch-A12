package com.catwatch.detector.core

import com.catwatch.detector.data.CatEventDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.File

class StoragePurgeManagerTest {

    @Test
    fun purgeOldEvents_callsDaoDeleteOldEventsWithCalculatedThreshold() = runBlocking {
        val dao = mock(CatEventDao::class.java)
        val now = 100_000_000_000L
        val daysToKeep = 30
        val expectedThreshold = now - (daysToKeep * 24 * 60 * 60 * 1000L)

        val purgeManager = StoragePurgeManager(dao, currentTimeProvider = { now })
        purgeManager.purgeOldEvents(daysToKeep)

        verify(dao).deleteOldEvents(expectedThreshold)
    }

    @Test
    fun purgeOrphanFiles_deletesFilesOlderThanThreshold() = runBlocking {
        val dao = mock(CatEventDao::class.java)
        val tempDir = File.createTempFile("test_purge_", "").apply {
            delete()
            mkdir()
        }
        try {
            val now = 100_000_000_000L
            val oldFile = File(tempDir, "old.jpg").apply {
                createNewFile()
                setLastModified(now - (35 * 24 * 60 * 60 * 1000L))
            }
            val newFile = File(tempDir, "new.jpg").apply {
                createNewFile()
                setLastModified(now - (5 * 24 * 60 * 60 * 1000L))
            }

            val purgeManager = StoragePurgeManager(dao, currentTimeProvider = { now })
            purgeManager.purgeOrphanFiles(tempDir, daysToKeep = 30)

            assertFalse(oldFile.exists())
            assertTrue(newFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
