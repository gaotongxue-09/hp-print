package com.hpprint.app.data.papertemplate.render

import android.graphics.Path
import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 专业图表类纸张 */
fun drawSpecial(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.ISOMETRIC -> isometric(ctx)
        PaperType.HEXAGONAL -> hexagonal(ctx)
        PaperType.POLAR_GRID -> polarGrid(ctx)
        PaperType.LOG_LOG -> logLog(ctx)
        PaperType.SEMI_LOG -> semiLog(ctx)
        PaperType.KNITTING_GRAPH -> knittingGraph(ctx)
        PaperType.SEWING_GRID -> sewingGrid(ctx)
        PaperType.PERSPECTIVE -> perspective(ctx)
        PaperType.STORYBOARD -> storyboard(ctx)
        PaperType.SWOT -> swot(ctx)
        PaperType.CHILDREN_DRAWING -> childrenDrawing(ctx)
        else -> {}
    }
}

/** 等距网格：60° 斜线 + 120° 斜线 + 水平线 */
private fun isometric(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val dy = s * sqrt(3f) / 2f
    // 水平线
    var y = t
    while (y <= b) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += dy
    }
    // 斜线（60° 和 120°）
    val h = ctx.contentHeight
    var x = l - h / sqrt(3f)
    while (x <= r + h / sqrt(3f)) {
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(t), ctx.mm(x + h / sqrt(3f)), ctx.mm(b), ctx.thinPaint)
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(b), ctx.mm(x + h / sqrt(3f)), ctx.mm(t), ctx.thinPaint)
        x += s
    }
}

/** 六边形蜂窝网格 */
private fun hexagonal(ctx: Ctx) {
    val a = ctx.cfg.spacingMm / 2f  // 边长
    val colW = 3f * a / 2f
    val rowH = sqrt(3f) * a
    val l = ctx.contentLeft
    val t = ctx.contentTop
    val r = ctx.contentRight
    val b = ctx.contentBottom

    var row = 0
    var y = t
    while (y - a < b) {
        val offset = if (row % 2 == 1) colW / 2f else 0f
        var x = l + offset
        while (x - a < r) {
            val p = Path()
            for (i in 0 until 6) {
                val angle = PI / 6.0 + i * PI / 3.0
                val vx = x + a * cos(angle).toFloat()
                val vy = y + a * sin(angle).toFloat()
                if (i == 0) p.moveTo(ctx.mm(vx), ctx.mm(vy)) else p.lineTo(ctx.mm(vx), ctx.mm(vy))
            }
            p.close()
            ctx.path(p, ctx.thinPaint)
            x += colW
        }
        y += rowH
        row++
    }
}

/** 极坐标网格：同心圆 + 放射线 */
private fun polarGrid(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val cx = (ctx.contentLeft + ctx.contentRight) / 2f
    val cy = (ctx.contentTop + ctx.contentBottom) / 2f
    val maxR = minOf(ctx.contentWidth, ctx.contentHeight) / 2f
    // 同心圆
    var r = s
    while (r <= maxR) {
        ctx.circle(cx, cy, r, ctx.thinPaint)
        r += s
    }
    // 放射线（每 15°）
    val steps = 24
    for (i in 0 until steps) {
        val angle = i * PI / steps
        val x2 = cx + maxR * cos(angle).toFloat()
        val y2 = cy + maxR * sin(angle).toFloat()
        ctx.canvas.drawLine(ctx.mm(cx), ctx.mm(cy), ctx.mm(x2), ctx.mm(y2), ctx.thinPaint)
    }
}

/** 双对数纸：两轴对数刻度 */
private fun logLog(ctx: Ctx) {
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    // 垂直对数刻度线
    val decades = 4
    for (d in 0..decades) {
        val x = l + ctx.contentWidth * d / decades
        ctx.vLine(x, t, b, ctx.linePaint)
        for (i in 2..9) {
            val xi = l + ctx.contentWidth * (d + log10(i.toDouble()).toFloat()) / decades
            ctx.vLine(xi, t, b, ctx.thinPaint)
        }
    }
    // 水平对数刻度线
    for (d in 0..decades) {
        val y = t + ctx.contentHeight * d / decades
        ctx.hLine(y, l, r, ctx.linePaint)
        for (i in 2..9) {
            val yi = t + ctx.contentHeight * (d + log10(i.toDouble()).toFloat()) / decades
            ctx.hLine(yi, l, r, ctx.thinPaint)
        }
    }
}

