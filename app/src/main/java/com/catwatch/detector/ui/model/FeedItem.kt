package com.catwatch.detector.ui.model

import com.catwatch.detector.data.CatEventEntity

sealed class FeedItem {
    abstract val id: String

    data class DateHeader(
        val dateText: String
    ) : FeedItem() {
        override val id: String = "date_$dateText"
    }

    data class SessionHeader(
        val sessionId: String,
        val dateText: String,
        val hourRange: String,
        val itemCount: Int,
        val coverPhoto: CatEventEntity,
        val eventsInSession: List<CatEventEntity>
    ) : FeedItem() {
        override val id: String = "session_$sessionId"
    }
}
