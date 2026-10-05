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
    private val context: Context? = null,
    private val imageCapture: ImageCapture? = null,
    private val ioExecutor: ExecutorService? = null,
    private val onEventLogged: ((File, Float) -> Unit)? = null,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val snapshotAction: ((Float) -> Unit)? = null
) {
    private var lastCaptureTimestamp = 0L
    private val cooldownDurationMs = 15_000L // 15 segundos entre eventos consecutivos

    fun onCatCandidate(confidence: Float = 1.0f): Boolean {
        val now = currentTimeProvider()
        if (now - lastCaptureTimestamp < cooldownDurationMs) {
            // Animal ainda em cena ou evento duplicado recente
            return false
        }

        lastCaptureTimestamp = now
        if (snapshotAction != null) {
            snapshotAction.invoke(confidence)
        } else {
            executeSnapshot(confidence)
        }
        return true
    }

    private fun executeSnapshot(confidence: Float) {
        val ctx = context ?: return
        val capture = imageCapture ?: return
        val executor = ioExecutor ?: return
        val onLogged = onEventLogged ?: return

        val storageDir = File(ctx.filesDir, "cat_events").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(storageDir, "CAT_${timestamp}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("CaptureCoordinator", "Snapshot gravado com sucesso: ${photoFile.absolutePath}")
                    onLogged.invoke(photoFile, confidence)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureCoordinator", "Falha na gravação do snapshot", exception)
                }
            }
        )
    }
}