/** 半对数纸：水平轴线性 + 垂直轴对数 */
private fun semiLog(ctx: Ctx) {
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val decades = 4
    // 水平线性刻度
    val s = ctx.cfg.spacingMm
    var x = l
    while (x <= r + 0.01f) {
        ctx.vLine(x, t, b, ctx.thinPaint)
        x += s
    }
    // 垂直对数刻度
    for (d in 0..decades) {
        val y = t + ctx.contentHeight * d / decades
        ctx.hLine(y, l, r, ctx.linePaint)
        for (i in 2..9) {
            val yi = t + ctx.contentHeight * (d + log10(i.toDouble()).toFloat()) / decades
            ctx.hLine(yi, l, r, ctx.thinPaint)
        }
    }
}

/** 编织图：长方形格（高宽比 1:0.75） */
private fun knittingGraph(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val cellH = s * 0.75f
    var y = ctx.contentTop
    while (y + cellH <= ctx.contentBottom + 0.01f) {
        var x = ctx.contentLeft
        while (x + s <= ctx.contentRight + 0.01f) {
            ctx.rect(x, y, x + s, y + cellH, ctx.thinPaint)
            x += s
        }
        y += cellH
    }
}

/** 缝纫格：1cm 方格 + 每 5cm 粗线 */
private fun sewingGrid(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val t = ctx.contentTop
    val r = ctx.contentRight
    val b = ctx.contentBottom
    val major = 5f
    var x = kotlin.math.floor(l / s) * s
    while (x <= r + 0.01f) {
        val p = if (kotlin.math.abs(x % major) < 0.01f) ctx.linePaint else ctx.thinPaint
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(t), ctx.mm(x), ctx.mm(b), p)
        x += s
    }
    var y = kotlin.math.floor(t / s) * s
    while (y <= b + 0.01f) {
        val p = if (kotlin.math.abs(y % major) < 0.01f) ctx.linePaint else ctx.thinPaint
        ctx.canvas.drawLine(ctx.mm(l), ctx.mm(y), ctx.mm(r), ctx.mm(y), p)
        y += s
    }
}

/** 透视网格：单点透视（消失点放射 + 水平线 + 垂直线） */
private fun perspective(ctx: Ctx) {
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val vpX = (l + r) / 2f
    val vpY = b
    // 水平线
    val s = ctx.cfg.spacingMm
    var y = t
    while (y <= b) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
    // 从消失点放射线
    val n = 20
    for (i in 0..n) {
        val xTop = l + ctx.contentWidth * i / n
        ctx.canvas.drawLine(ctx.mm(vpX), ctx.mm(vpY), ctx.mm(xTop), ctx.mm(t), ctx.thinPaint)
    }
    // 垂直线
    var x = l
    while (x <= r) {
        ctx.vLine(x, t, b, ctx.thinPaint)
        x += s
    }
}

/** 分镜纸：2×3 大框 */
private fun storyboard(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val t = ctx.contentTop
    val cols = 3
    val rows = 2
    val gap = s
    val cellW = (ctx.contentWidth - gap * (cols - 1)) / cols
    val cellH = (ctx.contentHeight - gap * (rows - 1)) / rows
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = l + col * (cellW + gap)
            val y = t + row * (cellH + gap)
            ctx.rect(x, y, x + cellW, y + cellH, ctx.linePaint)
        }
    }
}

/** SWOT：2×2 四象限 */
private fun swot(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val midX = (l + r) / 2f
    val midY = (t + b) / 2f
    val headH = s * 1.4f
    // 四象限标题
    ctx.rect(l, t, r, b, ctx.linePaint)
    ctx.vLine(midX, t, b, ctx.linePaint)
    ctx.hLine(midY, l, r, ctx.linePaint)
    ctx.text("S 优势", (l + midX) / 2f, t + headH / 2, headH * 0.6f)
    ctx.text("W 劣势", (midX + r) / 2f, t + headH / 2, headH * 0.6f)
    ctx.text("O 机会", (l + midX) / 2f, midY + headH / 2, headH * 0.6f)
    ctx.text("T 威胁", (midX + r) / 2f, midY + headH / 2, headH * 0.6f)
    // 各象限横线
    val ql = listOf(l to t to (midX to midY), midX to t to (r to midY),
        l to midY to (midX to b), midX to midY to (r to b))
    for (q in ql) {
        val (q1, q2) = q
        val (x0, y0) = q1
        val (x1, y1) = q2
        var y = y0 + headH
        while (y + s < y1) {
            ctx.canvas.drawLine(ctx.mm(x0 + s * 0.5f), ctx.mm(y), ctx.mm(x1 - s * 0.5f), ctx.mm(y), ctx.thinPaint)
            y += s
        }
    }
}

/** 儿童绘画纸：大方格 */
private fun childrenDrawing(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var x = ctx.contentLeft
    while (x <= ctx.contentRight + 0.01f) {
        ctx.vLine(x, ctx.contentTop, ctx.contentBottom, ctx.thinPaint)
        x += s
    }
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y, ctx.contentLeft, ctx.contentRight, ctx.thinPaint)
        y += s
    }
}
