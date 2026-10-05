package com.catwatch.detector.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class CatDetectorAnalyzer(
    private val onCatDetected: (Float) -> Unit
) : ImageAnalysis.Analyzer {

    // Configuração do detetor on-device com ‘threshold’ de confiança
    private val options = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(0.60f)
        .build()

    private val labeler = ImageLabeling.getClient(options)
    
    // Controle de throttling: Processar no máximo 2 quadros por segundo
    private var lastAnalyzedTimestamp = 0L
    private val throttleIntervalMs = 500L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Descarte rápido caso o intervalo de subamostragem não tenha decorrido
        if (currentTimestamp - lastAnalyzedTimestamp < throttleIntervalMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
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
                // Silenciar ou registrar ‘logs’ mínimos para não onerar o I/O
            }
            .addOnCompleteListener {
                // Fechar o ImageProxy é imperativo para evitar o congelamento do ‘buffer’ CameraX
                imageProxy.close()
            }
    }
}