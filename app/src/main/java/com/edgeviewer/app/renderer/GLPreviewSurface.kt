package com.edgeviewer.app.renderer

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class GLPreviewSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    init {
        setEGLContextClientVersion(2)
    }
}

