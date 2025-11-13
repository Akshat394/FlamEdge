package com.edgeviewer.app

import java.nio.ByteBuffer

object NativeBridge {

    init {
        load()
    }

    fun load() {
        runCatching {
            System.loadLibrary("edge_native")
        }.onFailure {
            throw UnsatisfiedLinkError("Failed to load edge_native: ${it.message}")
        }
    }

    external fun processFrame(
        nv21Buffer: ByteBuffer,
        width: Int,
        height: Int,
        outBuffer: ByteBuffer
    ): Boolean
}

