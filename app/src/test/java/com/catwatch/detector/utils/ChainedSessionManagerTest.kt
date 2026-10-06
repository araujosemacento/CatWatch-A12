package com.catwatch.detector.utils

import com.catwatch.detector.data.CatEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainedSessionManagerTest {

    @Test
    fun getChainedSessionForEvent_singleEvent_returnsSingleItem() {
        val event = CatEventEntity(id = 1L, timestamp = 1_000_000L, filePath = "path1.jpg", confidence = 0.85f)
        val allEvents = listOf(event)

        val session = ChainedSessionManager.getChainedSessionForEvent(event, allEvents)

        assertEquals(1, session.size)
        assertEquals(1L, session[0].id)
    }

    @Test
    fun getChainedSessionForEvent_dualSnapshotWithin5Seconds_groupsIntoSameSession() {
        val approach = CatEventEntity(id = 1L, timestamp = 1_000_000L, filePath = "approach.jpg", confidence = 0.85f, eventType = "APPROACH")
        val drinking = CatEventEntity(id = 2L, timestamp = 1_005_000L, filePath = "drinking.jpg", confidence = 0.92f, eventType = "DRINKING", isConfirmedDrinking = true)
        val oldEvent = CatEventEntity(id = 3L, timestamp = 500_000L, filePath = "old.jpg", confidence = 0.88f)

        val allEvents = listOf(approach, drinking, oldEvent)

        val sessionFromApproach = ChainedSessionManager.getChainedSessionForEvent(approach, allEvents)
        val sessionFromDrinking = ChainedSessionManager.getChainedSessionForEvent(drinking, allEvents)

        assertEquals(2, sessionFromApproach.size)
        assertEquals(listOf(1L, 2L), sessionFromApproach.map { it.id })

        assertEquals(2, sessionFromDrinking.size)
        assertEquals(listOf(1L, 2L), sessionFromDrinking.map { it.id })
    }

    @Test
    fun getChainedSessionForEvent_eventFartherThan5Minutes_isolatedFromSession() {
        val event1 = CatEventEntity(id = 1L, timestamp = 1_000_000L, filePath = "1.jpg", confidence = 0.85f)
        // 6 minutos depois (360.000 ms)
        val event2 = CatEventEntity(id = 2L, timestamp = 1_360_000L, filePath = "2.jpg", confidence = 0.89f)

        val allEvents = listOf(event1, event2)

        val session1 = ChainedSessionManager.getChainedSessionForEvent(event1, allEvents)
        val session2 = ChainedSessionManager.getChainedSessionForEvent(event2, allEvents)

        assertEquals(1, session1.size)
        assertEquals(1L, session1[0].id)

        assertEquals(1, session2.size)
        assertEquals(2L, session2[0].id)
    }
}
