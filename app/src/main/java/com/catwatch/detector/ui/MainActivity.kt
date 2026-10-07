package com.catwatch.detector.ui

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.Configuration
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
    private var detectorAnalyzer: CatDetectorAnalyzer? = null

    private var currentCameraProvider: ProcessCameraProvider? = null
    private var isCameraActive: Boolean = false
    private var isStickyModeEnabled: Boolean = true
    private var isPanelExpanded: Boolean = false
    private var reposeTranslationY: Float = 0f

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ioExecutor: ExecutorService

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            val prefs = getSharedPreferences("catwatch_prefs", android.content.Context.MODE_PRIVATE)
            val savedMode = prefs.getInt("camera_mode", R.id.btnModeMonitor)
            if (savedMode != R.id.btnModeOff) {
                startCamera()
            } else {
                isCameraActive = false
                Log.i("MainActivity", "Permissões concedidas. Modo Desligado persistido: sensor mantido desativado.")
            }
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

        setupSlidingPanel()
        setupRecyclerView()
        setupToolbars()
        setupFilterChips()
        checkAndRequestPermissions()

        observeViewModel()
    }

    private fun setupSlidingPanel() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (!isPortrait) return

        binding.root.post {
            val totalHeight = binding.root.height.toFloat()
            // Em repouso (45%), o topo do painel ancorado em 20% translada +25% da altura
            reposeTranslationY = totalHeight * 0.25f
            binding.feedPanel.translationY = reposeTranslationY
        }

        var isDraggingPanel = false
        var lastY = 0f
        var startY = 0f
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop

        fun snapPanel() {
            val currentTranslation = binding.feedPanel.translationY
            val target = if (currentTranslation < reposeTranslationY / 2) 0f else reposeTranslationY
            isPanelExpanded = (target == 0f)
            binding.feedPanel.animate().translationY(target).setDuration(250).start()
            
            isStickyModeEnabled = !isPanelExpanded
            if (isPanelExpanded) {
                binding.fabContainer.visibility = View.VISIBLE
            } else {
                binding.fabContainer.visibility = View.GONE
                binding.fabNewItemsBadge.visibility = View.GONE
            }
        }

        binding.eventsRecyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                val layoutManager = binding.eventsRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    ?: return false
                val firstVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition()

                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        isDraggingPanel = false
                        startY = e.rawY
                        lastY = e.rawY
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val totalDy = kotlin.math.abs(e.rawY - startY)
                        if (totalDy > touchSlop && !isDraggingPanel) {
                            if (e.rawY - startY < 0 && binding.feedPanel.translationY > 0f) {
                                isDraggingPanel = true
                            } else if (e.rawY - startY > 0 && firstVisibleItem <= 0 && binding.feedPanel.translationY < reposeTranslationY) {
                                isDraggingPanel = true
                            }
                            if (isDraggingPanel) {
                                binding.feedPanel.animate().cancel()
                                lastY = e.rawY
                                return true
                            }
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingPanel) {
                            snapPanel()
                            isDraggingPanel = false
                            return true
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dy = e.rawY - lastY
                        lastY = e.rawY
                        
                        if (isDraggingPanel) {
                            var newTranslation = binding.feedPanel.translationY + dy
                            newTranslation = newTranslation.coerceIn(0f, reposeTranslationY)
                            binding.feedPanel.translationY = newTranslation
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingPanel) {
                            snapPanel()
                            isDraggingPanel = false
                        }
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.feedItemsState.collect { items ->
                updateFeed(items)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CatEventAdapter()
        binding.eventsRecyclerView.adapter = adapter

        val gridManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        gridManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (adapter.getItemViewType(position)) {
                    CatEventAdapter.TYPE_DATE_HEADER -> 2
                    else -> 1
                }
            }
        }
        binding.eventsRecyclerView.layoutManager = gridManager

        fun getFirstVisibleItemPosition(): Int {
            return (binding.eventsRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
                ?.findFirstVisibleItemPosition() ?: 0
        }

        binding.eventsRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisibleItem = getFirstVisibleItemPosition()

                if (dy > 0) {
                    isStickyModeEnabled = false
                    binding.fabContainer.visibility = View.VISIBLE
                } else if (dy < 0) {
                    if (firstVisibleItem == 0) {
                        isStickyModeEnabled = true
                        binding.fabContainer.visibility = View.GONE
                        binding.fabNewItemsBadge.visibility = View.GONE
                    }
                }
            }
        })

        binding.fabScrollToTop.setOnClickListener {
            binding.eventsRecyclerView.smoothScrollToPosition(0)
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            if (isPortrait && isPanelExpanded) {
                isPanelExpanded = false
                binding.feedPanel.animate().translationY(reposeTranslationY).setDuration(250).start()
            }
            isStickyModeEnabled = true
            binding.fabContainer.visibility = View.GONE
            binding.fabNewItemsBadge.visibility = View.GONE
        }

        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    if (isStickyModeEnabled && getFirstVisibleItemPosition() <= 0) {
                        binding.eventsRecyclerView.scrollToPosition(0)
                    } else {
                        binding.fabContainer.visibility = View.VISIBLE
                        binding.fabNewItemsBadge.visibility = View.VISIBLE
                        pulseBadge()
                    }
                }
            }
        })

        adapter.onAlbumClick = { sessionHeader ->
            CatEventDetailDialogFragment.show(
                supportFragmentManager,
                sessionHeader.eventsInSession,
                sessionHeader.coverPhoto
            ) { evt, onComplete ->
                viewModel.deleteEvent(evt, onComplete)
            }
        }

        adapter.onSelectionChanged = { selectedCount ->
            binding.selectionCountTextView.text = "$selectedCount álbuns"
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

        // Configuração do seletor operacional de 3 estados com persistência
        val prefs = getSharedPreferences("catwatch_prefs", android.content.Context.MODE_PRIVATE)
        val savedMode = prefs.getInt("camera_mode", R.id.btnModeMonitor)
        binding.cameraModeToggleGroup.check(savedMode)
        updateCameraOverlay(savedMode)

        binding.cameraModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            
            prefs.edit().putInt("camera_mode", checkedId).apply()
            updateCameraOverlay(checkedId)
            
            when (checkedId) {
                R.id.btnModeOff -> {
                    detectorAnalyzer?.isAnalysisEnabled = false
                    currentCameraProvider?.unbindAll()
                    isCameraActive = false
                    Toast.makeText(this, "Câmera desativada (Standby).", Toast.LENGTH_SHORT).show()
                }
                R.id.btnModeFrame -> {
                    detectorAnalyzer?.isAnalysisEnabled = false
                    if (!isCameraActive) {
                        startCamera()
                    }
                    Toast.makeText(this, "Modo Enquadrar ativo (sem inferência de IA).", Toast.LENGTH_SHORT).show()
                }
                R.id.btnModeMonitor -> {
                    detectorAnalyzer?.isAnalysisEnabled = true
                    if (!isCameraActive) {
                        startCamera()
                    }
                    Toast.makeText(this, "Modo Monitoramento 24/7 ativo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateCameraOverlay(checkedId: Int) {
        if (checkedId == R.id.btnModeOff) {
            binding.cameraOffOverlay.visibility = View.VISIBLE
        } else {
            binding.cameraOffOverlay.visibility = View.GONE
        }
    }

    private fun pulseBadge() {
        val pulseAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
        binding.fabNewItemsBadge.startAnimation(pulseAnimation)
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
        val selectedItems = adapter.getSelectedEvents()
        if (selectedItems.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Excluir Fotos de Álbuns")
            .setMessage("Deseja excluir permanentemente ${selectedItems.size} fotos pertencentes aos álbuns selecionados?")
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

    private fun updateFeed(items: List<com.catwatch.detector.ui.model.FeedItem>) {
        adapter.submitList(items)
        val totalCount = viewModel.eventsState.value.size
        binding.feedTitleTextView.text = "Detecções ($totalCount)"
        binding.emptyStateTextView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // As lógicas de display e carragamento de imagem foram extraídas para CatEventDetailDialog e ImageUtils

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            val prefs = getSharedPreferences("catwatch_prefs", android.content.Context.MODE_PRIVATE)
            val savedMode = prefs.getInt("camera_mode", R.id.btnModeMonitor)
            if (savedMode != R.id.btnModeOff) {
                startCamera()
            } else {
                isCameraActive = false
                detectorAnalyzer?.isAnalysisEnabled = false
                Log.i("MainActivity", "Inicialização com permissões ativas. Modo Desligado persistido: câmera mantida em standby.")
            }
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startCamera() {
        val currentMode = binding.cameraModeToggleGroup.checkedButtonId
        if (currentMode == R.id.btnModeOff) {
            isCameraActive = false
            detectorAnalyzer?.isAnalysisEnabled = false
            currentCameraProvider?.unbindAll()
            Log.i("MainActivity", "startCamera() cancelado: app está em modo Desligado (Standby).")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            currentCameraProvider = cameraProvider

            // Validação atômica caso o modo tenha sido alterado para Desligado durante a resolução assíncrona
            val activeMode = binding.cameraModeToggleGroup.checkedButtonId
            if (activeMode == R.id.btnModeOff) {
                cameraProvider.unbindAll()
                isCameraActive = false
                detectorAnalyzer?.isAnalysisEnabled = false
                Log.i("MainActivity", "startCamera() abortado após resolução assíncrona: modo Desligado ativo.")
                return@addListener
            }

            // 1. Preview vinculado ao ViewFinder (modo compatível)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // 2. ImageCapture configurado para fotos sob demanda com compressão de 80% e resolução 720p
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(1280, 720))
                .setJpegQuality(80)
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
            detectorAnalyzer = analyzer

            // Define se a IA processa com base no modo selecionado (Enquadrar vs Monitorar)
            analyzer.isAnalysisEnabled = (activeMode == R.id.btnModeMonitor)

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
                isCameraActive = true
                Log.i("MainActivity", "CameraX inicializado e vinculado ao ciclo de vida com sucesso.")
            } catch (exc: Exception) {
                isCameraActive = false
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
