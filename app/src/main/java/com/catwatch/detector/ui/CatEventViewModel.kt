package com.catwatch.detector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.data.CatEventRepository
import com.catwatch.detector.data.CatWatchDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CatEventViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CatEventRepository
    private var filterJob: Job? = null

    private val _eventsState = MutableStateFlow<List<CatEventEntity>>(emptyList())
    val eventsState: StateFlow<List<CatEventEntity>> = _eventsState.asStateFlow()

    init {
        val database = CatWatchDatabase.getDatabase(application)
        repository = CatEventRepository(application, database.catEventDao())
        loadAllEvents()
    }

    fun loadAllEvents() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getAllEvents()
                .catch { /* Lidar com erro se necessário */ }
                .collect { events ->
                    _eventsState.value = events
                }
        }
    }

    fun loadEventsToday() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsToday()
                .catch { }
                .collect { events ->
                    _eventsState.value = events
                }
        }
    }

    fun loadEventsLast24h() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsLast24h()
                .catch { }
                .collect { events ->
                    _eventsState.value = events
                }
        }
    }

    fun loadEventsYesterday() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsYesterday()
                .catch { }
                .collect { events ->
                    _eventsState.value = events
                }
        }
    }

    fun loadEventsByRange(start: Long, end: Long) {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            repository.getEventsByRange(start, end)
                .catch { }
                .collect { events ->
                    _eventsState.value = events
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

    /**
     * Identifica a sessão encadeada à qual o [event] pertence dentro da lista [allEvents].
     * Agrupa eventos com diferença temporal consecutiva menor ou igual a 5 minutos.
     */
    fun getChainedSessionForEvent(
        event: CatEventEntity,
        allEvents: List<CatEventEntity> = _eventsState.value
    ): List<CatEventEntity> {
        return com.catwatch.detector.utils.ChainedSessionManager.getChainedSessionForEvent(event, allEvents)
    }
}