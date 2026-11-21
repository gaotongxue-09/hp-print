package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * 本地滤镜引擎（纯 CPU 实现，不联网）。
 *
 * 阴影/光照不均的处理思路：先把原图大幅降采样并做半径较大的模糊，
 * 得到“背景光照图”，再用每个像素除以背景得到归一化亮度。
 * 这一步也是“去阴影”和“黑白文档”效果的基础。
 */
object Filters {

    const val ORIGINAL = 0
    const val ENHANCE = 1
    const val GRAY = 2
    const val BW = 3
    const val SHADOW = 4

    val all = intArrayOf(ORIGINAL, ENHANCE, GRAY, BW, SHADOW)

    fun apply(src: Bitmap, mode: Int): Bitmap {
        if (mode == ORIGINAL) {
            return if (src.config == Bitmap.Config.ARGB_8888) src
            else src.copy(Bitmap.Config.ARGB_8888, false)
        }

        // 增强/灰度/黑白走原生扫描模型；去阴影按约定保留本地实现
        if (mode != SHADOW) {
            NativeScanEngine.filter(src, mode)?.let { return it }
        }

        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        if (mode == GRAY) {
            for (i in px.indices) {
                val c = px[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = (r * 299 + g * 587 + b * 114) / 1000
                px[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
        } else {
            val bg = background(px, w, h)
            when (mode) {
                ENHANCE -> enhance(px, w, h, bg)
                BW -> document(px, w, h, bg, binary = true)
                SHADOW -> document(px, w, h, bg, binary = false)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** 返回 [w,h] 尺度上的背景亮度（仅亮度通道，降采样后双线性放大） */
    private class Bg(val w: Int, val h: Int, val data: IntArray, val srcW: Int, val srcH: Int)

    private fun background(px: IntArray, w: Int, h: Int): Bg {
        val step = 8
        val bw = max(8, w / step)
        val bh = max(8, h / step)
        val small = IntArray(bw * bh)

        for (by in 0 until bh) {
            val y0 = by * h / bh
            val y1 = max(y0 + 1, (by + 1) * h / bh)
            for (bx in 0 until bw) {
                val x0 = bx * w / bw
                val x1 = max(x0 + 1, (bx + 1) * w / bw)
                var sum = 0L
                var n = 0
                var y = y0
                while (y < y1) {
                    var x = x0
                    while (x < x1) {
                        val c = px[y * w + x]
                        sum += ((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114
                        n++
                        x += 2
                    }
                    y += 2
                }
                small[by * bw + bx] = if (n == 0) 200 else (sum / (n * 1000)).toInt()
            }
        }

        val r = max(3, min(bw, bh) / 10)
        val blurred = boxBlur(small, bw, bh, r)
        return Bg(bw, bh, blurred, w, h)
    }

    private fun sampleBg(bg: Bg, x: Int, y: Int): Int {
        val bx = (x * bg.w / bg.srcW).coerceIn(0, bg.w - 1)
        val by = (y * bg.h / bg.srcH).coerceIn(0, bg.h - 1)
        return bg.data[by * bg.w + bx]
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) sum += src[row + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                sum += src[row + (x + r + 1).coerceIn(0, w - 1)] - src[row + (x - r).coerceIn(0, w - 1)]
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) sum += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                sum += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }

    /** 彩色增亮：按背景归一化后再拉一点对比 */
    private fun enhance(px: IntArray, w: Int, h: Int, bg: Bg) {
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val c = px[i]
                val b = sampleBg(bg, x, y).coerceAtLeast(1)
                val gain = 235f / b
                val r = ((c shr 16) and 0xFF) * gain
                val g = ((c shr 8) and 0xFF) * gain
                val bl = (c and 0xFF) * gain
                px[i] = argb(contrast(r, 1.06f), contrast(g, 1.06f), contrast(bl, 1.06f))
            }
        }
    }

    /** 文档模式：binary=true 纯黑白，false 灰度去阴影 */
    private fun document(px: IntArray, w: Int, h: Int, bg: Bg, binary: Boolean) {
        val threshold = 0.78f
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val i = row + x
                val c = px[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b0 = (c and 0xFF)
                val lum = (r * 299 + g * 587 + b0 * 114) / 1000
                val bgv = sampleBg(bg, x, y).coerceAtLeast(1)
                val ratio = lum.toFloat() / bgv.toFloat()

                if (binary) {
                    val v = if (ratio >= threshold) 255 else 0
                    px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                } else {
                    var v = (ratio * 255f).coerceIn(0f, 255f)
                    v = (v - 128f) * 1.18f + 128f
                    val iv = v.toInt().coerceIn(0, 255)
                    px[i] = (0xFF shl 24) or (iv shl 16) or (iv shl 8) or iv
                }
            }
        }
    }

    private fun contrast(v: Float, k: Float): Int = ((v - 128f) * k + 128f).toInt().coerceIn(0, 255)

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
