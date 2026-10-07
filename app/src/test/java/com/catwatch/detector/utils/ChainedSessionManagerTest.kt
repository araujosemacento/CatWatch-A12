package com.catwatch.detector.utils

import com.catwatch.detector.data.CatEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChainedSessionManagerTest {

    private fun createTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun isSameHour_sameHour_returnsTrue() {
        val t1 = createTimestamp(2026, Calendar.OCTOBER, 7, 14, 5)
        val t2 = createTimestamp(2026, Calendar.OCTOBER, 7, 14, 55)

        assertTrue(ChainedSessionManager.isSameHour(t1, t2))
    }

    @Test
    fun isSameHour_differentHours_returnsFalse() {
        val t1 = createTimestamp(2026, Calendar.OCTOBER, 7, 14, 55)
        val t2 = createTimestamp(2026, Calendar.OCTOBER, 7, 15, 5)

        assertFalse(ChainedSessionManager.isSameHour(t1, t2))
    }

    @Test
    fun getChainedSessionForEvent_eventsInSameHour_groupedTogether() {
        val t14_05 = createTimestamp(2026, Calendar.OCTOBER, 7, 14, 5)
        val t14_50 = createTimestamp(2026, Calendar.OCTOBER, 7, 14, 50)
        val t15_05 = createTimestamp(2026, Calendar.OCTOBER, 7, 15, 5)

        val e1 = CatEventEntity(id = 1L, timestamp = t14_05, filePath = "1.jpg", confidence = 0.85f)
        val e2 = CatEventEntity(id = 2L, timestamp = t14_50, filePath = "2.jpg", confidence = 0.90f)
        val e3 = CatEventEntity(id = 3L, timestamp = t15_05, filePath = "3.jpg", confidence = 0.88f)

        val allEvents = listOf(e1, e2, e3)

        val session14h = ChainedSessionManager.getChainedSessionForEvent(e1, allEvents)
        val session15h = ChainedSessionManager.getChainedSessionForEvent(e3, allEvents)

        assertEquals(2, session14h.size)
        assertEquals(listOf(1L, 2L), session14h.map { it.id })

        assertEquals(1, session15h.size)
        assertEquals(3L, session15h[0].id)
    }
}
