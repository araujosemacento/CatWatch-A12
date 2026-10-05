package com.catwatch.detector.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService

class CaptureCoordinator(
    private val context: Context? = null,
    private val imageCapture: ImageCapture? = null,
    private val ioExecutor: ExecutorService? = null,
    private val onEventLogged: ((String, Float, String, Boolean) -> Unit)? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val snapshotAction: ((String, Float, Boolean) -> Unit)? = null,
    val confirmationDelayMs: Long = 5_000L,
    val cooldownDurationMs: Long = 25_000L
) {
    private var lastEventTimestamp = 0L
    private var activeMonitoringJob: Job? = null
    private var lastSeenConfidence = 0.0f
    private var isCatPresent = false

    fun onCatCandidate(confidence: Float = 1.0f): Boolean {
        val now = currentTimeProvider()

        // 1. Se uma janela ativa de confirmação já estiver em andamento, registra presença contínua
        if (activeMonitoringJob?.isActive == true) {
            isCatPresent = true
            lastSeenConfidence = confidence
            return true
        }

        // 2. Se estiver dentro da janela de cooldown pós-evento, ignora
        if (now - lastEventTimestamp < cooldownDurationMs) {
            return false
        }

        // 3. Inicia nova sessão
        lastEventTimestamp = now
        lastSeenConfidence = confidence
        isCatPresent = true

        // Disparo imediato T0 (Aproximação)
        executeSnapshot("APPROACH", confidence, isConfirmed = false)

        // Janela de monitoramento ativo de 5 segundos para confirmação de hidratação
        activeMonitoringJob = coroutineScope.launch {
            isCatPresent = false
            delay(confirmationDelayMs)

            if (isCatPresent) {
                // O animal continuou sendo detectado no local após 5s: confirmação de hidratação
                executeSnapshot("DRINKING", lastSeenConfidence, isConfirmed = true)
            }
            lastEventTimestamp = currentTimeProvider()
        }
        return true
    }

    private fun executeSnapshot(eventType: String, confidence: Float, isConfirmed: Boolean) {
        if (snapshotAction != null) {
            snapshotAction.invoke(eventType, confidence, isConfirmed)
            return
        }

        val ctx = context ?: return
        val capture = imageCapture ?: return
        val executor = ioExecutor ?: return
        val onLogged = onEventLogged ?: return

        val storageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CatWatch"
        ).apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CAT_${eventType}_${timestamp}.jpg"
        val photoFile = File(storageDir, fileName)

        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CatWatch")
            }
            ImageCapture.OutputFileOptions.Builder(
                ctx.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        } else {
            ImageCapture.OutputFileOptions.Builder(photoFile).build()
        }

        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val finalPath = photoFile.absolutePath
                    Log.i("CaptureCoordinator", "Snapshot gravado com sucesso [$eventType]: $finalPath")

                    // Notifica a Galeria do Android / Meus Arquivos da Samsung
                    MediaScannerConnection.scanFile(
                        ctx,
                        arrayOf(finalPath),
                        arrayOf("image/jpeg")
                    ) { path, uri ->
                        Log.d("CaptureCoordinator", "Indexado no MediaStore: $path -> $uri")
                    }

                    onLogged.invoke(finalPath, confidence, eventType, isConfirmed)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureCoordinator", "Falha na gravação do snapshot [$eventType]", exception)
                }
            }
        )
    }
}