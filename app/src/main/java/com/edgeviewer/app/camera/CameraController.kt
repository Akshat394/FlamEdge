package com.edgeviewer.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.edgeviewer.app.renderer.FrameListener
import com.edgeviewer.app.util.YuvConverter
import timber.log.Timber
import java.nio.ByteBuffer

class CameraController(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    private val listener: FrameListener,
    private val previewSize: Size = Size(640, 480)
) : DefaultLifecycleObserver {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var imageReader: android.media.ImageReader? = null

    private var currentCameraId: String? = null
    private var currentSize: Size = previewSize

    private var nv21Buffer: ByteBuffer =
        ByteBuffer.allocateDirect(previewSize.width * previewSize.height * 3 / 2)

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun start(textureView: TextureView) {
        if (cameraDevice != null) return

        startBackgroundThread()
        val chosenCameraId = currentCameraId ?: (selectBackCamera() ?: return)
        val characteristics = cameraManager.getCameraCharacteristics(chosenCameraId)
        val streamConfigs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return

        val chosenSize = chooseOptimalSize(
            streamConfigs.getOutputSizes(ImageFormat.YUV_420_888).toList(),
            previewSize
        )

        currentCameraId = chosenCameraId
        currentSize = chosenSize

        val requiredCapacity = chosenSize.width * chosenSize.height * 3 / 2
        if (nv21Buffer.capacity() != requiredCapacity) {
            nv21Buffer = ByteBuffer.allocateDirect(requiredCapacity)
        } else {
            nv21Buffer.clear()
        }

        imageReader = android.media.ImageReader.newInstance(
            chosenSize.width,
            chosenSize.height,
            ImageFormat.YUV_420_888,
            3
        ).apply {
            setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.use { image ->
                    val nv21 = nv21Buffer
                    nv21.position(0)
                    YuvConverter.imageToNv21(image, nv21)
                    listener.onFrameAvailable(nv21, image.width, image.height)
                }
            }, backgroundHandler)
        }

        openCamera(chosenCameraId, textureView.surfaceTexture, chosenSize)
    }

    fun stop() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
    }

    private fun chooseOptimalSize(choices: List<Size>, requested: Size): Size {
        return choices.minByOrNull { size ->
            val widthDiff = (size.width - requested.width).toDouble()
            val heightDiff = (size.height - requested.height).toDouble()
            kotlin.math.abs(widthDiff) + kotlin.math.abs(heightDiff)
        } ?: requested
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(
        cameraId: String,
        surfaceTexture: SurfaceTexture,
        size: Size
    ) {
        surfaceTexture.setDefaultBufferSize(size.width, size.height)
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device, surfaceTexture)
            }

            override fun onDisconnected(device: CameraDevice) {
                Timber.w("Camera disconnected")
                device.close()
                cameraDevice = null
            }

            override fun onError(device: CameraDevice, error: Int) {
                Timber.e("Camera error: $error")
                device.close()
                cameraDevice = null
            }
        }, backgroundHandler)
    }

    private fun createSession(device: CameraDevice, surfaceTexture: SurfaceTexture) {
        val previewSurface = Surface(surfaceTexture)
        val readerSurface = imageReader?.surface ?: return

        device.createCaptureSession(
            listOf(previewSurface, readerSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        addTarget(readerSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }

                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Timber.e("Capture session configuration failed")
                }
            },
            backgroundHandler
        )
    }

    fun switchCamera(textureView: TextureView) {
        val targetId = when (currentCameraId) {
            selectBackCamera() -> selectFrontCamera() ?: currentCameraId
            selectFrontCamera() -> selectBackCamera() ?: currentCameraId
            else -> selectFrontCamera() ?: selectBackCamera()
        }
        targetId ?: return

        // Clean up current session/device, but keep the background thread running for quicker switch
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null

        currentCameraId = targetId

        // Re-create ImageReader with the same size
        imageReader?.close()
        imageReader = android.media.ImageReader.newInstance(
            currentSize.width,
            currentSize.height,
            ImageFormat.YUV_420_888,
            3
        ).apply {
            setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.use { image ->
                    val nv21 = nv21Buffer
                    nv21.position(0)
                    YuvConverter.imageToNv21(image, nv21)
                    listener.onFrameAvailable(nv21, image.width, image.height)
                }
            }, backgroundHandler)
        }

        openCamera(targetId, textureView.surfaceTexture, currentSize)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            Timber.e(e, "Error stopping background thread")
        }
        backgroundThread = null
        backgroundHandler = null
    }

    private fun selectBackCamera(): String? {
        cameraManager.cameraIdList.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun selectFrontCamera(): String? {
        cameraManager.cameraIdList.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return null
    }
}

