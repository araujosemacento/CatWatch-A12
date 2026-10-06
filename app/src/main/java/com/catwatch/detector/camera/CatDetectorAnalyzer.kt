package com.catwatch.detector.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class CatDetectorAnalyzer(
    private val onCatDetected: (Float) -> Unit,
    private val labeler: com.google.mlkit.vision.label.ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.80f).build()
    ),
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) : ImageAnalysis.Analyzer {

    // Controle de ativação do ML Kit (permite modo de apenas enquadramento da câmera com zero custo de IA)
    @Volatile
    var isAnalysisEnabled: Boolean = true

    // Controle de throttling: Processar no máximo 2 quadros por segundo
    private var lastAnalyzedTimestamp = 0L
    private val throttleIntervalMs = 500L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isAnalysisEnabled) {
            imageProxy.close()
            return
        }

        val currentTimestamp = currentTimeProvider()

        // Descarte rápido caso o intervalo de subamostragem não tenha decorrido
        if (currentTimestamp - lastAnalyzedTimestamp < throttleIntervalMs) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        labeler.process(inputImage)
            .addOnSuccessListener { labels ->
                for (label in labels) {
                    val labelText = label.text
                    // O modelo Image Labeling identifica entidades específicas de felinos
                    if (labelText.equals("Cat", ignoreCase = true) || 
                        labelText.equals("Kitten", ignoreCase = true) ||
                        labelText.equals("Felidae", ignoreCase = true)) {
                        onCatDetected(label.confidence)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                // Silenciar ou registrar logs mínimos para não onerar o I/O
            }
            .addOnCompleteListener {
                // Fechar o ImageProxy é imperativo para evitar o congelamento do buffer CameraX
                imageProxy.close()
            }
    }
}