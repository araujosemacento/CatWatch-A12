package com.catwatch.detector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.catwatch.detector.camera.CatDetectorAnalyzer
import com.catwatch.detector.core.CaptureCoordinator
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.data.CatWatchDatabase
import com.catwatch.detector.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CatEventAdapter
    private lateinit var database: CatWatchDatabase

    private var imageCapture: ImageCapture? = null
    private var captureCoordinator: CaptureCoordinator? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ioExecutor: ExecutorService

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissão da câmera é necessária para o monitoramento.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mantém tela ligada enquanto o app estiver no primeiro plano
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        database = CatWatchDatabase.getDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        ioExecutor = Executors.newSingleThreadExecutor()

        setupRecyclerView()
        checkAndRequestPermissions()
        loadEventsFromDatabase()
    }

    private fun setupRecyclerView() {
        adapter = CatEventAdapter()
        binding.eventsRecyclerView.adapter = adapter
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startCamera()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Preview vinculado ao ViewFinder (modo compatível)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // 2. ImageCapture configurado para fotos sob demanda
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture

            // 3. CaptureCoordinator para gerenciar o cooldown e salvar em disco
            val coordinator = CaptureCoordinator(
                context = applicationContext,
                imageCapture = capture,
                ioExecutor = ioExecutor,
                onEventLogged = { photoFile, confidence ->
                    val event = CatEventEntity(
                        timestamp = System.currentTimeMillis(),
                        filePath = photoFile.absolutePath,
                        confidence = confidence
                    )
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.catEventDao().insert(event)
                        loadEventsFromDatabase()
                    }
                }
            )
            captureCoordinator = coordinator

            // 4. ImageAnalysis travado em VGA (640x480) com subamostragem temporal
            @Suppress("DEPRECATION")
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer = CatDetectorAnalyzer(
                onCatDetected = { confidence ->
                    coordinator.onCatCandidate(confidence)
                }
            )

            imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

            // 5. Vinculação ao ciclo de vida da Activity
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    capture
                )
                Log.i("MainActivity", "CameraX inicializado e vinculado ao ciclo de vida com sucesso.")
            } catch (exc: Exception) {
                Log.e("MainActivity", "Falha ao vincular casos de uso da CameraX", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadEventsFromDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            val events = database.catEventDao().getAllEventsPaged(limit = 100, offset = 0)
            withContext(Dispatchers.Main) {
                adapter.submitList(events)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ioExecutor.shutdown()
    }
}
