package com.edgeviewer.app.renderer

import java.nio.ByteBuffer

interface FrameListener {
    fun onFrameAvailable(data: ByteBuffer, width: Int, height: Int)
}

