package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.floor

/** 基础线条类纸张 */
fun drawBasic(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.LINED -> linedPaper(ctx)
        PaperType.VERTICAL_LINED -> verticalLinedPaper(ctx)
        PaperType.GRID -> gridPaper(ctx)
        PaperType.DOT -> dotPaper(ctx)
        PaperType.BLANK -> blankPaper(ctx)
        PaperType.GRID_LINED_SPLIT -> splitPaper(ctx)
        PaperType.PINK_MILLIMETER_GRID -> millimeterGrid(ctx)
        else -> {}
    }
}

/** 横线纸：等距水平线 */
private fun linedPaper(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
}

/** 竖线纸：等距垂直线 */
private fun verticalLinedPaper(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var x = ctx.contentLeft
    while (x <= ctx.contentRight + 0.01f) {
        ctx.vLine(x)
        x += s
    }
}

/** 方格纸：等距网格 */
private fun gridPaper(ctx: Ctx) {
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

/** 点阵纸：等距圆点 */
private fun dotPaper(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val r = (s * 0.12f).coerceIn(0.3f, 1.2f)
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        var x = ctx.contentLeft
        while (x <= ctx.contentRight + 0.01f) {
            ctx.dot(x, y, r)
            x += s
        }
        y += s
    }
}

/** 空白纸：仅背景与边框 */
private fun blankPaper(ctx: Ctx) {
    ctx.rect(ctx.contentLeft, ctx.contentTop, ctx.contentRight, ctx.contentBottom, ctx.thinPaint)
}

/** 左半方格 + 右半横线 */
private fun splitPaper(ctx: Ctx) {
    val gridS = ctx.cfg.spacingMm
    val lineS = ctx.cfg.secondaryMm
    val mid = (ctx.contentLeft + ctx.contentRight) / 2f

    // 左半：方格
    var x = ctx.contentLeft
    while (x <= mid + 0.01f) {
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(ctx.contentTop), ctx.mm(x), ctx.mm(ctx.contentBottom), ctx.linePaint)
        x += gridS
    }
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.canvas.drawLine(ctx.mm(ctx.contentLeft), ctx.mm(y), ctx.mm(mid), ctx.mm(y), ctx.linePaint)
        y += gridS
    }
    // 右半：横线
    y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.canvas.drawLine(ctx.mm(mid), ctx.mm(y), ctx.mm(ctx.contentRight), ctx.mm(y), ctx.linePaint)
        y += lineS
    }
    // 分隔线（加重）
    ctx.canvas.drawLine(ctx.mm(mid), ctx.mm(ctx.contentTop), ctx.mm(mid), ctx.mm(ctx.contentBottom), ctx.accentPaint)
}

/** 毫米方格纸：1mm 细格 + 5mm 中格 + 10mm 粗格 */
private fun millimeterGrid(ctx: Ctx) {
    val step = 1f
    val major = 5f
    val big = 10f
    val left = ctx.contentLeft
    val top = ctx.contentTop
    val right = ctx.contentRight
    val bottom = ctx.contentBottom

    var x = floor(left / step) * step
    while (x <= right + 0.01f) {
        val p = gridLinePaint(ctx, x, big, major)
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(top), ctx.mm(x), ctx.mm(bottom), p)
        x += step
    }
    var y = floor(top / step) * step
    while (y <= bottom + 0.01f) {
        val p = gridLinePaint(ctx, y, big, major)
        ctx.canvas.drawLine(ctx.mm(left), ctx.mm(y), ctx.mm(right), ctx.mm(y), p)
        y += step
    }
}

/** 根据刻度值返回对应粗细的画笔（毫米网格用） */
private fun gridLinePaint(ctx: Ctx, v: Float, big: Float, major: Float): android.graphics.Paint {
    val isBig = kotlin.math.abs(v % big) < 0.01f
    val isMajor = kotlin.math.abs(v % major) < 0.01f
    return when {
        isBig -> ctx.linePaint
        isMajor -> ctx.accentPaint
        else -> ctx.thinPaint
    }
}
