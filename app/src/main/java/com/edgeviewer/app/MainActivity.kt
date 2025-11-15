package com.edgeviewer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edgeviewer.app.camera.CameraController
import android.opengl.GLSurfaceView
import com.edgeviewer.app.renderer.GLPreviewSurface
import com.edgeviewer.app.renderer.PipelineRenderer
import com.edgeviewer.app.util.FpsAverager
import com.edgeviewer.app.util.SaveUtils
import com.edgeviewer.app.viewmodel.CameraViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity(), TextureView.SurfaceTextureListener {

    private val viewModel: CameraViewModel by viewModels()

    private lateinit var textureView: TextureView
    private lateinit var glSurface: GLPreviewSurface
    private lateinit var toggleButton: MaterialButton
    private lateinit var switchCameraButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var filterButton: MaterialButton
    private lateinit var fpsLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var errorLabel: TextView
    private lateinit var loadingIndicator: ProgressBar

    private var cameraController: CameraController? = null
    private var renderer: PipelineRenderer? = null
    private val fpsAverager = FpsAverager()

    private var isNativeLoaded = false
    private var isDestroyed = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.INITIALIZING)
                startCameraIfReady()
            } else {
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.PERMISSION_DENIED)
                showError(getString(R.string.status_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            initializeViews()
            setupObservers()
            initializeNativeLibrary()
            initializeRenderer()
            initializeCameraController()
            setupClickListeners()
        } catch (e: Exception) {
            Timber.e(e, "Critical error in onCreate")
            viewModel.setError("Failed to initialize app: ${e.message}")
            showError("App initialization failed. Please restart.")
        }
    }

    private fun initializeViews() {
        try {
            textureView = findViewById(R.id.cameraTexture) ?: throw IllegalStateException("TextureView not found")
            glSurface = findViewById(R.id.glSurface) ?: throw IllegalStateException("GLSurface not found")
            toggleButton = findViewById(R.id.toggleButton) ?: throw IllegalStateException("ToggleButton not found")
            switchCameraButton = findViewById(R.id.switchCameraButton) ?: throw IllegalStateException("SwitchCameraButton not found")
            exportButton = findViewById(R.id.exportButton) ?: throw IllegalStateException("ExportButton not found")
            filterButton = findViewById(R.id.filterButton) ?: throw IllegalStateException("FilterButton not found")
            fpsLabel = findViewById(R.id.fpsLabel) ?: throw IllegalStateException("FpsLabel not found")
            statusLabel = findViewById(R.id.statusLabel) ?: throw IllegalStateException("StatusLabel not found")
            errorLabel = findViewById(R.id.errorLabel) ?: throw IllegalStateException("ErrorLabel not found")
            loadingIndicator = findViewById(R.id.loadingIndicator) ?: throw IllegalStateException("LoadingIndicator not found")

            fpsLabel.text = getString(R.string.fps_label, 0.0)
            statusLabel.visibility = View.GONE
            errorLabel.visibility = View.GONE
            loadingIndicator.visibility = View.GONE
        } catch (e: Exception) {
            Timber.e(e, "Error initializing views")
            throw e
        }
    }

    private fun setupObservers() {
        viewModel.appState.observe(this) { state ->
            if (isDestroyed) return@observe
            updateUI(state)
        }
    }

    private fun updateUI(state: com.edgeviewer.app.viewmodel.AppState) {
        try {
            // Update FPS
            fpsLabel.text = getString(R.string.fps_label, state.fps)

            // Update status
            when (state.cameraState) {
                com.edgeviewer.app.viewmodel.CameraState.IDLE -> {
                    statusLabel.visibility = View.GONE
                    loadingIndicator.visibility = View.GONE
                }
                com.edgeviewer.app.viewmodel.CameraState.INITIALIZING -> {
                    statusLabel.text = getString(R.string.status_initializing)
                    statusLabel.visibility = View.VISIBLE
                    loadingIndicator.visibility = View.VISIBLE
                }
                com.edgeviewer.app.viewmodel.CameraState.READY -> {
                    statusLabel.text = getString(R.string.status_ready)
                    statusLabel.visibility = View.VISIBLE
                    loadingIndicator.visibility = View.GONE
                }
                com.edgeviewer.app.viewmodel.CameraState.RUNNING -> {
                    statusLabel.visibility = View.GONE
                    loadingIndicator.visibility = View.GONE
                }
                com.edgeviewer.app.viewmodel.CameraState.ERROR -> {
                    statusLabel.text = getString(R.string.status_error)
                    statusLabel.visibility = View.VISIBLE
                    loadingIndicator.visibility = View.GONE
                    if (state.errorMessage != null) {
                        showError(state.errorMessage)
                    }
                }
                com.edgeviewer.app.viewmodel.CameraState.PERMISSION_DENIED -> {
                    statusLabel.text = getString(R.string.status_permission_denied)
                    statusLabel.visibility = View.VISIBLE
                    loadingIndicator.visibility = View.GONE
                }
            }

            // Update error message
            if (state.errorMessage != null) {
                errorLabel.text = state.errorMessage
                errorLabel.visibility = View.VISIBLE
            } else {
                errorLabel.visibility = View.GONE
            }

            // Update toggle button text
            toggleButton.text = if (state.viewMode == com.edgeviewer.app.viewmodel.ViewMode.PROCESSED) {
                getString(R.string.processed_view)
            } else {
                getString(R.string.raw_view)
            }

            // Update filter button
            filterButton.text = when (state.filterMode) {
                com.edgeviewer.app.viewmodel.FilterMode.NONE -> getString(R.string.filter_none)
                com.edgeviewer.app.viewmodel.FilterMode.INVERT -> getString(R.string.filter_invert)
                com.edgeviewer.app.viewmodel.FilterMode.THRESHOLD -> getString(R.string.filter_threshold)
            }

            // Update button states
            val isEnabled = state.cameraState == com.edgeviewer.app.viewmodel.CameraState.RUNNING ||
                    state.cameraState == com.edgeviewer.app.viewmodel.CameraState.READY
            toggleButton.isEnabled = isEnabled
            switchCameraButton.isEnabled = isEnabled
            exportButton.isEnabled = isEnabled && state.viewMode == com.edgeviewer.app.viewmodel.ViewMode.PROCESSED
            filterButton.isEnabled = isEnabled

        } catch (e: Exception) {
            Timber.e(e, "Error updating UI")
        }
    }

    private fun initializeNativeLibrary() {
        try {
            NativeBridge.load()
            isNativeLoaded = true
            Timber.d("Native library loaded successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load native library")
            isNativeLoaded = false
            viewModel.setError("Native library not available. Edge detection may not work properly.")
        }
    }

    private fun initializeRenderer() {
        try {
            renderer = PipelineRenderer(glSurface) { fps ->
                if (!isDestroyed) {
                    lifecycleScope.launch {
                        val smoothFps = fpsAverager.track(fps)
                        viewModel.updateFps(smoothFps)
                    }
                }
            }
            glSurface.setRenderer(renderer)
            glSurface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            glSurface.visibility = View.VISIBLE
            textureView.visibility = View.INVISIBLE
            textureView.surfaceTextureListener = this
        } catch (e: Exception) {
            Timber.e(e, "Error initializing renderer")
            viewModel.setError("Failed to initialize renderer: ${e.message}")
        }
    }

    private fun initializeCameraController() {
        try {
            if (renderer == null) {
                Timber.e("Renderer not initialized")
                return
            }
            cameraController = CameraController(
                context = this,
                lifecycleOwner = this,
                listener = renderer!!
            )
        } catch (e: Exception) {
            Timber.e(e, "Error initializing camera controller")
            viewModel.setError("Failed to initialize camera: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        try {
            toggleButton.setOnClickListener {
                try {
                    val currentState = viewModel.appState.value ?: return@setOnClickListener
                    viewModel.toggleViewMode()
                    toggleViewMode(currentState.viewMode == com.edgeviewer.app.viewmodel.ViewMode.PROCESSED)
                } catch (e: Exception) {
                    Timber.e(e, "Error toggling view mode")
                    showError("Failed to toggle view")
                }
            }

            switchCameraButton.setOnClickListener {
                try {
                    cameraController?.let { controller ->
                        if (textureView.isAvailable) {
                            controller.switchCamera(textureView)
                        }
                    } ?: showError("Camera not ready")
                } catch (e: Exception) {
                    Timber.e(e, "Error switching camera")
                    showError("Failed to switch camera")
                }
            }

            exportButton.setOnClickListener {
                try {
                    exportFrame()
                } catch (e: Exception) {
                    Timber.e(e, "Error exporting frame")
                    showError(getString(R.string.save_failed))
                }
            }

            filterButton.setOnClickListener {
                try {
                    viewModel.nextFilter()
                    renderer?.nextFilter()
                } catch (e: Exception) {
                    Timber.e(e, "Error cycling filter")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting up click listeners")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            isDestroyed = false
            requestCameraPermission()
        } catch (e: Exception) {
            Timber.e(e, "Error in onResume")
            viewModel.setError("Error resuming: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            cameraController?.stop()
            viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.IDLE)
        } catch (e: Exception) {
            Timber.e(e, "Error in onPause")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        try {
            cameraController?.stop()
            cameraController = null
            renderer = null
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroy")
        }
    }

    private fun requestCameraPermission() {
        try {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> {
                    startCameraIfReady()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.PERMISSION_DENIED)
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
                else -> {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error requesting camera permission")
            viewModel.setError("Permission error: ${e.message}")
        }
    }

    private fun startCameraIfReady() {
        try {
            if (isDestroyed) return
            if (cameraController == null || renderer == null) {
                Timber.w("Camera controller or renderer not initialized")
                viewModel.setError("Camera not initialized. Please restart the app.")
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.ERROR)
                return
            }
            if (!textureView.isAvailable) {
                Timber.d("Texture view not available yet, waiting...")
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.INITIALIZING)
                return
            }
            viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.INITIALIZING)
            try {
                cameraController?.start(textureView)
                // Camera started successfully - state will be updated when running
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.RUNNING)
            } catch (e: IllegalStateException) {
                // Camera start failed - handle gracefully
                Timber.e(e, "Camera start failed")
                viewModel.setError("Camera unavailable: ${e.message ?: "Unknown error"}")
                viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.ERROR)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting camera")
            viewModel.setError("Failed to start camera: ${e.message ?: "Unknown error"}")
            viewModel.updateCameraState(com.edgeviewer.app.viewmodel.CameraState.ERROR)
        }
    }

    private fun toggleViewMode(wasProcessed: Boolean) {
        try {
            val newMode = !wasProcessed
            renderer?.setShowProcessed(newMode)
            glSurface.visibility = if (newMode) View.VISIBLE else View.INVISIBLE
            textureView.visibility = if (newMode) View.INVISIBLE else View.VISIBLE
        } catch (e: Exception) {
            Timber.e(e, "Error toggling view mode")
        }
    }

    private fun exportFrame() {
        try {
            val snap = renderer?.snapshot()
            if (snap == null) {
                showError("No frame available to save")
                return
            }
            viewModel.setProcessing(true)
            lifecycleScope.launch {
                try {
                    val uri = SaveUtils.saveRgbaToPng(
                        this@MainActivity,
                        snap.data,
                        snap.width,
                        snap.height,
                        "FlamEdge_${System.currentTimeMillis()}.png"
                    )
                    Timber.i("Saved processed frame to $uri")
                    showSuccess(getString(R.string.saved))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save frame")
                    showError(getString(R.string.save_failed))
                } finally {
                    viewModel.setProcessing(false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error exporting frame")
            viewModel.setProcessing(false)
        }
    }

    private fun showError(message: String) {
        try {
            if (isDestroyed) return
            viewModel.setError(message)
            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(this, com.edgeviewer.app.R.color.status_error))
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing error message")
        }
    }

    private fun showSuccess(message: String) {
        try {
            if (isDestroyed) return
            Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(this, com.edgeviewer.app.R.color.status_success))
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing success message")
        }
    }

    // TextureView.SurfaceTextureListener implementation
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Timber.d("Surface texture available: ${width}x${height}")
        if (!isDestroyed) {
            startCameraIfReady()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Timber.d("Surface texture size changed: ${width}x${height}")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Timber.d("Surface texture destroyed")
        try {
            cameraController?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping camera on surface destroyed")
        }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Not needed
    }
}

