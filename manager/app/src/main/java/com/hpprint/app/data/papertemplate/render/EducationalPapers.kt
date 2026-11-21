package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** 学习教育类纸张 */
fun drawEducational(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.ENGLISH_PAPER -> englishPaper(ctx)
        PaperType.PINYIN_PAPER -> pinyinPaper(ctx)
        PaperType.ARITHMETIC -> arithmetic(ctx)
        PaperType.PRACTICE -> practice(ctx)
        PaperType.NUMBER_LINE -> numberLine(ctx)
        PaperType.FRACTION_BARS -> fractionBars(ctx)
        PaperType.TEN_FRAME -> tenFrame(ctx)
        PaperType.CLOCK_FACES -> clockFaces(ctx)
        PaperType.BRAILLE -> braille(ctx)
        else -> {}
    }
}

/** 英语四线三格：每组 4 条线，中格略宽 */
private fun englishPaper(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val rowH = s * 3f
    var top = ctx.contentTop
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        for (i in 0..3) ctx.hLine(top + s * i)
        top += rowH + s * 1.2f
    }
}

/** 拼音四线格：同四线三格 */
private fun pinyinPaper(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val rowH = s * 3f
    var top = ctx.contentTop
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        for (i in 0..3) ctx.hLine(top + s * i)
        top += rowH + s * 1.2f
    }
}

/** 算术练习纸：方格 */
private fun arithmetic(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var x = ctx.contentLeft
    while (x <= ctx.contentRight + 0.01f) {
        ctx.vLine(x)
        x += s
    }
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
}

/** 数学练习纸：横线 */
private fun practice(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
}

/** 数轴纸：水平数轴带刻度 */
private fun numberLine(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val midY = (ctx.contentTop + ctx.contentBottom) / 2f
    val left = ctx.contentLeft
    val right = ctx.contentRight
    // 数轴主线
    ctx.hLine(midY, left, right, ctx.linePaint)
    // 两端箭头
    ctx.canvas.drawLine(ctx.mm(right), ctx.mm(midY), ctx.mm(right - 3f), ctx.mm(midY - 2f), ctx.linePaint)
    ctx.canvas.drawLine(ctx.mm(right), ctx.mm(midY), ctx.mm(right - 3f), ctx.mm(midY + 2f), ctx.linePaint)
    // 刻度
    var x = floor(left / s) * s
    var n = 0
    while (x <= right + 0.01f) {
        val tickH = if (n % 5 == 0) 4f else 2f
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(midY - tickH), ctx.mm(x), ctx.mm(midY + tickH), ctx.linePaint)
        if (n % 5 == 0) ctx.text("$n", x, midY + tickH + 4f, 3.5f)
        x += s
        n++
    }
}

/** 分数条：水平长条 + 等分刻度 */
private fun fractionBars(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val barH = s * 1.4f
    val left = ctx.contentLeft
    val right = ctx.contentRight
    var top = ctx.contentTop
    while (top + barH <= ctx.contentBottom + 0.01f) {
        ctx.rect(left, top, right, top + barH, ctx.linePaint)
        // 等分刻度
        val divs = 8
        for (i in 1 until divs) {
            val x = left + (right - left) * i / divs
            ctx.canvas.drawLine(ctx.mm(x), ctx.mm(top), ctx.mm(x), ctx.mm(top + barH), ctx.thinPaint)
        }
        top += barH + s * 2f
    }
}

/** 十格表：2×5 的十格框，多个排列 */
private fun tenFrame(ctx: Ctx) {
    val cell = ctx.cfg.spacingMm
    val rows = 2
    val cols = 5
    val gap = cell * 0.6f
    var top = ctx.contentTop
    while (top + cell * rows <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + cell * cols <= ctx.contentRight + 0.01f) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    ctx.rect(left + c * cell, top + r * cell, left + c * cell + cell, top + r * cell + cell, ctx.linePaint)
                }
            }
            left += cell * cols + gap
        }
        top += cell * rows + gap
    }
}

/** 钟面练习纸：空表盘 */
private fun clockFaces(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val r = s * 2.6f
    val gap = r * 2.6f
    var cy = ctx.contentTop + r
    while (cy + r <= ctx.contentBottom + 0.01f) {
        var cx = ctx.contentLeft + r
        while (cx + r <= ctx.contentRight + 0.01f) {
            ctx.circle(cx, cy, r, ctx.linePaint)
            // 12 个刻度
            for (i in 0 until 12) {
                val a = i * PI / 6.0
                val x1 = cx + (r * 0.92f) * cos(a).toFloat()
                val y1 = cy + (r * 0.92f) * sin(a).toFloat()
                val x2 = cx + (r * 0.80f) * cos(a).toFloat()
                val y2 = cy + (r * 0.80f) * sin(a).toFloat()
                ctx.canvas.drawLine(ctx.mm(x1), ctx.mm(y1), ctx.mm(x2), ctx.mm(y2), ctx.linePaint)
            }
            cx += gap
        }
        cy += gap
    }
}

/** 盲文点字纸：2×3 点阵单元格 */
private fun braille(ctx: Ctx) {
    val cell = ctx.cfg.spacingMm
    val dotR = cell * 0.14f
    val cols = 2
    val rows = 3
    var top = ctx.contentTop
    while (top + cell * rows <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + cell * cols <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + cell * cols, top + cell * rows, ctx.thinPaint)
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    ctx.dot(left + cell * (c + 0.5f), top + cell * (r + 0.5f), dotR)
                }
            }
            left += cell * cols + cell * 0.4f
        }
        top += cell * rows + cell * 0.4f
    }
}
