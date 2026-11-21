package com.hpprint.app.data.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.hpprint.app.data.ipp.PrintHelper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 证件照 / 身份证复印排版生成器：把照片按物理尺寸排布到 A4（300 DPI）页面上，
 * 生成整页位图后再走现有 IMAGE 打印管线。所有尺寸按毫米换算（1 英寸 = 25.4 mm）。
 */
object LayoutGen {

    private const val DPI = 300f
    private val MM_PX = DPI / 25.4f
    private val A4W = (210f * MM_PX).roundToInt()
    private val A4H = (297f * MM_PX).roundToInt()
    private const val MARGIN_MM = 10f
    private const val GAP_MM = 4f
    private const val PHOTO_MARGIN_MM = 3f
    private const val PHOTO_GAP_MM = 2f

    fun pageWidth() = A4W
    fun pageHeight() = A4H

    /**
     * 证件照排版：把照片按 [wMm]×[hMm] 物理尺寸在 [paperWmm]×[paperHmm] 纸张上 N-up 网格排布，
     * [bg] 为每格底色（null 表示不换底）。网格整体居中，彩色底色时绘制细边框。
     */
    fun idPhotoGrid(photo: Bitmap, wMm: Float, hMm: Float, bg: Int?, paperWmm: Float, paperHmm: Float): Bitmap {
        val cw = (wMm * MM_PX).roundToInt().coerceAtLeast(1)
        val ch = (hMm * MM_PX).roundToInt().coerceAtLeast(1)
        val pw = (paperWmm * MM_PX).roundToInt().coerceAtLeast(cw)
        val ph = (paperHmm * MM_PX).roundToInt().coerceAtLeast(ch)
        val margin = (PHOTO_MARGIN_MM * MM_PX).roundToInt()
        val gap = (PHOTO_GAP_MM * MM_PX).roundToInt()

        val cols = ((pw - 2 * margin + gap) / (cw + gap)).coerceAtLeast(1)
        val rows = ((ph - 2 * margin + gap) / (ch + gap)).coerceAtLeast(1)

        val page = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val cover = PrintHelper.cropCover(photo, cw, ch)
        // 仅彩色底色绘制细边框；白底/无底时照片整格铺满，避免被缩放。
        val frame = if (bg != null && bg != Color.WHITE) {
            (1f * MM_PX).roundToInt().coerceAtMost(minOf(cw, ch) / 4)
        } else 0

        val gridW = cols * cw + (cols - 1) * gap
        val gridH = rows * ch + (rows - 1) * gap
        val startX = ((pw - gridW) / 2).coerceAtLeast(margin)
        val startY = ((ph - gridH) / 2).coerceAtLeast(margin)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        cellPaint.color = bg ?: Color.WHITE
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = startX + c * (cw + gap)
                val y = startY + r * (ch + gap)
                canvas.drawRect(RectF(x.toFloat(), y.toFloat(), (x + cw).toFloat(), (y + ch).toFloat()), cellPaint)
                canvas.drawBitmap(
                    cover,
                    null,
                    RectF((x + frame).toFloat(), (y + frame).toFloat(), (x + cw - frame).toFloat(), (y + ch - frame).toFloat()),
                    bmpPaint,
                )
            }
        }
        if (cover !== photo) cover.recycle()
        return page
    }

    /** 身份证正反面复印：正面在左、反面在右，各 85.6×54 mm 1:1 排到 A4 中央。 */
    fun idCardSheet(front: Bitmap, back: Bitmap): Bitmap {
        val w = (85.6f * MM_PX).roundToInt()
        val h = (54f * MM_PX).roundToInt()
        val margin = (MARGIN_MM * MM_PX).roundToInt()
        val gap = (GAP_MM * MM_PX).roundToInt()

        val page = Bitmap.createBitmap(A4W, A4H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val totalW = w * 2 + gap
        val startX = ((A4W - totalW) / 2).coerceAtLeast(margin)
        val startY = ((A4H - h) / 2).coerceAtLeast(margin)

        val frontCover = PrintHelper.cropCover(front, w, h)
        val backCover = PrintHelper.cropCover(back, w, h)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(frontCover, startX.toFloat(), startY.toFloat(), paint)
        canvas.drawBitmap(backCover, (startX + w + gap).toFloat(), startY.toFloat(), paint)
        if (frontCover !== front) frontCover.recycle()
        if (backCover !== back) backCover.recycle()
        return page
    }

    /**
     * 身份证正反面复印（国内惯例竖排）：正面在上、反面在下，各 85.6×54 mm 1:1
     * 等比缩放置于 A4 中央（不拉伸、不变形，与普通身份证复印件版式一致）。
     * [fitCenterBitmap] 保证比例正确、完整保留整张卡，不裁切不拉伸。
     */
    fun idCardSheetVertical(front: Bitmap, back: Bitmap): Bitmap {
        val w = (85.6f * MM_PX).roundToInt()
        val h = (54f * MM_PX).roundToInt()
        val margin = (MARGIN_MM * MM_PX).roundToInt()
        val gap = (GAP_MM * MM_PX).roundToInt()

        val page = Bitmap.createBitmap(A4W, A4H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val totalH = h * 2 + gap
        val startX = ((A4W - w) / 2).coerceAtLeast(margin)
        val startY = ((A4H - totalH) / 2).coerceAtLeast(margin)

        val frontCover = fitCenterBitmap(front, w, h)
        val backCover = fitCenterBitmap(back, w, h)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(frontCover, startX.toFloat(), startY.toFloat(), paint)
        canvas.drawBitmap(backCover, startX.toFloat(), (startY + h + gap).toFloat(), paint)
        if (frontCover !== front) frontCover.recycle()
        if (backCover !== back) backCover.recycle()
        return page
    }

    /**
     * 等比缩放到目标画布并居中（留白、不裁切、不拉伸），返回一张目标尺寸的新位图。
     * 身份证复印件需完整保留整张卡，故用 fitCenter 而非 cropCover（后者会裁切并拉伸）。
     */
    private fun fitCenterBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val scale = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val drawW = (src.width * scale).roundToInt().coerceIn(1, targetW)
        val drawH = (src.height * scale).roundToInt().coerceIn(1, targetH)
        val left = ((targetW - drawW) / 2).toFloat()
        val top = ((targetH - drawH) / 2).toFloat()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, RectF(left, top, left + drawW, top + drawH), paint)
        return out
    }

    /** 把生成的位图保存为缓存 PNG，返回绝对路径（供 IMAGE 打印管线直接读取）。 */
    fun savePng(context: Context, bitmap: Bitmap, name: String): String? {
        val f = File(context.cacheDir, "layout_${name}_${System.currentTimeMillis()}.png")
        return runCatching {
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f.absolutePath
        }.getOrNull()
    }

    // 证件照底色
    val BG_WHITE = Color.WHITE
    val BG_BLUE = Color.rgb(67, 142, 219)
    val BG_RED = Color.rgb(211, 47, 47)
}