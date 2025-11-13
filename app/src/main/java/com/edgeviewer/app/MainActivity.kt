package com.edgeviewer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.edgeviewer.app.camera.CameraController
import com.edgeviewer.app.renderer.GLPreviewSurface
import com.edgeviewer.app.renderer.PipelineRenderer
import com.edgeviewer.app.util.FpsAverager
import timber.log.Timber

class MainActivity : ComponentActivity(), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private lateinit var glSurface: GLPreviewSurface
    private lateinit var toggleButton: Button
    private lateinit var switchCameraButton: Button
    private lateinit var exportButton: Button
    private lateinit var filterButton: Button
    private lateinit var fpsLabel: TextView

    private lateinit var cameraController: CameraController
    private lateinit var renderer: PipelineRenderer
    private val fpsAverager = FpsAverager()

    private var showingProcessed = true

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraIfReady()
            } else {
                Timber.w("Camera permission denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.cameraTexture)
        glSurface = findViewById(R.id.glSurface)
        toggleButton = findViewById(R.id.toggleButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        exportButton = findViewById(R.id.exportButton)
        filterButton = findViewById(R.id.filterButton)
        fpsLabel = findViewById(R.id.fpsLabel)
        fpsLabel.text = getString(R.string.fps_label, 0.0)

        renderer = PipelineRenderer(glSurface, fpsUpdate = ::onFpsUpdated)
        cameraController = CameraController(
            context = this,
            lifecycleOwner = this,
            listener = renderer
        )

        toggleButton.setOnClickListener { toggleOutputMode() }
        switchCameraButton.setOnClickListener { onSwitchCamera() }
        exportButton.setOnClickListener { onExportFrame() }
        filterButton.setOnClickListener { onCycleFilter() }

        textureView.surfaceTextureListener = this
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLPreviewSurface.RENDERMODE_WHEN_DIRTY
        glSurface.visibility = View.VISIBLE
        textureView.visibility = View.INVISIBLE

        NativeBridge.load()
    }

    override fun onResume() {
        super.onResume()
        requestCamera()
    }

    override fun onPause() {
        super.onPause()
        cameraController.stop()
    }

    private fun requestCamera() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> startCameraIfReady()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }

            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraIfReady() {
        if (textureView.isAvailable) {
            cameraController.start(textureView)
        }
    }

    private fun toggleOutputMode() {
        showingProcessed = !showingProcessed
        renderer.setShowProcessed(showingProcessed)
        glSurface.visibility = if (showingProcessed) android.view.View.VISIBLE else android.view.View.INVISIBLE
        textureView.visibility = if (showingProcessed) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    private fun onFpsUpdated(fps: Double) {
        val smoothFps = fpsAverager.track(fps)
        fpsLabel.text = getString(R.string.fps_label, smoothFps)
    }

    private fun onSwitchCamera() {
        cameraController.switchCamera(textureView)
    }

    private fun onExportFrame() {
        val snap = renderer.snapshot() ?: run {
            Timber.w("No frame available to export")
            return
        }
        Thread {
            try {
                val uri = SaveUtils.saveRgbaToPng(this, snap.data, snap.width, snap.height, "FlamEdge_${System.currentTimeMillis()}.png")
                Timber.i("Saved processed frame to $uri")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save frame")
            }
        }.start()
    }

    private fun onCycleFilter() {
        renderer.nextFilter()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCameraIfReady()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        cameraController.stop()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}

