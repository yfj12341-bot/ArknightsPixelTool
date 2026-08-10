package com.pixelpainter.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.pixelpainter.core.PixelArtResult
import java.io.File
import java.io.FileOutputStream

private fun exportsDir(context: Context): File =
    File(context.cacheDir, "exports").apply { mkdirs() }

private fun Context.shareFile(file: File, mimeType: String, title: String) {
    val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, title))
}

fun sharePixelPng(context: Context, result: PixelArtResult) {
    val bitmap = Bitmap.createBitmap(
        result.gridSize,
        result.gridSize,
        Bitmap.Config.ARGB_8888
    )
    bitmap.setPixels(
        result.preview.pixels,
        0,
        result.gridSize,
        0,
        0,
        result.gridSize,
        result.gridSize
    )
    val upscaled = Bitmap.createScaledBitmap(bitmap, 480, 480, false)
    val file = File(exportsDir(context), "pixel_art_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use {
        upscaled.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    bitmap.recycle()
    upscaled.recycle()
    context.shareFile(file, "image/png", "分享像素画")
}
