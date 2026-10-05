package com.catwatch.detector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatEventEntityTest {

    @Test
    fun createCatEventEntity_holdsExpectedValues() {
        val now = 1728000000000L
        val path = "/data/data/com.catwatch.detector/files/cat_events/CAT_test.jpg"
        val confidence = 0.94f

        val entity = CatEventEntity(
            id = 1L,
            timestamp = now,
            filePath = path,
            confidence = confidence
        )

        assertEquals(1L, entity.id)
        assertEquals(now, entity.timestamp)
        assertEquals(path, entity.filePath)
        assertEquals(confidence, entity.confidence, 0.001f)
    }

    @Test
    fun defaultId_isZero() {
        val entity = CatEventEntity(
            timestamp = System.currentTimeMillis(),
            filePath = "/fake/path.jpg",
            confidence = 0.88f
        )

        assertEquals(0L, entity.id)
        assertTrue(entity.confidence >= 0.60f)
    }
}
