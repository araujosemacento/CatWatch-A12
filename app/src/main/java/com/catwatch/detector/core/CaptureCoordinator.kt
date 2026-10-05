package com.catwatch.detector.core

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService

class CaptureCoordinator(
    private val context: Context,
    private val imageCapture: ImageCapture,
    private val ioExecutor: ExecutorService,
    private val onEventLogged: (File, Float) -> Unit
) {
    private var lastCaptureTimestamp = 0L
    private val cooldownDurationMs = 15_000L // 15 segundos entre eventos consecutivos

    fun onCatCandidate(confidence: Float = 1.0f) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTimestamp < cooldownDurationMs) {
            // Animal ainda em cena ou evento duplicado recente
            return
        }

        lastCaptureTimestamp = now
        executeSnapshot(confidence)
    }

    private fun executeSnapshot(confidence: Float) {
        val storageDir = File(context.filesDir, "cat_events").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(storageDir, "CAT_${timestamp}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ioExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("CaptureCoordinator", "Snapshot gravado com sucesso: ${photoFile.absolutePath}")
                    onEventLogged(photoFile, confidence)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureCoordinator", "Falha na gravação do snapshot", exception)
                }
            }
        )
    }
}