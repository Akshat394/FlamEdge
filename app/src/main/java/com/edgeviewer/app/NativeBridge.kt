package com.edgeviewer.app

import timber.log.Timber
import java.nio.ByteBuffer

object NativeBridge {

    @Volatile
    private var loaded: Boolean = false

    /**
     * Attempts to load the native library. Returns true if successful, false otherwise.
     * No exceptions are thrown from here to avoid crashing on devices without the .so.
     */
    fun load(): Boolean {
        val result = runCatching {
            System.loadLibrary("edge_native")
        }
        loaded = result.isSuccess
        if (!loaded) {
            Timber.e(result.exceptionOrNull(), "Failed to load edge_native")
        } else {
            Timber.d("edge_native loaded")
        }
        return loaded
    }

    fun isLoaded(): Boolean = loaded

    external fun processFrame(
        nv21Buffer: ByteBuffer,
        width: Int,
        height: Int,
        outBuffer: ByteBuffer
    ): Boolean
}

