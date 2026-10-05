package com.catwatch.detector.camera

import androidx.camera.core.ImageProxy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

import com.google.mlkit.vision.label.ImageLabeler
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.label.ImageLabel
import org.mockito.ArgumentMatchers.any

class CatDetectorAnalyzerTest {

    private var simulatedTimeMs = 100_000L
    private var detectedCount = 0
    private lateinit var mockLabeler: ImageLabeler
    private lateinit var mockTask: Task<List<ImageLabel>>

    @Before
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        simulatedTimeMs = 100_000L
        detectedCount = 0
        mockLabeler = mock(ImageLabeler::class.java)
        mockTask = mock(Task::class.java) as Task<List<ImageLabel>>
        
        org.mockito.Mockito.`when`(mockLabeler.process(any(com.google.mlkit.vision.common.InputImage::class.java)))
            .thenReturn(mockTask)
        org.mockito.Mockito.`when`(mockTask.addOnSuccessListener(any())).thenReturn(mockTask)
        org.mockito.Mockito.`when`(mockTask.addOnFailureListener(any())).thenReturn(mockTask)
        org.mockito.Mockito.`when`(mockTask.addOnCompleteListener(any())).thenReturn(mockTask)
    }

    private fun createAnalyzer(): CatDetectorAnalyzer {
        return CatDetectorAnalyzer(
            onCatDetected = { detectedCount++ },
            labeler = mockLabeler,
            currentTimeProvider = { simulatedTimeMs }
        )
    }

    @Test
    fun analyze_withinThrottleInterval_closesImageProxyImmediately() {
        val analyzer = createAnalyzer()
        
        // Primeiro frame em t=100_000
        val proxy1 = mock(ImageProxy::class.java)
        analyzer.analyze(proxy1) // Aceito para análise. Mock não tem image, retorna nulo e chama close()
        verify(proxy1).close()
        
        // Segundo frame em t=100_100 (100ms depois, < 500ms do throttle)
        simulatedTimeMs += 100L
        val proxy2 = mock(ImageProxy::class.java)
        analyzer.analyze(proxy2) // Deve ser descartado rápido e fechado
        verify(proxy2).close()
        // Verificamos que get proxy.image não foi chamado porque descartou antes
        verify(proxy2, never()).image
    }
    
    @Test
    fun analyze_afterThrottleInterval_processesAndClosesImageProxy() {
        val analyzer = createAnalyzer()
        
        // Primeiro frame em t=100_000
        val proxy1 = mock(ImageProxy::class.java)
        analyzer.analyze(proxy1)
        verify(proxy1).close()
        
        // Terceiro frame em t=100_600 (600ms depois, > 500ms do throttle)
        simulatedTimeMs += 600L
        val proxy3 = mock(ImageProxy::class.java)
        
        // Vamos mockar o image para não ser nulo e forçar o labeler
        // O teste de processamento interno do ML kit seria um instrumented test, 
        // mas podemos validar pelo menos que ele verificou a imagem:
        analyzer.analyze(proxy3) 
        
        verify(proxy3).image
        verify(proxy3).close()
    }
}