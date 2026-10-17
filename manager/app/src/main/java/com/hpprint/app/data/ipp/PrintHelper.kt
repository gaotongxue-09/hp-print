package com.hpprint.app.data.ipp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * 打印配置（对应 IPP Job Template 属性，与参考项目 HpPrintScanner 保持一致）。
 * @param color       true=彩色 false=黑白
 * @param quality     3=草稿 4=标准 5=高质量
 * @param copies      份数
 * @param media       iso_a4 / na_letter 等
 * @param orientation 3=纵向 4=横向
 * @param sides       one-sided / two-sided-long-edge / two-sided-short-edge
 */
data class PrintConfig(
    val color: Boolean = true,
    val quality: Int = 4,
    val copies: Int = 1,
    val media: String = "iso_a4",
    val orientation: Int = 3,
    val sides: String = "one-sided",
)

/**
 * 文档生成工具：图片转 PDF / JPEG 字节、灰度转换，供自研 IPP 客户端发送。
 * 与参考项目 HpPrintScanner 的 PrintHelper 一致，使用显式纸张尺寸（点）生成 PDF，
 * 不依赖 Android 打印框架的 PrintedPdfDocument，兼容性更高。
 */
object PrintHelper {

    // 纸张尺寸（点）
    private val A4 = 595 to 842
    private val LETTER = 612 to 792

    /** 把图片转为灰度（黑白打印时在文档层面去色，即使打印机忽略色彩属性也能输出黑白）。 */
    fun toGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** 生成单页 PDF，按配置选择纸张与方向，图片等比居中。 */
    fun bitmapToPdfBytes(bitmap: Bitmap, config: PrintConfig = PrintConfig()): ByteArray {
        val base = if (config.media == "na_letter") LETTER else A4
        val portrait = config.orientation == 3
        val pageW = if (portrait) base.first else base.second
        val pageH = if (portrait) base.second else base.first

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(pageInfo)
        drawCentered(page.canvas, bitmap, pageW, pageH)
        doc.finishPage(page)

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** 压缩为 JPEG 字节。 */
    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun drawCentered(
        canvas: Canvas, bitmap: Bitmap, pageW: Int, pageH: Int,
    ) {
        val margin = 30
        val maxW = pageW - margin * 2
        val maxH = pageH - margin * 2
        val scale = minOf(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = (pageW - drawW) / 2
        val top = (pageH - drawH) / 2
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), null)
    }
}