package com.edgeviewer.app.net

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal HTTP server that serves the latest processed frame as PNG at /frame.
 * Runs on a background thread and is safe to start/stop from Activity.
 */
class HttpFrameServer(private val port: Int = 8080) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    @Volatile private var latestPng: ByteArray? = null
    @Volatile private var width: Int = 0
    @Volatile private var height: Int = 0

    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        if (running.get()) return
        running.set(true)
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Timber.i("HttpFrameServer started on port $port")
                while (running.get()) {
                    val socket = try { serverSocket?.accept() } catch (e: Exception) {
                        if (running.get()) Timber.e(e, "Server accept error")
                        null
                    } ?: continue
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Timber.e(e, "HttpFrameServer failed")
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
                Timber.i("HttpFrameServer stopped")
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }

    /**
     * Update the latest frame from RGBA data; converts to PNG bytes.
     */
    fun updateRgbaFrame(rgba: ByteArray, w: Int, h: Int) {
        try {
            val bmp = Bitmap.createBitmap(w, h, Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rgba))
            val out = ByteArrayOutputStream(rgba.size)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            latestPng = out.toByteArray()
            width = w
            height = h
            out.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to encode RGBA to PNG")
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.getInputStream().use { input ->
                socket.getOutputStream().use { output ->
                    val reader = BufferedReader(InputStreamReader(input))
                    val requestLine = reader.readLine() ?: ""
                    // Consume headers until empty line
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    if (requestLine.startsWith("GET /frame")) {
                        serveFrame(output)
                    } else {
                        serveIndex(output)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling client")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveFrame(output: OutputStream) {
        val png = latestPng
        if (png == null || png.isEmpty()) {
            val body = "No frame available".toByteArray()
            writeResponse(output, 503, "text/plain", body)
            return
        }
        writeResponse(output, 200, "image/png", png)
    }

    private fun serveIndex(output: OutputStream) {
        val body = """
            <html><body>
            <h3>FlamEdge Frame Server</h3>
            <p>Latest frame: <a href='/frame'>/frame</a></p>
            </body></html>
        """.trimIndent().toByteArray()
        writeResponse(output, 200, "text/html; charset=utf-8", body)
    }

    private fun writeResponse(output: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val status = when (code) {
            200 -> "200 OK"
            503 -> "503 Service Unavailable"
            else -> "$code OK"
        }
        val headers = """
            HTTP/1.1 $status

            Content-Type: $contentType

            Content-Length: ${body.size}

            Access-Control-Allow-Origin: *
            Cache-Control: no-cache, no-store, must-revalidate
            Pragma: no-cache
            Expires: 0

            Connection: close

            

        """.trimIndent().replace("\n", "\r\n")
        output.write(headers.toByteArray())
        output.write(body)
        output.flush()
    }
}