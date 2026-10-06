package com.catwatch.detector.utils

import com.catwatch.detector.data.CatEventEntity

object ChainedSessionManager {
    const val CHAINED_SESSION_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutos

    /**
     * Identifica a sessão encadeada à qual o [event] pertence dentro da lista [allEvents].
     * Agrupa eventos com diferença temporal consecutiva menor ou igual a 5 minutos.
     * Retorna a lista da sessão ordenada cronologicamente (do início ao fim da sessão).
     */
    fun getChainedSessionForEvent(
        event: CatEventEntity,
        allEvents: List<CatEventEntity>
    ): List<CatEventEntity> {
        if (allEvents.isEmpty()) return listOf(event)

        val sortedAsc = allEvents.sortedBy { it.timestamp }
        val targetIndex = sortedAsc.indexOfFirst { it.id == event.id }
        if (targetIndex == -1) return listOf(event)

        var startIndex = targetIndex
        while (startIndex > 0 &&
            (sortedAsc[startIndex].timestamp - sortedAsc[startIndex - 1].timestamp) <= CHAINED_SESSION_THRESHOLD_MS
        ) {
            startIndex--
        }

        var endIndex = targetIndex
        while (endIndex < sortedAsc.size - 1 &&
            (sortedAsc[endIndex + 1].timestamp - sortedAsc[endIndex].timestamp) <= CHAINED_SESSION_THRESHOLD_MS
        ) {
            endIndex++
        }

        return sortedAsc.subList(startIndex, endIndex + 1)
    }
}
