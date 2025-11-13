package com.edgeviewer.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream

object SaveUtils {

    fun saveRgbaToPng(
        context: Context,
        rgba: ByteArray,
        width: Int,
        height: Int,
        displayName: String
    ): Uri {
        val bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        // ByteArray in RGBA → setPixels expects ARGB; we can copy via buffer
        // Convert RGBA to ARGB by swapping R and A positions would be expensive;
        // use copyPixelsFromBuffer which accepts RGBA as-is on Android (interprets as RGBA)
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(rgba))

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.WIDTH, width)
            put(MediaStore.Images.Media.HEIGHT, height)
        }

        val uri = resolver.insert(collection, values) ?: error("Failed to create MediaStore entry")
        resolver.openOutputStream(uri).use { out: OutputStream? ->
            requireNotNull(out) { "Failed to open output stream" }
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("Bitmap compress failed")
            }
            out.flush()
        }
        return uri
    }
}


