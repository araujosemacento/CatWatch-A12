package com.catwatch.detector.ui

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import com.catwatch.detector.R
import com.catwatch.detector.camera.CatDetectorAnalyzer
import com.catwatch.detector.core.CaptureCoordinator
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.data.CatWatchDatabase
import com.catwatch.detector.databinding.ActivityMainBinding
import com.catwatch.detector.databinding.DialogFullscreenImageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CatEventAdapter
    private val viewModel: CatEventViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var captureCoordinator: CaptureCoordinator? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ioExecutor: ExecutorService

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

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

        cameraExecutor = Executors.newSingleThreadExecutor()
        ioExecutor = Executors.newSingleThreadExecutor()

        setupRecyclerView()
        setupToolbars()
        setupFilterChips()
        checkAndRequestPermissions()

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.eventsState.collect { events ->
                updateFeed(events)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CatEventAdapter()
        binding.eventsRecyclerView.adapter = adapter

        adapter.onItemClick = { event ->
            showFullscreenDialog(event)
        }

        adapter.onSelectionChanged = { selectedCount ->
            binding.selectionCountTextView.text = "$selectedCount selecionados"
            binding.deleteSelectedButton.isEnabled = selectedCount > 0
        }
    }

    private fun setupToolbars() {
        binding.selectModeButton.setOnClickListener {
            enterSelectionMode()
        }

        binding.cancelSelectionButton.setOnClickListener {
            exitSelectionMode()
        }

        binding.selectAllButton.setOnClickListener {
            adapter.selectAll()
        }

        binding.deleteSelectedButton.setOnClickListener {
            confirmBatchDeletion()
        }
    }

    private fun enterSelectionMode() {
        adapter.setSelectionMode(true)
        binding.normalToolbar.visibility = View.GONE
        binding.selectionToolbar.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        binding.selectionToolbar.visibility = View.GONE
        binding.normalToolbar.visibility = View.VISIBLE
    }

    private fun confirmBatchDeletion() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Excluir Fotos")
            .setMessage("Deseja excluir permanentemente ${selectedItems.size} fotos selecionadas do banco e do disco?")
            .setPositiveButton("Excluir") { _, _ ->
                executeBatchDeletion(selectedItems)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executeBatchDeletion(items: List<CatEventEntity>) {
        viewModel.deleteEventsBatch(items) {
            Toast.makeText(this@MainActivity, "${items.size} fotos excluídas com sucesso.", Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { applyFilterAll() }
        binding.chipToday.setOnClickListener { applyFilterToday() }
        binding.chip24h.setOnClickListener { applyFilter24h() }
        binding.chipYesterday.setOnClickListener { applyFilterYesterday() }
        binding.chipCustomRange.setOnClickListener { openDateRangePicker() }
    }

    private fun applyFilterAll() {
        viewModel.loadAllEvents()
    }

    private fun applyFilterToday() {
        viewModel.loadEventsToday()
    }

    private fun applyFilter24h() {
        viewModel.loadEventsLast24h()
    }

    private fun applyFilterYesterday() {
        viewModel.loadEventsYesterday()
    }

    private fun openDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Selecione o Intervalo")
            .build()

        picker.addOnPositiveButtonClickListener { selection: Pair<Long, Long>? ->
            if (selection != null) {
                val start = selection.first ?: return@addOnPositiveButtonClickListener
                // Adiciona o final do dia de término (23:59:59)
                val end = (selection.second ?: start) + (24 * 60 * 60 * 1000L - 1)

                viewModel.loadEventsByRange(start, end)
            }
        }

        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun updateFeed(events: List<CatEventEntity>) {
        adapter.submitList(events)
        binding.feedTitleTextView.text = "Detecções (${events.size})"
        binding.emptyStateTextView.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showFullscreenDialog(event: CatEventEntity) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogFullscreenImageBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Metadados (sem emojis)
        dialogBinding.dialogTimestamp.text = "Data/Hora: ${dateFormat.format(Date(event.timestamp))}"
        dialogBinding.dialogConfidence.text = "Confiança: %.1f%%".format(event.confidence * 100)
        dialogBinding.dialogFilePath.text = "Arquivo: ${event.filePath}"

        if (event.isConfirmedDrinking || event.eventType == "DRINKING") {
            dialogBinding.dialogStatusBadge.text = "Hidratação Confirmada"
            dialogBinding.dialogStatusBadge.setBackgroundResource(R.drawable.bg_badge_drinking)
            dialogBinding.dialogStatusBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_water_drop, 0, 0, 0)
        } else {
            dialogBinding.dialogStatusBadge.text = "Aproximação"
            dialogBinding.dialogStatusBadge.setBackgroundResource(R.drawable.bg_badge_approach)
            dialogBinding.dialogStatusBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_visibility, 0, 0, 0)
        }

        // Carrega imagem em alta resolução
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = loadHighResBitmap(event.filePath, 1080, 1080)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    dialogBinding.dialogFullImageView.setImageBitmap(bitmap)
                } else {
                    dialogBinding.dialogFullImageView.setImageResource(android.R.drawable.ic_menu_camera)
                }
            }
        }

        dialogBinding.dialogCloseButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.dialogDeleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Excluir Registro")
                .setMessage("Deseja excluir permanentemente este registro?")
                .setPositiveButton("Excluir") { _, _ ->
                    viewModel.deleteEvent(event) {
                        Toast.makeText(this@MainActivity, "Registro excluído.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        dialog.show()
    }

    private fun loadHighResBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            null
        }
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

            // 3. CaptureCoordinator para gerenciar o Dual-Snapshot, cooldown e salvar na Galeria
            val coordinator = CaptureCoordinator(
                context = applicationContext,
                imageCapture = capture,
                ioExecutor = ioExecutor,
                coroutineScope = lifecycleScope,
                onEventLogged = { filePath, confidence, eventType, isConfirmed ->
                    val event = CatEventEntity(
                        timestamp = System.currentTimeMillis(),
                        filePath = filePath,
                        confidence = confidence,
                        eventType = eventType,
                        isConfirmedDrinking = isConfirmed
                    )
                    viewModel.insertEvent(event)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ioExecutor.shutdown()
    }
}
