package com.catwatch.detector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.data.CatEventRepository
import com.catwatch.detector.data.CatWatchDatabase
import com.catwatch.detector.ui.model.FeedItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CatEventViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CatEventRepository
    private var filterJob: Job? = null

    private val _rawEventsState = MutableStateFlow<List<CatEventEntity>>(emptyList())
    val eventsState: StateFlow<List<CatEventEntity>> = _rawEventsState.asStateFlow()

    private val _feedItemsState = MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItemsState: StateFlow<List<FeedItem>> = _feedItemsState.asStateFlow()

    init {
        val database = CatWatchDatabase.getDatabase(application)
        repository = CatEventRepository(application, database.catEventDao())
        loadAllEvents()
    }

    private fun updateFeedItems() {
        val rawList = _rawEventsState.value
        _feedItemsState.value = FeedItemMapper.buildFeedItems(rawList)
    }

    fun loadAllEvents() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getAllEvents()
                .catch { }
                .collect { events ->
                    _rawEventsState.value = events
                    updateFeedItems()
                }
        }
    }

    fun loadEventsToday() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsToday()
                .catch { }
                .collect { events ->
                    _rawEventsState.value = events
                    updateFeedItems()
                }
        }
    }

    fun loadEventsLast24h() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsLast24h()
                .catch { }
                .collect { events ->
                    _rawEventsState.value = events
                    updateFeedItems()
                }
        }
    }

    fun loadEventsYesterday() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsYesterday()
                .catch { }
                .collect { events ->
                    _rawEventsState.value = events
                    updateFeedItems()
                }
        }
    }

    fun loadEventsByRange(start: Long, end: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsByRange(start, end)
                .catch { }
                .collect { events ->
                    _rawEventsState.value = events
                    updateFeedItems()
                }
        }
    }

    fun deleteEvent(event: CatEventEntity, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            onComplete()
        }
    }

    fun deleteEventsBatch(events: List<CatEventEntity>, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEventsBatch(events)
            onComplete()
        }
    }

    fun insertEvent(event: CatEventEntity) {
        viewModelScope.launch {
            repository.insertEvent(event)
        }
    }

    fun getChainedSessionForEvent(
        event: CatEventEntity,
        allEvents: List<CatEventEntity> = _rawEventsState.value
    ): List<CatEventEntity> {
        return com.catwatch.detector.utils.ChainedSessionManager.getChainedSessionForEvent(event, allEvents)
    }
}
