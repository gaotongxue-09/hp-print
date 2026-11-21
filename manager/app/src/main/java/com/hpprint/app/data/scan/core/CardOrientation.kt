package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 证件朝向归一：把「竖着拍」「倒着拍」的卡片转正。
 *
 * 分两层处理，都不依赖用户手动转：
 *
 * 1. **90° / 270°（竖拍）**：只看几何。四边形的短边是卡片高度、长边是宽度，
 *    如果拖出来的框是竖的（高 > 宽），直接把四角顺序转 90°，让长边落在输出矩形的上边。
 *    不做这一步的话，竖着的卡片会被硬拉成 1400×883，内容整个压扁。
 *
 * 2. **180°（倒置）**：看内容。用离线 OCR 的行倾角判断文字是正立还是倒着；
 *    文字太少（倒置时识别不出来）就把图翻过来再识别一次，看哪边认出的字多。
 *    两条都不确定时，用身份证的版面先验兜底：
 *      - 人像面：证件照在右半边，若高饱和区域集中在左边 → 倒置
 *      - 国徽面：国徽是红色的、在上半部，若红色集中在下方 → 倒置
 *
 * 全都判断不出来时保持原样，证件页还有「翻转」可以手动纠正。
 */
object CardOrientation {

    const val SIDE_FRONT = 0
    const val SIDE_BACK = 1
    const val SIDE_UNKNOWN = -1

    /** 判断朝向至少要有的「墨水量」（有效字符数 × 置信度） */
    private const val MIN_INK = 22f

    /** 高比宽超过这个倍数才算「竖着拍」 */
    private const val PORTRAIT_RATIO = 1.08f

    /**
     * 把四边形转成「长边在上」的方向。
     * 只换四角顺序、不动坐标，所以透视关系不变，只是输出时整体转 90°。
     */
    fun landscape(q: Quad): Quad {
        val p = q.points()
        val w = (Quad.dist(p[0], p[1]) + Quad.dist(p[2], p[3])) / 2f
        val h = (Quad.dist(p[1], p[2]) + Quad.dist(p[3], p[0])) / 2f
        if (w >= h * PORTRAIT_RATIO) return q
        // 顺时针 90°：新 tl = 旧 bl，新 tr = 旧 tl，新 br = 旧 tr，新 bl = 旧 br
        return Quad(p[3], p[0], p[1], p[2])
    }

    /** 位图顺时针旋转 [deg] 度；不需要转时返回 null */
    fun rotated(src: Bitmap, deg: Float): Bitmap? {
        var d = deg % 360f
        if (d < 0f) d += 360f
        if (d < 0.5f) return null
        val m = Matrix().apply { postRotate(d) }
        return try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } catch (e: Throwable) {
            null
        }
    }

    /** 判断（已拉正的）卡片图是否上下颠倒 */
    fun isUpsideDown(bmp: Bitmap, side: Int = SIDE_UNKNOWN): Boolean {
        val e = evidence(bmp)
        if (e.ink >= MIN_INK) {
            val ratio = if (e.ink > 0f) e.upright / e.ink else 1f
            Log.d(TAG, "ink=${e.ink.toInt()} upright=${"%.2f".format(ratio)}")
            if (ratio < 0.45f) return true
            if (ratio > 0.60f) return false
            return prior(bmp, side) ?: false
        }

        // 字太少：倒置时 OCR 常常一个都认不出，翻过来再试一次
        val flipped = rotated(bmp, 180f)
        if (flipped != null) {
            val e2 = evidence(flipped)
            flipped.recycle()
            Log.d(TAG, "ink=${e.ink.toInt()} flipInk=${e2.ink.toInt()}")
            if (e2.ink >= max(MIN_INK, e.ink * 1.8f)) return true
        } else {
            Log.d(TAG, "ink=${e.ink.toInt()} (no text)")
        }
        return prior(bmp, side) ?: false
    }

    /**
     * 需要翻转就返回转好的新位图，否则返回原图（调用方自行比较引用、回收旧图）。
     * [side] 未知（比如文档裁切）时不判断，直接原样返回。
     */
    fun orient(src: Bitmap, side: Int = SIDE_UNKNOWN): Bitmap {
        if (side == SIDE_UNKNOWN) return src
        if (!isUpsideDown(src, side)) return src
        return rotated(src, 180f) ?: src
    }

    // ------------------------------------------------------------------ 内部

    private const val TAG = "CardOrient"

    private class Evidence(val ink: Float, val upright: Float)

    /** 文字证据：ink 为总量，upright 为朝着正立方向的那部分 */
    private fun evidence(bmp: Bitmap): Evidence {
        var ink = 0f
        var up = 0f
        var minAng = 999f
        var maxAng = -999f
        for (l in OcrEngine.lines(bmp)) {
            val w = weight(l)
            if (w <= 0f) continue
            ink += w
            // 0° 正立 → 1；180° 倒置 → 0
            val c = cos(Math.toRadians(l.angle.toDouble())).toFloat()
            up += w * (0.5f + 0.5f * c)
            if (l.angle < minAng) minAng = l.angle
            if (l.angle > maxAng) maxAng = l.angle
        }
        if (ink > 0f) Log.d(TAG, "lines ink=${ink.toInt()} angle=[${minAng.toInt()},${maxAng.toInt()}]")
        return Evidence(ink, up)
    }

    /** 一行文字的分量：汉字优先，置信度缺失时按 0.75 计 */
    private fun weight(l: OcrEngine.Line): Float {
        val cjk = l.text.count { it.code > 0x2E7F }
        val len = if (cjk > 0) cjk else l.text.count { it.isLetterOrDigit() }
        if (len == 0) return 0f
        val c = if (l.confidence.isNaN() || l.confidence <= 0f) 0.75f else l.confidence.coerceIn(0f, 1f)
        return len * c
    }

    /**
     * 版面先验：只有信号足够强才给结论，否则返回 null（保持原样）。
     */
    private fun prior(bmp: Bitmap, side: Int): Boolean? {
        if (side == SIDE_UNKNOWN) return null
        val w = bmp.width
        val h = bmp.height
        if (w < 48 || h < 48) return null

        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        val step = max(1, min(w, h) / 160)
        var redTop = 0
        var redBottom = 0
        var satLeft = 0
        var satRight = 0
        var total = 0

        var y = 0
        while (y < h) {
            val row = y * w
            var x = 0
            while (x < w) {
                val c = px[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                total++
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                if (r - max(g, b) > 30 && r > 80) {
                    if (y < h / 2) redTop++ else redBottom++
                }
                if (mx - mn > 28 && mx > 60) {
                    if (x < w / 2) satLeft++ else satRight++
                }
                x += step
            }
            y += step
        }
        if (total == 0) return null

        return if (side == SIDE_FRONT) {
            // 人像面：证件照在右半边
            val l = satLeft.toFloat() / total
            val r = satRight.toFloat() / total
            Log.d(TAG, "prior front satL=$l satR=$r")
            when {
                r > 0.02f && l < r * 0.55f -> false
                l > 0.02f && r < l * 0.55f -> true
                else -> null
            }
        } else {
            // 国徽面：国徽在上半部
            Log.d(TAG, "prior back redTop=$redTop redBottom=$redBottom")
            when {
                redBottom > 40 && redBottom > redTop * 1.6f -> true
                redTop > 40 && redTop > redBottom * 1.6f -> false
                else -> null
            }
        }
    }
}
