package com.hpprint.app.data.scan.core

import android.graphics.PointF

/**
 * 四个角的四边形，顺序固定为 左上、右上、右下、左下。
 * 坐标单位为像素，原点在图片左上角。
 */
class Quad(
    val tl: PointF,
    val tr: PointF,
    val br: PointF,
    val bl: PointF
) {
    fun points(): Array<PointF> = arrayOf(tl, tr, br, bl)

    fun copyOfQuad(): Quad = Quad(
        PointF(tl.x, tl.y), PointF(tr.x, tr.y), PointF(br.x, br.y), PointF(bl.x, bl.y)
    )

    fun translated(dx: Float, dy: Float): Quad = Quad(
        PointF(tl.x + dx, tl.y + dy),
        PointF(tr.x + dx, tr.y + dy),
        PointF(br.x + dx, br.y + dy),
        PointF(bl.x + dx, bl.y + dy)
    )

    fun scaled(sx: Float, sy: Float): Quad = Quad(
        PointF(tl.x * sx, tl.y * sy),
        PointF(tr.x * sx, tr.y * sy),
        PointF(br.x * sx, br.y * sy),
        PointF(bl.x * sx, bl.y * sy)
    )

    /** 鞋带公式求面积（取绝对值） */
    fun area(): Float {
        val p = points()
        var s = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            s += a.x * b.y - b.x * a.y
        }
        return Math.abs(s) / 2f
    }

    fun isConvex(): Boolean {
        val p = points()
        var sign = 0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            val c = p[(i + 2) % p.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (sign != s) return false
            }
        }
        return true
    }

    fun edgeLengths(): FloatArray = floatArrayOf(
        dist(tl, tr), dist(tr, br), dist(br, bl), dist(bl, tl)
    )

    fun clampTo(width: Float, height: Float): Quad {
        fun cx(v: Float) = v.coerceIn(0f, width)
        fun cy(v: Float) = v.coerceIn(0f, height)
        return Quad(
            PointF(cx(tl.x), cy(tl.y)),
            PointF(cx(tr.x), cy(tr.y)),
            PointF(cx(br.x), cy(br.y)),
            PointF(cx(bl.x), cy(bl.y))
        )
    }

    companion object {
        fun dist(a: PointF, b: PointF): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }

        /** 默认内缩矩形，用于检测失败时的兜底 */
        fun insetRect(width: Float, height: Float, factor: Float = 0.06f): Quad {
            val dx = width * factor
            val dy = height * factor
            return Quad(
                PointF(dx, dy),
                PointF(width - dx, dy),
                PointF(width - dx, height - dy),
                PointF(dx, height - dy)
            )
        }
    }
}
