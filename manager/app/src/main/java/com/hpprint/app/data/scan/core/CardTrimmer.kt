package com.hpprint.app.data.scan.core

import android.graphics.Bitmap

/**
 * 矫正后残留的桌面暗边检测。
 *
 * 只负责「量」：某条边的行/列里暗像素占比超过阈值，就说明这条边还压在背景上，
 * 返回需要往里收的比例（0..0.08）。真正的收缩交给调用方按比例内缩源图上的四边形再做一次矫正
 * —— 这样不会像「裁完再拉伸」那样把卡片内容拉变形，也不会因为四条边裁得不均而看着更歪。
 */
object CardTrimmer {

    private const val MAX_TRIM_FRAC = 0.08f
    private const val DARK_LINE_RATIO = 0.10f

    data class Insets(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val any: Boolean get() = left > 0f || top > 0f || right > 0f || bottom > 0f
    }

    fun measure(src: Bitmap): Insets {
        val w = src.width
        val h = src.height
        if (w < 48 || h < 48) return Insets(0f, 0f, 0f, 0f)

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        fun lum(p: Int): Int =
            (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 151 + (p and 0xFF) * 28) shr 8

        // 卡片内部亮度基准（取中间 50% 区域）
        val x0 = w / 4
        val x1 = w - x0
        val y0 = h / 4
        val y1 = h - y0
        var sum = 0L
        var n = 0
        for (y in y0 until y1) {
            val row = y * w
            for (x in x0 until x1) {
                sum += lum(px[row + x])
                n++
            }
        }
        if (n == 0) return Insets(0f, 0f, 0f, 0f)
        val interior = sum.toFloat() / n
        val brightLine = interior * 0.80f

        // 用「暗像素占比」而不是平均值，才能处理斜着进来的楔形阴影（只盖住半条边）
        fun rowDarkRatio(y: Int): Float {
            val row = y * w
            var dark = 0
            for (x in 0 until w) if (lum(px[row + x]) < brightLine) dark++
            return dark.toFloat() / w
        }

        fun colDarkRatio(x: Int): Float {
            var dark = 0
            for (y in 0 until h) if (lum(px[y * w + x]) < brightLine) dark++
            return dark.toFloat() / h
        }

        val maxTop = (h * MAX_TRIM_FRAC).toInt().coerceAtLeast(1)
        val maxLeft = (w * MAX_TRIM_FRAC).toInt().coerceAtLeast(1)

        var top = 0
        while (top < maxTop && rowDarkRatio(top) > DARK_LINE_RATIO) top++
        var bottom = 0
        while (bottom < maxTop && rowDarkRatio(h - 1 - bottom) > DARK_LINE_RATIO) bottom++
        var left = 0
        while (left < maxLeft && colDarkRatio(left) > DARK_LINE_RATIO) left++
        var right = 0
        while (right < maxLeft && colDarkRatio(w - 1 - right) > DARK_LINE_RATIO) right++

        return Insets(
            left = left.toFloat() / w,
            top = top.toFloat() / h,
            right = right.toFloat() / w,
            bottom = bottom.toFloat() / h
        )
    }
}
