package com.catwatch.detector.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureCoordinatorTest {

    private var simulatedTimeMs = 100_000L
    private var snapshotsExecuted = 0
    private var lastRecordedConfidence = 0.0f

    @Before
    fun setUp() {
        simulatedTimeMs = 100_000L
        snapshotsExecuted = 0
        lastRecordedConfidence = 0.0f
    }

    private fun createCoordinator(): CaptureCoordinator {
        return CaptureCoordinator(
            currentTimeProvider = { simulatedTimeMs },
            snapshotAction = { confidence ->
                snapshotsExecuted++
                lastRecordedConfidence = confidence
            }
        )
    }

    @Test
    fun onCatCandidate_firstTrigger_executesSnapshot() {
        val coordinator = createCoordinator()

        val triggered = coordinator.onCatCandidate(0.85f)

        assertTrue("Primeiro candidato deve disparar a captura", triggered)
        assertEquals(1, snapshotsExecuted)
        assertEquals(0.85f, lastRecordedConfidence, 0.001f)
    }

    @Test
    fun onCatCandidate_withinCooldown_ignoresCandidate() {
        val coordinator = createCoordinator()

        // 1º disparo em t = 100_000ms
        assertTrue(coordinator.onCatCandidate(0.90f))
        assertEquals(1, snapshotsExecuted)

        // 2º candidato em t = 105_000ms (5 segundos depois, dentro dos 15s de cooldown)
        simulatedTimeMs += 5_000L
        assertFalse(
            "Candidato dentro da janela de 15s de cooldown deve ser ignorado",
            coordinator.onCatCandidate(0.92f)
        )
        assertEquals("Nenhum snapshot adicional deve ser executado no cooldown", 1, snapshotsExecuted)

        // 3º candidato em t = 114_999ms (14.999 segundos depois, ainda dentro do cooldown)
        simulatedTimeMs = 114_999L
        assertFalse(
            "Candidato antes de completar 15s ainda deve ser ignorado",
            coordinator.onCatCandidate(0.88f)
        )
        assertEquals(1, snapshotsExecuted)
    }

    @Test
    fun onCatCandidate_afterCooldownExpires_acceptsCandidate() {
        val coordinator = createCoordinator()

        // 1º disparo em t = 100_000ms
        assertTrue(coordinator.onCatCandidate(0.85f))
        assertEquals(1, snapshotsExecuted)

        // Avança 15.001ms (após expirar o cooldown de 15.000ms)
        simulatedTimeMs += 15_001L
        assertTrue(
            "Após expirar os 15s de cooldown, novo candidato deve disparar captura",
            coordinator.onCatCandidate(0.89f)
        )
        assertEquals(2, snapshotsExecuted)
        assertEquals(0.89f, lastRecordedConfidence, 0.001f)
    }
}
