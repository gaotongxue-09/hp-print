package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min

/**
 * 透视矫正：把四边形区域拉正成矩形。
 * 用一个 3x3 单应矩阵，通过 8x8 线性方程组求解，再做双线性采样。
 */
object Perspective {

    fun warpBitmap(src: Bitmap, quad: Quad, maxSize: Int = 2600): Bitmap {
        // 原生扫描模型优先；不可用时回退下面的纯 Kotlin 单应性实现
        NativeScanEngine.rectify(src, quad.points().toList())?.let { return limitLongEdge(it, maxSize) }

        val edges = quad.edgeLengths()
        var outW = max(edges[0], edges[2])
        var outH = max(edges[1], edges[3])
        if (outW < 8f || outH < 8f) return src

        val longEdge = max(outW, outH)
        if (longEdge > maxSize) {
            val k = maxSize / longEdge
            outW *= k
            outH *= k
        }

        val ow = outW.toInt().coerceAtLeast(8)
        val oh = outH.toInt().coerceAtLeast(8)
        return warpTo(src, quad, ow, oh)
    }

    /** 原生矫正不限制输出尺寸，统一把长边压到 maxSize，避免超大图占内存。 */
    private fun limitLongEdge(src: Bitmap, maxSize: Int): Bitmap {
        val long = max(src.width, src.height)
        if (maxSize <= 0 || long <= maxSize) return src
        val k = maxSize.toFloat() / long
        val out = Bitmap.createScaledBitmap(
            src,
            (src.width * k).toInt().coerceAtLeast(1),
            (src.height * k).toInt().coerceAtLeast(1),
            true,
        )
        if (out !== src) src.recycle()
        return out
    }

    /** 拉正到指定像素尺寸（证件这类有固定尺寸的目标用它） */
    fun warpTo(src: Bitmap, quad: Quad, outW: Int, outH: Int): Bitmap {
        if (outW < 8 || outH < 8) return src
        val ow = outW
        val oh = outH

        val m = homography(ow.toFloat(), oh.toFloat(), quad) ?: return src

        val sw = src.width
        val sh = src.height
        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        val out = IntArray(ow * oh)
        val a = m[0].toFloat(); val b = m[1].toFloat(); val c = m[2].toFloat()
        val d = m[3].toFloat(); val e = m[4].toFloat(); val f = m[5].toFloat()
        val g = m[6].toFloat(); val h = m[7].toFloat()

        for (y in 0 until oh) {
            val row = y * ow
            for (x in 0 until ow) {
                val den = g * x + h * y + 1f
                if (den == 0f) continue
                val sx = (a * x + b * y + c) / den
                val sy = (d * x + e * y + f) / den
                out[row + x] = bilinear(srcPixels, sw, sh, sx, sy)
            }
        }

        val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, ow, 0, 0, ow, oh)
        return bmp
    }

    private fun bilinear(px: IntArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val x0 = Math.floor(fx.toDouble()).toInt()
        val y0 = Math.floor(fy.toDouble()).toInt()
        val ax = fx - x0
        val ay = fy - y0
        val x1 = (x0 + 1).coerceIn(0, w - 1)
        val y1 = (y0 + 1).coerceIn(0, h - 1)
        val xa = x0.coerceIn(0, w - 1)
        val ya = y0.coerceIn(0, h - 1)

        val p00 = px[ya * w + xa]
        val p10 = px[ya * w + x1]
        val p01 = px[y1 * w + xa]
        val p11 = px[y1 * w + x1]

        fun ch(shift: Int): Int {
            val c00 = (p00 shr shift) and 0xFF
            val c10 = (p10 shr shift) and 0xFF
            val c01 = (p01 shr shift) and 0xFF
            val c11 = (p11 shr shift) and 0xFF
            val top = c00 + (c10 - c00) * ax
            val bot = c01 + (c11 - c01) * ax
            val v = top + (bot - top) * ay
            return v.toInt().coerceIn(0, 255)
        }

        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * 求把 (0,0)(w,0)(w,h)(0,h) 映射到 quad 四个角的单应矩阵，
     * 即“输出像素 → 源图像素”的逆映射，正合适做重采样。
     */
    private fun homography(w: Float, h: Float, q: Quad): DoubleArray? {
        val dst = arrayOf(
            floatArrayOf(0f, 0f), floatArrayOf(w, 0f),
            floatArrayOf(w, h), floatArrayOf(0f, h)
        )
        val srcPts = q.points()
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)

        for (i in 0 until 4) {
            val u = dst[i][0].toDouble()
            val v = dst[i][1].toDouble()
            val x = srcPts[i].x.toDouble()
            val y = srcPts[i].y.toDouble()

            a[i * 2] = doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -u * x, -v * x)
            b[i * 2] = x
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -u * y, -v * y)
            b[i * 2 + 1] = y
        }
        return gauss(a, b)
    }

    private fun gauss(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray? {
        val n = b0.size
        val a = Array(n) { a0[it].copyOf() }
        val b = b0.copyOf()

        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r
            }
            if (Math.abs(a[pivot][col]) < 1e-12) return null
            if (pivot != col) {
                val t = a[col]; a[col] = a[pivot]; a[pivot] = t
                val tb = b[col]; b[col] = b[pivot]; b[pivot] = tb
            }
            val div = a[col][col]
            for (c in col until n) a[col][c] /= div
            b[col] /= div
            for (r in 0 until n) {
                if (r == col) continue
                val factor = a[r][col]
                if (factor == 0.0) continue
                for (c in col until n) a[r][c] -= factor * a[col][c]
                b[r] -= factor * b[col]
            }
        }
        return b
    }

    /** 用于界面绘制：图片坐标 → 视图坐标 */
    fun mapPoint(p: PointF, scale: Float, dx: Float, dy: Float): PointF =
        PointF(p.x * scale + dx, p.y * scale + dy)
}
