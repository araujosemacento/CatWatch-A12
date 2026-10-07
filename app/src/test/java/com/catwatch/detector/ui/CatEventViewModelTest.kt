package com.catwatch.detector.ui

import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.ui.model.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CatEventViewModelTest {

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
    fun buildFeedItems_groupsEventsByDateAndHourSessionAlbums() {
        val t1 = createTimestamp(2026, Calendar.OCTOBER, 7, 17, 15)
        val t2 = createTimestamp(2026, Calendar.OCTOBER, 7, 17, 45)
        val t3 = createTimestamp(2026, Calendar.OCTOBER, 7, 18, 10)

        val e1 = CatEventEntity(id = 1L, timestamp = t1, filePath = "1.jpg", confidence = 0.90f)
        val e2 = CatEventEntity(id = 2L, timestamp = t2, filePath = "2.jpg", confidence = 0.92f)
        val e3 = CatEventEntity(id = 3L, timestamp = t3, filePath = "3.jpg", confidence = 0.88f)

        val items = FeedItemMapper.buildFeedItems(listOf(e1, e2, e3))

        // Esperado: 1 DateHeader + 2 SessionHeader (Álbum 17h com 2 fotos, Álbum 18h com 1 foto)
        assertEquals(3, items.size)
        assertTrue(items[0] is FeedItem.DateHeader)
        assertTrue(items[1] is FeedItem.SessionHeader)
        assertTrue(items[2] is FeedItem.SessionHeader)

        val album17h = items[1] as FeedItem.SessionHeader
        assertEquals(2, album17h.itemCount)
        assertEquals("17:00 ~ 18:00", album17h.hourRange)

        val album18h = items[2] as FeedItem.SessionHeader
        assertEquals(1, album18h.itemCount)
        assertEquals("18:00 ~ 19:00", album18h.hourRange)
    }
}
