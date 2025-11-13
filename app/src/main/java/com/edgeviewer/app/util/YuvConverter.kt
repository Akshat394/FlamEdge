package com.edgeviewer.app.util

import android.graphics.ImageFormat
import android.media.Image
import java.nio.ByteBuffer

object YuvConverter {

    fun imageToNv21(image: Image, outBuffer: ByteBuffer) {
        require(image.format == ImageFormat.YUV_420_888) { "Unsupported format ${image.format}" }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()

        yBuffer.position(0)
        uBuffer.position(0)
        vBuffer.position(0)

        val ySize = yBuffer.remaining()
        val uvSize = uBuffer.remaining()
        val required = ySize + uvSize
        if (outBuffer.capacity() < required) {
            throw IllegalArgumentException("Output buffer too small: ${outBuffer.capacity()} < $required")
        }

        outBuffer.position(0)

        copyPlane(yBuffer, yPlane.rowStride, image.width, image.height, outBuffer)
        interleaveChroma(
            uBuffer,
            vBuffer,
            uPlane.rowStride,
            vPlane.rowStride,
            uPlane.pixelStride,
            vPlane.pixelStride,
            image.width,
            image.height,
            outBuffer
        )

        outBuffer.position(0)
        outBuffer.limit(required)
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
        outBuffer: ByteBuffer
    ) {
        val rowData = ByteArray(rowStride)

        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(rowData, 0, rowStride)
            outBuffer.put(rowData, 0, width)
        }
    }

    private fun interleaveChroma(
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        uRowStride: Int,
        vRowStride: Int,
        uPixelStride: Int,
        vPixelStride: Int,
        width: Int,
        height: Int,
        outBuffer: ByteBuffer
    ) {
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                outBuffer.put(vBuffer[vIndex])
                outBuffer.put(uBuffer[uIndex])
            }
        }
    }
}
