package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * 身份证复印件排版：把两面按真实尺寸（85.6 × 54 mm）摆到 A4 上。
 *
 * 排版尺寸按实物 1:1 走，打印出来可以直接当复印件用：
 *  - 整块内容居中放在纸张正中央（左右、上下都居中）
 *  - 上下排列（默认，和常见复印件一致）或左右并排
 */
object IdCardComposer {

    const val LAYOUT_VERTICAL = 0
    const val LAYOUT_HORIZONTAL = 1

    const val CARD_W_MM = 85.6f
    const val CARD_H_MM = 54f
    const val A4_W_MM = 210f
    const val A4_H_MM = 297f

    /** 身份证标准比例 1.585，手动裁切时按它锁比例 */
    const val CARD_ASPECT = CARD_W_MM / CARD_H_MM

    /** 矫正后的卡片宽度（约 415 dpi），足够 300 dpi 打印 */
    const val CARD_PX_W = 1400

    fun cardPxH(pxW: Int = CARD_PX_W): Int = (pxW / CARD_ASPECT).roundToInt()

    private const val GAP_MM = 10f

    /** 预览用低分辨率，保存/导出用 300 dpi */
    const val DPI_PREVIEW = 110
    const val DPI_PRINT = 300

    fun mmToPx(mm: Float, dpi: Int): Int = (mm / 25.4f * dpi).roundToInt()

    fun cardWidth(dpi: Int) = mmToPx(CARD_W_MM, dpi)
    fun cardHeight(dpi: Int) = mmToPx(CARD_H_MM, dpi)
    fun pageWidth(dpi: Int) = mmToPx(A4_W_MM, dpi)
    fun pageHeight(dpi: Int) = mmToPx(A4_H_MM, dpi)

    /**
     * 合成一页 A4。front/back 可以任意一个为 null（只拍了一面时也能出图）。
     * 返回的新位图由调用方负责 recycle。
     */
    fun compose(
        front: Bitmap?,
        back: Bitmap?,
        layout: Int,
        dpi: Int = DPI_PRINT,
        border: Boolean = true,
        cardWmm: Float = CARD_W_MM,
        cardHmm: Float = CARD_H_MM,
        cornerMm: Float = 0f,
    ): Bitmap {
        val pageW = pageWidth(dpi)
        val pageH = pageHeight(dpi)
        val cw = mmToPx(cardWmm, dpi).coerceAtMost(pageW)
        val ch = mmToPx(cardHmm, dpi).coerceAtMost(pageH)
        val gap = mmToPx(GAP_MM, dpi)

        val out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (dpi / 120f).coerceAtLeast(1f)
            color = 0xFFD8D8D8.toInt()
        }

        fun drawCard(src: Bitmap?, left: Int, topY: Int) {
            if (src == null || src.isRecycled) return
            val dst = Rect(left, topY, left + cw, topY + ch)
            if (cornerMm > 0f) {
                val radius = mmToPx(cornerMm, dpi).toFloat()
                val clip = Path().apply {
                    addRoundRect(RectF(dst), radius, radius, Path.Direction.CW)
                }
                val save = canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(src, null, dst, paint)
                canvas.restoreToCount(save)
            } else {
                canvas.drawBitmap(src, null, dst, paint)
            }
            if (border) {
                canvas.drawRect(
                    dst.left.toFloat(), dst.top.toFloat(),
                    dst.right.toFloat(), dst.bottom.toFloat(), borderPaint
                )
            }
        }

        val hasFront = front != null && !front.isRecycled
        val hasBack = back != null && !back.isRecycled
        val both = hasFront && hasBack

        // 整块内容整体居中：先算块尺寸，再算左上角
        val horizontal = layout == LAYOUT_HORIZONTAL
        val blockW = if (horizontal) {
            if (both) cw * 2 + gap else cw
        } else {
            cw
        }
        val blockH = if (horizontal) {
            ch
        } else {
            if (both) ch * 2 + gap else ch
        }
        val left = (pageW - blockW) / 2
        val top = (pageH - blockH) / 2

        if (horizontal) {
            if (both) {
                drawCard(front, left, top)
                drawCard(back, left + cw + gap, top)
            } else {
                // 只有一面时同样摆在正中
                if (hasFront) drawCard(front, left, top) else drawCard(back, left, top)
            }
        } else {
            if (both) {
                drawCard(front, left, top)
                drawCard(back, left, top + ch + gap)
            } else {
                if (hasFront) drawCard(front, left, top) else drawCard(back, left, top)
            }
        }
        return out
    }
}
