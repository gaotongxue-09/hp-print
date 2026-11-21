package com.hpprint.app.ui.screen.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.hpprint.app.data.scan.core.Quad
import kotlin.math.max
import kotlin.math.min

/**
 * 手动矫正视图：四个角各自独立拖动，框选出来的四边形会被透视拉正。
 * 四角手柄 + 放大镜 + 三分线；框内拖动整体平移；角点越界/自交自动拒绝。
 */
class QuadCropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var quad: Quad? = null

    private var scale = 1f
    private var dx = 0f
    private var dy = 0f

    private var dragIndex = -1
    private var moving = false
    private var downX = 0f
    private var downY = 0f
    private var startQuad: Quad? = null

    private val density = resources.displayMetrics.density
    private val touchRadius = 40f * density

    /** 图片四周留白：手柄离屏幕边缘远一点，避免和返回手势冲突 */
    private val edgePadding = 26f * density

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = 0xFFFABB06.toInt()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(11f * density, 7f * density), 0f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x40FFFFFF
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFABB06.toInt()
    }
    private val handleHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FFFFFF
    }
    private val magRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val magBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6000000.toInt() }

    private val path = Path()
    private val vp = Array(4) { PointF() }

    fun setImage(bmp: Bitmap, q: Quad? = null) {
        bitmap = bmp
        quad = q?.clampTo(bmp.width.toFloat(), bmp.height.toFloat())
            ?: Quad.insetRect(bmp.width.toFloat(), bmp.height.toFloat(), 0.004f)
        updateTransform()
        invalidate()
    }

    fun getQuad(): Quad? = quad?.copyOfQuad()

    fun getBitmap(): Bitmap? = bitmap

    /** 重置成贴着图片的矩形框 */
    fun resetQuad() {
        val bmp = bitmap ?: return
        quad = Quad.insetRect(bmp.width.toFloat(), bmp.height.toFloat(), 0.004f)
        invalidate()
    }

    /** 顺时针旋转 90°，四角跟着走 */
    fun rotate90() {
        val bmp = bitmap ?: return
        val m = Matrix().apply { postRotate(90f) }
        val nb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val q = quad
        val h0 = bmp.height.toFloat()
        fun t(p: PointF) = PointF(h0 - p.y, p.x)
        val nq = if (q == null) null else Quad(t(q.bl), t(q.tl), t(q.tr), t(q.br))
        bitmap = nb
        quad = nq
        updateTransform()
        invalidate()
    }

    private fun updateTransform() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val pad = min(edgePadding, min(width, height) / 6f)
        val availW = max(1f, width - pad * 2)
        val availH = max(1f, height - pad * 2)
        scale = min(availW / bmp.width, availH / bmp.height)
        dx = (width - bmp.width * scale) / 2f
        dy = (height - bmp.height * scale) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTransform()
    }

    private fun toView(p: PointF) = PointF(p.x * scale + dx, p.y * scale + dy)

    private fun toImage(x: Float, y: Float): PointF {
        val bmp = bitmap ?: return PointF(0f, 0f)
        return PointF(
            ((x - dx) / scale).coerceIn(0f, bmp.width.toFloat()),
            ((y - dy) / scale).coerceIn(0f, bmp.height.toFloat())
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        val dst = RectF(dx, dy, dx + bmp.width * scale, dy + bmp.height * scale)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        val q = quad ?: return
        val pts = q.points()
        for (i in 0..3) vp[i].set(toView(pts[i]))

        // 框外压暗：整图减去四边形
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(dst, Path.Direction.CW)
        path.moveTo(vp[0].x, vp[0].y)
        for (i in 1..3) path.lineTo(vp[i].x, vp[i].y)
        path.close()
        canvas.drawPath(path, dimPaint)

        // 透视后的边框
        path.reset()
        path.moveTo(vp[0].x, vp[0].y)
        for (i in 1..3) path.lineTo(vp[i].x, vp[i].y)
        path.close()
        canvas.drawPath(path, framePaint)

        // 三分辅助线（跟着透视走）
        val saved = canvas.save()
        canvas.clipPath(path)
        for (i in 1..2) {
            val t = i / 3f
            canvas.drawLine(
                vp[0].x + (vp[1].x - vp[0].x) * t, vp[0].y + (vp[1].y - vp[0].y) * t,
                vp[3].x + (vp[2].x - vp[3].x) * t, vp[3].y + (vp[2].y - vp[3].y) * t,
                gridPaint
            )
            canvas.drawLine(
                vp[0].x + (vp[3].x - vp[0].x) * t, vp[0].y + (vp[3].y - vp[0].y) * t,
                vp[1].x + (vp[2].x - vp[1].x) * t, vp[1].y + (vp[2].y - vp[1].y) * t,
                gridPaint
            )
        }
        canvas.restoreToCount(saved)

        // 四个角手柄
        val active = dragIndex
        for (i in 0..3) {
            val p = vp[i]
            if (i == active) canvas.drawCircle(p.x, p.y, 20f * density, handleHalo)
            canvas.drawCircle(p.x, p.y, 9f * density, handleRing)
            canvas.drawCircle(p.x, p.y, 6.5f * density, handleFill)
        }

        if (active in 0..3) {
            val p = vp[active]
            val radius = 46f * density
            var cy = p.y - 96f * density
            if (cy - radius < 0f) cy = p.y + 96f * density
            // 气泡横向收拢，避免贴边时被裁掉；取样点始终是当前拖动的角点
            val cx = if (width >= radius * 2f) p.x.coerceIn(radius, width - radius) else p.x
            drawMagnifier(canvas, p.x, p.y, cx, cy, radius)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false
        val q = quad ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pts = q.points()
                var best = -1
                var bestD = touchRadius * 1.5f
                for (i in 0..3) {
                    val v = toView(pts[i])
                    val d = Quad.dist(v, PointF(event.x, event.y))
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
                if (best >= 0) {
                    dragIndex = best
                } else if (contains(pts, event.x, event.y)) {
                    moving = true
                } else {
                    return false
                }
                downX = event.x
                downY = event.y
                startQuad = q.copyOfQuad()
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val start = startQuad ?: return false
                if (dragIndex in 0..3) {
                    val p = toImage(event.x, event.y)
                    val pts = start.points()
                    pts[dragIndex].set(p.x, p.y)
                    val candidate = Quad(pts[0], pts[1], pts[2], pts[3])
                    if (isUsable(candidate, bmp)) {
                        quad = candidate
                        invalidate()
                    }
                    return true
                }
                if (moving) {
                    var tx = (event.x - downX) / scale
                    var ty = (event.y - downY) / scale
                    val moved = start.translated(tx, ty)
                    val mp = moved.points()
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (p in mp) {
                        minX = min(minX, p.x); minY = min(minY, p.y)
                        maxX = max(maxX, p.x); maxY = max(maxY, p.y)
                    }
                    if (minX < 0f) tx -= minX
                    if (minY < 0f) ty -= minY
                    if (maxX > bmp.width) tx -= (maxX - bmp.width)
                    if (maxY > bmp.height) ty -= (maxY - bmp.height)
                    quad = start.translated(tx, ty)
                    invalidate()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragIndex >= 0 || moving) {
                    dragIndex = -1
                    moving = false
                    startQuad = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /** 点是否落在四边形内（射线法，图片坐标） */
    private fun contains(pts: Array<PointF>, x: Float, y: Float): Boolean {
        val ix = (x - dx) / scale
        val iy = (y - dy) / scale
        var inside = false
        var j = 3
        for (i in 0..3) {
            val a = pts[i]
            val b = pts[j]
            if ((a.y > iy) != (b.y > iy)) {
                val t = (iy - a.y) / (b.y - a.y)
                if (ix < a.x + t * (b.x - a.x)) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** 凸四边形、面积和边长都够大，才允许落地 */
    private fun isUsable(c: Quad, bmp: Bitmap): Boolean {
        val minArea = bmp.width.toFloat() * bmp.height * 0.03f
        if (c.area() < minArea) return false
        val edges = c.edgeLengths()
        for (e in edges) if (e < max(20f, min(bmp.width, bmp.height) * 0.03f)) return false
        val p = c.points()
        for (i in 0..3) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val d = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            if (cross <= 0f) return false
        }
        return true
    }

    /**
     * 放大镜：取 [srcX]/[srcY]（当前拖动的角点）周围的内容，绘制在 [cx]/[cy] 位置。
     * 取样点与绘制点分离，手指按住的角点会出现在放大镜中心。
     */
    private fun drawMagnifier(
        canvas: Canvas,
        srcX: Float,
        srcY: Float,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val bmp = bitmap ?: return
        val ix = (srcX - dx) / scale
        val iy = (srcY - dy) / scale
        val half = (radius / scale) / 2.4f
        val src = Rect(
            (ix - half).toInt().coerceIn(0, bmp.width - 1),
            (iy - half).toInt().coerceIn(0, bmp.height - 1),
            (ix + half).toInt().coerceIn(1, bmp.width),
            (iy + half).toInt().coerceIn(1, bmp.height)
        )
        if (src.width() < 2 || src.height() < 2) return
        canvas.drawCircle(cx, cy, radius, magBg)
        canvas.drawBitmap(
            bmp, src, RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        canvas.drawCircle(cx, cy, radius, magRing)
        canvas.drawLine(cx - 8 * density, cy, cx + 8 * density, cy, magRing)
        canvas.drawLine(cx, cy - 8 * density, cx, cy + 8 * density, magRing)
    }
}
