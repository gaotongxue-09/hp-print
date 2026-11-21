package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 扫描输出格式：仅保留 JPEG 与 PDF。
 * 负责把设备返回的原始字节转换成目标格式（含 PDF 首页转 JPEG）。
 */
object ScanOutputFormat {
    const val JPEG = "image/jpeg"
    const val PDF = "application/pdf"

    private const val A4_W = 595
    private const val A4_H = 842
    private const val MARGIN = 16f

    fun isPdf(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        return bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
    }

    fun encodeJpeg(bitmap: Bitmap, quality: Int = 92): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** 原始字节 → JPEG；若源为 PDF，则取首页渲染后编码。 */
    fun toJpeg(bytes: ByteArray, quality: Int = 92): ByteArray? {
        if (isPdf(bytes)) {
            val page = pdfFirstPage(bytes) ?: return null
            return try {
                encodeJpeg(page, quality)
            } finally {
                page.recycle()
            }
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            encodeJpeg(bmp, quality)
        } finally {
            bmp.recycle()
        }
    }

    /** 原始字节 → A4 PDF；若源已是 PDF 则原样返回。 */
    fun toPdf(bytes: ByteArray): ByteArray? {
        if (isPdf(bytes)) return bytes
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            bitmapToPdf(bmp)
        } finally {
            bmp.recycle()
        }
    }

    /** 单页位图 → A4 纵向 PDF 字节（等比缩放居中）。 */
    fun bitmapToPdf(bitmap: Bitmap): ByteArray? {
        val doc = PdfDocument()
        return try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val availW = A4_W - MARGIN * 2
            val availH = A4_H - MARGIN * 2
            val scale = minOf(availW / bitmap.width, availH / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val left = (A4_W - dw) / 2f
            val top = (A4_H - dh) / 2f
            canvas.drawBitmap(
                bitmap, null,
                RectF(left, top, left + dw, top + dh),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            doc.finishPage(page)
            ByteArrayOutputStream().use { out ->
                doc.writeTo(out)
                out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        } finally {
            doc.close()
        }
    }

    /** 渲染 PDF 首页为位图。 */
    fun pdfFirstPage(bytes: ByteArray): Bitmap? = runCatching {
        val temp = File.createTempFile("scan_convert", ".pdf")
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching null
                    val page = renderer.openPage(0)
                    val scale = (1600f / page.width.coerceAtLeast(1)).coerceAtMost(1f)
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
            }
        } finally {
            runCatching { temp.delete() }
        }
    }.getOrNull()
}
