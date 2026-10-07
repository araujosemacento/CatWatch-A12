package com.catwatch.detector.utils

import com.catwatch.detector.data.CatEventEntity
import java.util.Calendar

object ChainedSessionManager {

    /**
     * Verifica se dois timestamps ocorrem no mesmo ano, mesmo dia do ano e mesma hora do dia (0-23h).
     */
    fun isSameHour(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Identifica a sessão encadeada à qual o [event] pertence dentro da lista [allEvents].
     * Agrupa todos os eventos ocorridos na mesma hora civil do dia.
     * Retorna a lista da sessão ordenada cronologicamente (do início ao fim da hora).
     */
    fun getChainedSessionForEvent(
        event: CatEventEntity,
        allEvents: List<CatEventEntity>
    ): List<CatEventEntity> {
        if (allEvents.isEmpty()) return listOf(event)

        val hourEvents = allEvents.filter { isSameHour(it.timestamp, event.timestamp) }
            .sortedBy { it.timestamp }

        return if (hourEvents.isNotEmpty()) hourEvents else listOf(event)
    }
}
