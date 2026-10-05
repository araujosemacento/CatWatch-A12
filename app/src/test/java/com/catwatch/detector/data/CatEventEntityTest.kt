package com.catwatch.detector.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatEventEntityTest {

    @Test
    fun createCatEventEntity_holdsExpectedValues() {
        val now = 1728000000000L
        val path = "/storage/emulated/0/Pictures/CatWatch/CAT_APPROACH_test.jpg"
        val confidence = 0.94f

        val entity = CatEventEntity(
            id = 1L,
            timestamp = now,
            filePath = path,
            confidence = confidence,
            eventType = "APPROACH",
            isConfirmedDrinking = false
        )

        assertEquals(1L, entity.id)
        assertEquals(now, entity.timestamp)
        assertEquals(path, entity.filePath)
        assertEquals(confidence, entity.confidence, 0.001f)
        assertEquals("APPROACH", entity.eventType)
        assertFalse(entity.isConfirmedDrinking)
    }

    @Test
    fun defaultValues_areApproachAndNotDrinking() {
        val entity = CatEventEntity(
            timestamp = System.currentTimeMillis(),
            filePath = "/fake/path.jpg",
            confidence = 0.88f
        )

        assertEquals(0L, entity.id)
        assertTrue(entity.confidence >= 0.60f)
        assertEquals("APPROACH", entity.eventType)
        assertFalse(entity.isConfirmedDrinking)
    }

    @Test
    fun confirmedDrinkingEvent_hasCorrectFlags() {
        val entity = CatEventEntity(
            timestamp = System.currentTimeMillis(),
            filePath = "/storage/emulated/0/Pictures/CatWatch/CAT_DRINKING_test.jpg",
            confidence = 0.91f,
            eventType = "DRINKING",
            isConfirmedDrinking = true
        )

        assertEquals("DRINKING", entity.eventType)
        assertTrue(entity.isConfirmedDrinking)
    }
}
