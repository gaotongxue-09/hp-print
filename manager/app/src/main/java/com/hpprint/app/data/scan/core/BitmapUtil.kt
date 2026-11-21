package com.hpprint.app.data.scan.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object BitmapUtil {

    fun decodeFile(path: String, maxLong: Int = 2600): Bitmap? {
        val f = File(path)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        var long = max(bounds.outWidth, bounds.outHeight)
        while (long / sample > maxLong * 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        long = max(bmp.width, bmp.height)
        if (long > maxLong) {
            val k = maxLong.toFloat() / long
            val nb = Bitmap.createScaledBitmap(
                bmp, (bmp.width * k).toInt().coerceAtLeast(1),
                (bmp.height * k).toInt().coerceAtLeast(1), true
            )
            if (nb !== bmp) bmp.recycle()
            bmp = nb
        }
        return bmp
    }

    fun decodeUri(context: Context, uri: Uri, maxLong: Int = 2600): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / sample > maxLong * 2) sample *= 2

        val real = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, real)
        } ?: return null

        val degrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }

        if (degrees != 0) {
            val nb = rotate(bmp, degrees.toFloat())
            if (nb !== bmp) bmp.recycle()
            bmp = nb
        }

        val long = max(bmp.width, bmp.height)
        if (long > maxLong) {
            val k = maxLong.toFloat() / long
            val nb = Bitmap.createScaledBitmap(
                bmp, (bmp.width * k).toInt().coerceAtLeast(1),
                (bmp.height * k).toInt().coerceAtLeast(1), true
            )
            if (nb !== bmp) bmp.recycle()
            bmp = nb
        }
        return bmp
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src
        val m = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun copyToFile(bitmap: Bitmap, dir: File, name: String): File? {
        val f = File(dir, name)
        return if (saveJpeg(bitmap, f)) f else null
    }
}
