package com.hpprint.app.data.scan.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    private const val A4_W = 595
    private const val A4_H = 842

    /** 导出为 A4 纵向 PDF（图片等比缩放居中） */
    fun exportPdf(pages: List<Bitmap>, out: File): Boolean {
        if (pages.isEmpty()) return false
        val doc = PdfDocument()
        try {
            writePages(doc, pages)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { doc.writeTo(it) }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            doc.close()
        }
    }

    /** 保存图片到系统相册，返回保存的张数 */
    fun saveImagesToGallery(context: Context, pages: List<Bitmap>, prefix: String = "Scan"): Int {
        if (pages.isEmpty()) return 0
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        var ok = 0
        pages.forEachIndexed { index, bmp ->
            val name = if (pages.size == 1) "${prefix}_$stamp.jpg" else "${prefix}_${stamp}_${index + 1}.jpg"
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HP Print")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: return@forEachIndexed
                    context.contentResolver.openOutputStream(uri)?.use {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    ok++
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "HP Print"
                    ).apply { mkdirs() }
                    val f = File(dir, name)
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    ok++
                }
            } catch (e: Exception) {
                // 忽略单张失败
            }
        }
        return ok
    }

    /** 导出 PDF 到系统「下载」目录，返回展示给用户的路径 */
    fun savePdfToDownloads(context: Context, pages: List<Bitmap>, name: String): String? {
        val safe = name.ifBlank { "scan" }.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$safe.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HP Print")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val doc = PdfDocument()
                    try {
                        writePages(doc, pages)
                        doc.writeTo(os)
                    } finally {
                        doc.close()
                    }
                }
                "下载/HP Print/$safe.pdf"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "HP Print"
                ).apply { mkdirs() }
                val f = File(dir, "$safe.pdf")
                if (exportPdf(pages, f)) f.absolutePath else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 把识别出的文字存成 .txt 到系统「下载」目录，返回展示给用户的路径 */
    fun saveTextToDownloads(context: Context, text: String, name: String): String? {
        val safe = name.ifBlank { "ocr" }.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        val fileName = "$safe.txt"
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HP Print")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                "下载/HP Print/$fileName"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "HP Print"
                ).apply { mkdirs() }
                val f = File(dir, fileName)
                f.writeBytes(bytes)
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 整页 A4 直接铺满输出（用于证件复印件这类已经排版好的整页图），
     * 不加白边、不缩放，保证实物 1:1。
     */
    fun savePagePdfToDownloads(context: Context, page: Bitmap, name: String): String? {
        val safe = name.ifBlank { "page" }.replace(Regex("[^\\w\\u4e00-\\u9fa5-]"), "_")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$safe.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HP Print")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val doc = PdfDocument()
                    try {
                        val p = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
                        p.canvas.drawColor(Color.WHITE)
                        p.canvas.drawBitmap(
                            page, null,
                            android.graphics.RectF(0f, 0f, A4_W.toFloat(), A4_H.toFloat()),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        )
                        doc.finishPage(p)
                        doc.writeTo(os)
                    } finally {
                        doc.close()
                    }
                }
                "下载/HP Print/$safe.pdf"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "HP Print"
                ).apply { mkdirs() }
                val f = File(dir, "$safe.pdf")
                val doc = PdfDocument()
                try {
                    val p = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
                    p.canvas.drawColor(Color.WHITE)
                    p.canvas.drawBitmap(
                        page, null,
                        android.graphics.RectF(0f, 0f, A4_W.toFloat(), A4_H.toFloat()),
                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                    )
                    doc.finishPage(p)
                    FileOutputStream(f).use { doc.writeTo(it) }
                    f.absolutePath
                } finally {
                    doc.close()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writePages(doc: PdfDocument, pages: List<Bitmap>) {        for (bmp in pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create())
            drawPage(page, bmp)
            doc.finishPage(page)
        }
    }

    private fun drawPage(page: PdfDocument.Page, bmp: Bitmap) {
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val margin = 16f
        val availW = A4_W - margin * 2
        val availH = A4_H - margin * 2
        val scale = minOf(availW / bmp.width, availH / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val left = (A4_W - dw) / 2f
        val top = (A4_H - dh) / 2f
        canvas.drawBitmap(
            bmp, null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
    }
}
