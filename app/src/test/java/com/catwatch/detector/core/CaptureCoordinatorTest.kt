package com.catwatch.detector.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCoordinatorTest {

    private var simulatedTimeMs = 100_000L
    private val recordedSnapshots = mutableListOf<SnapshotRecord>()

    data class SnapshotRecord(
        val eventType: String,
        val confidence: Float,
        val isConfirmed: Boolean
    )

    @Before
    fun setUp() {
        simulatedTimeMs = 100_000L
        recordedSnapshots.clear()
    }

    private fun TestScope.createCoordinator(): CaptureCoordinator {
        return CaptureCoordinator(
            coroutineScope = this,
            currentTimeProvider = { simulatedTimeMs },
            snapshotAction = { eventType, confidence, isConfirmed ->
                recordedSnapshots.add(SnapshotRecord(eventType, confidence, isConfirmed))
            },
            confirmationDelayMs = 5_000L,
            cooldownDurationMs = 25_000L
        )
    }

    @Test
    fun onCatCandidate_firstTrigger_immediatelyCapturesApproach() = runTest {
        val coordinator = createCoordinator()

        val triggered = coordinator.onCatCandidate(0.85f)

        assertTrue("Primeiro candidato deve disparar a captura", triggered)
        assertEquals(1, recordedSnapshots.size)
        assertEquals("APPROACH", recordedSnapshots[0].eventType)
        assertEquals(0.85f, recordedSnapshots[0].confidence, 0.001f)
        assertFalse(recordedSnapshots[0].isConfirmed)
    }

    @Test
    fun onCatCandidate_catRemainsPresentFor5Seconds_triggersDrinkingConfirmation() = runTest {
        val coordinator = createCoordinator()

        // 1. T0: Gato detectado
        assertTrue(coordinator.onCatCandidate(0.85f))
        assertEquals(1, recordedSnapshots.size)

        // 2. Gato continua sendo detectado no frame durante a janela
        simulatedTimeMs += 2_000L
        testScheduler.advanceTimeBy(2_000L)
        assertTrue(coordinator.onCatCandidate(0.92f))

        // 3. Avança o tempo restante para completar os 5 segundos
        simulatedTimeMs += 3_000L
        testScheduler.advanceTimeBy(3_000L)
        runCurrent()

        // Deve ter disparado o segundo snapshot (DRINKING)
        assertEquals(2, recordedSnapshots.size)
        assertEquals("DRINKING", recordedSnapshots[1].eventType)
        assertEquals(0.92f, recordedSnapshots[1].confidence, 0.001f)
        assertTrue(recordedSnapshots[1].isConfirmed)
    }

    @Test
    fun onCatCandidate_catLeavesBefore5Seconds_doesNotTriggerDrinkingConfirmation() = runTest {
        val coordinator = createCoordinator()

        // 1. T0: Gato detectado
        assertTrue(coordinator.onCatCandidate(0.85f))
        assertEquals(1, recordedSnapshots.size)

        // O gato NÃO é detectado novamente. Avança 5s.
        simulatedTimeMs += 5_000L
        testScheduler.advanceTimeBy(5_000L)

        // Não deve disparar DRINKING se não houve detecção adicional na janela
        assertEquals(1, recordedSnapshots.size)
        assertEquals("APPROACH", recordedSnapshots[0].eventType)
    }

    @Test
    fun onCatCandidate_withinCooldown_ignoresSubsequentTriggers() = runTest {
        val coordinator = createCoordinator()

        // T0 em t = 100_000ms
        assertTrue(coordinator.onCatCandidate(0.90f))
        assertEquals(1, recordedSnapshots.size)

        // Conclui os 5s de monitoramento
        simulatedTimeMs += 5_000L
        testScheduler.advanceTimeBy(5_000L)

        // t = 115_000ms (10 segundos dentro do cooldown de 25s)
        simulatedTimeMs += 10_000L
        testScheduler.advanceTimeBy(10_000L)
        assertFalse(
            "Candidato dentro do cooldown de 25s deve ser rejeitado",
            coordinator.onCatCandidate(0.95f)
        )
        assertEquals(1, recordedSnapshots.size)
    }

    @Test
    fun onCatCandidate_afterCooldownExpires_startsNewSession() = runTest {
        val coordinator = createCoordinator()

        // 1º evento em t = 100_000ms
        assertTrue(coordinator.onCatCandidate(0.85f))
        simulatedTimeMs += 5_000L
        testScheduler.advanceTimeBy(5_000L)
        runCurrent()

        // Avança 25.001ms após a finalização da sessão anterior
        simulatedTimeMs += 25_001L
        testScheduler.advanceTimeBy(25_001L)
        runCurrent()

        assertTrue(
            "Após expirar o cooldown de 25s, nova aproximação deve ser aceita",
            coordinator.onCatCandidate(0.89f)
        )
        assertEquals(2, recordedSnapshots.size)
        assertEquals("APPROACH", recordedSnapshots.last().eventType)
    }
}
