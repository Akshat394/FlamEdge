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
        synchronized(this) {
            try {
                if (cameraDevice != null) {
                    Timber.d("Camera already started")
                    return
                }
                
                if (backgroundThread == null || !backgroundThread!!.isAlive) {
                    startBackgroundThread()
                }

                val chosenCameraId = currentCameraId ?: (selectBackCamera() ?: run {
                    Timber.e("No camera available")
                    throw IllegalStateException("No camera available")
                })
                
                val characteristics = try {
                    cameraManager.getCameraCharacteristics(chosenCameraId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get camera characteristics")
                    throw IllegalStateException("Failed to get camera characteristics: ${e.message}", e)
                }
                
                val streamConfigs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: run {
                        Timber.e("No stream configuration map available")
                        throw IllegalStateException("No stream configuration map available")
                    }

                val outputSizes = streamConfigs.getOutputSizes(ImageFormat.YUV_420_888)
                if (outputSizes == null || outputSizes.isEmpty()) {
                    Timber.e("No output sizes available")
                    throw IllegalStateException("No supported output sizes available")
                }

                val chosenSize = chooseOptimalSize(
                    outputSizes.toList(),
                    previewSize
                )

                currentCameraId = chosenCameraId
                currentSize = chosenSize

                val requiredCapacity = chosenSize.width * chosenSize.height * 3 / 2
                if (requiredCapacity <= 0) {
                    Timber.e("Invalid buffer capacity: $requiredCapacity")
                    throw IllegalStateException("Invalid buffer capacity")
                }
                
                if (nv21Buffer.capacity() != requiredCapacity) {
                    nv21Buffer = ByteBuffer.allocateDirect(requiredCapacity)
                } else {
                    nv21Buffer.clear()
                }

                imageReader?.close()
                imageReader = android.media.ImageReader.newInstance(
                    chosenSize.width,
                    chosenSize.height,
                    ImageFormat.YUV_420_888,
                    3
                ).apply {
                    setOnImageAvailableListener({ reader ->
                        try {
                            reader.acquireLatestImage()?.use { image ->
                                val nv21 = nv21Buffer
                                nv21.position(0)
                                YuvConverter.imageToNv21(image, nv21)
                                listener.onFrameAvailable(nv21, image.width, image.height)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing camera frame")
                        }
                    }, backgroundHandler)
                }

                val surfaceTexture = textureView.surfaceTexture
                if (surfaceTexture != null) {
                    openCamera(chosenCameraId, surfaceTexture, chosenSize)
                } else {
                    Timber.e("Surface texture is not available")
                    throw IllegalStateException("Surface texture not available")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting camera")
                try {
                    stop()
                } catch (stopError: Exception) {
                    Timber.e(stopError, "Error stopping camera after start failure")
                }
                throw e
            }
        }
    }

    fun stop() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            stopBackgroundThread()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping camera")
        }
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
        try {
            surfaceTexture.setDefaultBufferSize(size.width, size.height)
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    try {
                        cameraDevice = device
                        createSession(device, surfaceTexture)
                    } catch (e: Exception) {
                        Timber.e(e, "Error in onOpened")
                        device.close()
                        cameraDevice = null
                    }
                }

                override fun onDisconnected(device: CameraDevice) {
                    Timber.w("Camera disconnected")
                    try {
                        device.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing disconnected camera")
                    }
                    cameraDevice = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    Timber.e("Camera error: $error")
                    try {
                        device.close()
                    } catch (e: Exception) {
                        Timber.e(e, "Error closing camera after error")
                    }
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Timber.e(e, "Error opening camera")
            cameraDevice = null
        }
    }

    private fun createSession(device: CameraDevice, surfaceTexture: SurfaceTexture) {
        try {
            val previewSurface = Surface(surfaceTexture)
            val readerSurface = imageReader?.surface ?: run {
                Timber.e("ImageReader surface is null")
                return
            }

            device.createCaptureSession(
                listOf(previewSurface, readerSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            captureSession = session
                            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                addTarget(readerSurface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            }

                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        } catch (e: Exception) {
                            Timber.e(e, "Error in onConfigured")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Timber.e("Capture session configuration failed")
                        try {
                            session.close()
                        } catch (e: Exception) {
                            Timber.e(e, "Error closing failed session")
                        }
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Timber.e(e, "Error creating capture session")
        }
    }

    fun switchCamera(textureView: TextureView) {
        try {
            // Determine the target camera ID based on current facing
            val targetId = when (currentCameraId) {
                selectBackCamera() -> selectFrontCamera() ?: currentCameraId
                selectFrontCamera() -> selectBackCamera() ?: currentCameraId
                else -> selectFrontCamera() ?: selectBackCamera()
            }
            targetId ?: return

            // Fetch characteristics and supported sizes for the target camera
            val characteristics = try {
                cameraManager.getCameraCharacteristics(targetId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get characteristics for target camera")
                return
            }

            val streamConfigs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: run {
                    Timber.e("No stream configuration map available for target camera")
                    return
                }

            val outputSizes = streamConfigs.getOutputSizes(ImageFormat.YUV_420_888)
            if (outputSizes == null || outputSizes.isEmpty()) {
                Timber.e("No supported YUV_420_888 sizes for target camera")
                return
            }

            // Choose the best matching size for the requested preview
            val chosenSize = chooseOptimalSize(outputSizes.toList(), previewSize)

            // Clean up current session/device, but keep the background thread running for quicker switch
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null

            // Update current camera and size
            currentCameraId = targetId
            currentSize = chosenSize

            // Ensure NV21 buffer has the correct capacity for the new size
            val requiredCapacity = chosenSize.width * chosenSize.height * 3 / 2
            if (requiredCapacity <= 0) {
                Timber.e("Invalid buffer capacity during switch: $requiredCapacity")
                return
            }
            if (nv21Buffer.capacity() != requiredCapacity) {
                nv21Buffer = ByteBuffer.allocateDirect(requiredCapacity)
            } else {
                nv21Buffer.clear()
            }

            // Recreate ImageReader with the new size
            imageReader?.close()
            imageReader = android.media.ImageReader.newInstance(
                chosenSize.width,
                chosenSize.height,
                ImageFormat.YUV_420_888,
                3
            ).apply {
                setOnImageAvailableListener({ reader ->
                    try {
                        reader.acquireLatestImage()?.use { image ->
                            val nv21 = nv21Buffer
                            nv21.position(0)
                            YuvConverter.imageToNv21(image, nv21)
                            listener.onFrameAvailable(nv21, image.width, image.height)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing camera frame after switch")
                    }
                }, backgroundHandler)
            }

            val surfaceTexture = textureView.surfaceTexture
            if (surfaceTexture != null) {
                // Ensure the preview buffer size matches the chosen camera output
                openCamera(targetId, surfaceTexture, chosenSize)
            } else {
                Timber.e("Surface texture is null when switching camera")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error switching camera")
            // Try to restore a clean state on failure
            try {
                stop()
            } catch (e2: Exception) {
                Timber.e(e2, "Error during cleanup after switch failure")
            }
        }
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

