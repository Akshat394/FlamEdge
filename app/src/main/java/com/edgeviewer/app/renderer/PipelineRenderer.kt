package com.edgeviewer.app.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.edgeviewer.app.NativeBridge
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

class PipelineRenderer(
    private val surfaceView: GLSurfaceView,
    private val fpsUpdate: (Double) -> Unit
) : GLSurfaceView.Renderer, FrameListener {

    enum class FilterMode(val code: Int) { NONE(0), INVERT(1), THRESHOLD(2) }

    private val showProcessed = AtomicBoolean(true)
    private val framePending = AtomicBoolean(false)
    @Volatile private var filterMode: FilterMode = FilterMode.NONE

    private val vertexCoords = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    private val textureCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(vertexCoords)
                position(0)
            }

    private val texBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(textureCoords)
                position(0)
            }

    private var programHandle = 0
    private var textureHandle = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureUniform = 0
    private var modeUniform = 0

    private var latestFrame: ByteBuffer? = null
    private var frameWidth: Int = 0
    private var frameHeight: Int = 0
    private val rgbaBufferLock = Any()
    private var rgbaBuffer: ByteBuffer? = null
    private var textureInitialized = false

    private var lastTimestamp: Long = 0

    fun setShowProcessed(enabled: Boolean) {
        showProcessed.set(enabled)
    }

    data class RgbaFrame(val data: ByteArray, val width: Int, val height: Int)

    private var frameConsumer: ((ByteArray, Int, Int) -> Unit)? = null

    fun setFrameConsumer(consumer: ((ByteArray, Int, Int) -> Unit)?) {
        frameConsumer = consumer
    }

    fun snapshot(): RgbaFrame? {
        val (w, h, buf) = synchronized(rgbaBufferLock) {
            Triple(frameWidth, frameHeight, rgbaBuffer?.duplicate())
        }
        if (w <= 0 || h <= 0 || buf == null) return null
        val arr = ByteArray(w * h * 4)
        buf.position(0)
        buf.get(arr, 0, arr.size)
        return RgbaFrame(arr, w, h)
    }

    fun nextFilter() {
        filterMode = when (filterMode) {
            FilterMode.NONE -> FilterMode.INVERT
            FilterMode.INVERT -> FilterMode.THRESHOLD
            FilterMode.THRESHOLD -> FilterMode.NONE
        }
        surfaceView.requestRender()
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        programHandle = ShaderHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureHandle = ShaderHelper.createTexture()

        positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
        textureUniform = GLES20.glGetUniformLocation(programHandle, "uTexture")
        modeUniform = GLES20.glGetUniformLocation(programHandle, "uMode")

        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val buffer = synchronized(rgbaBufferLock) { rgbaBuffer?.duplicate() } ?: return

        GLES20.glUseProgram(programHandle)

        if (!textureInitialized) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                frameWidth,
                frameHeight,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                buffer
            )
            textureInitialized = true
        }

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glUniform1i(textureUniform, 0)
        GLES20.glUniform1i(modeUniform, filterMode.code)

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        buffer.position(0)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            0,
            0,
            frameWidth,
            frameHeight,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Provide the current RGBA frame to consumer (e.g., HTTP server)
        frameConsumer?.let { consumer ->
            val w = frameWidth
            val h = frameHeight
            if (w > 0 && h > 0) {
                val src = synchronized(rgbaBufferLock) { rgbaBuffer?.duplicate() }
                if (src != null) {
                    try {
                        val arr = ByteArray(w * h * 4)
                        src.position(0)
                        src.get(arr)
                        consumer(arr, w, h)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to provide RGBA frame to consumer")
                    }
                }
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onFrameAvailable(data: ByteBuffer, width: Int, height: Int) {
        try {
            if (!showProcessed.get()) {
                return
            }

            if (!framePending.compareAndSet(false, true)) return

            if (width <= 0 || height <= 0) {
                Timber.w("Invalid frame dimensions: ${width}x${height}")
                framePending.set(false)
                return
            }

            val rgbaCapacity = width * height * 4
            if (rgbaCapacity <= 0) {
                Timber.w("Invalid buffer capacity: $rgbaCapacity")
                framePending.set(false)
                return
            }

            var localBuffer: ByteBuffer?

            synchronized(rgbaBufferLock) {
                if (rgbaBuffer == null || rgbaBuffer!!.capacity() != rgbaCapacity) {
                    rgbaBuffer = ByteBuffer.allocateDirect(rgbaCapacity)
                }
                if (frameWidth != width || frameHeight != height) {
                    frameWidth = width
                    frameHeight = height
                    textureInitialized = false
                }
                localBuffer = rgbaBuffer
            }

            // If native library isn't loaded, skip processing gracefully
            if (!NativeBridge.isLoaded()) {
                Timber.w("Native library not loaded; skipping frame processing")
                framePending.set(false)
                return
            }

            val duration = measureTimeMillis {
                localBuffer?.let { out ->
                    try {
                        out.position(0)
                        val success = NativeBridge.processFrame(data, width, height, out)
                        if (!success) {
                            Timber.w("Native processing failed")
                            framePending.set(false)
                            return
                        }
                        out.position(0)
                        synchronized(rgbaBufferLock) {
                            rgbaBuffer = out
                        }
                    } catch (e: UnsatisfiedLinkError) {
                        Timber.e(e, "Native library missing during processFrame")
                        framePending.set(false)
                        return
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing frame")
                        framePending.set(false)
                        return
                    }
                } ?: run {
                    Timber.w("Local buffer is null")
                    framePending.set(false)
                    return
                }
            }

            val now = System.nanoTime()
            if (lastTimestamp != 0L) {
                val timeDiff = now - lastTimestamp
                if (timeDiff > 0) {
                    val fps = 1e9 / timeDiff.toDouble()
                    fpsUpdate(fps.coerceIn(0.0, 120.0)) // Cap FPS at reasonable value
                }
            }
            lastTimestamp = now

            surfaceView.requestRender()
            framePending.set(false)
        } catch (e: Exception) {
            Timber.e(e, "Critical error in onFrameAvailable")
            framePending.set(false)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform int uMode;
            void main() {
                vec4 c = texture2D(uTexture, vTexCoord);
                if (uMode == 1) {
                    gl_FragColor = vec4(1.0 - c.rgb, 1.0);
                } else if (uMode == 2) {
                    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));
                    float t = g > 0.5 ? 1.0 : 0.0;
                    gl_FragColor = vec4(vec3(t), 1.0);
                } else {
                    gl_FragColor = c;
                }
            }
        """
    }
}

