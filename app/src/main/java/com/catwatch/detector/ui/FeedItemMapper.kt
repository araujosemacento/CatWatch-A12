package com.catwatch.detector.ui

import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.ui.model.FeedItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FeedItemMapper {
    private val dateFormatDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun buildFeedItems(events: List<CatEventEntity>): List<FeedItem> {
        if (events.isEmpty()) return emptyList()

        val result = mutableListOf<FeedItem>()
        val eventsByDate = events.groupBy { dateFormatDate.format(Date(it.timestamp)) }

        for ((dateText, dateEvents) in eventsByDate) {
            result.add(FeedItem.DateHeader(dateText))

            val eventsByHour = dateEvents.groupBy {
                Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
            }

            for ((hour, sessionEvents) in eventsByHour) {
                val sessionId = "${dateText}_$hour"
                val hourRange = "%02d:00 ~ %02d:00".format(hour, hour + 1)
                val cover = sessionEvents.first()

                result.add(
                    FeedItem.SessionHeader(
                        sessionId = sessionId,
                        dateText = dateText,
                        hourRange = hourRange,
                        itemCount = sessionEvents.size,
                        coverPhoto = cover,
                        eventsInSession = sessionEvents
                    )
                )
            }
        }
        return result
    }
}
