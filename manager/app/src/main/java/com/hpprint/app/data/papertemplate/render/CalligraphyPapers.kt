package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.floor

/** 书法写作类纸张 */
fun drawCalligraphy(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.CALLIGRAPHY -> calligraphy(ctx)
        PaperType.ENGLISH_CALLIGRAPHY -> englishCalligraphy(ctx)
        PaperType.BRAZILIAN_CALLIGRAPHY -> brazilianCalligraphy(ctx)
        PaperType.GERMAN_PRIMARY -> germanPrimary(ctx, bordered = false)
        PaperType.GERMAN_PRIMARY_BORDERED -> germanPrimary(ctx, bordered = true)
        PaperType.KINDER_HANDWRITING -> kinderHandwriting(ctx)
        PaperType.SLANT_GUIDE -> slantGuide(ctx)
        PaperType.SLANT_RULED -> slantRuled(ctx)
        else -> {}
    }
}

/** 书法练习纸：米字格（大方格） */
private fun calligraphy(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var top = ctx.contentTop
    while (top + s <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + s, top + s, ctx.linePaint)
            ctx.canvas.drawLine(ctx.mm(left + s / 2), ctx.mm(top), ctx.mm(left + s / 2), ctx.mm(top + s), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left), ctx.mm(top + s / 2), ctx.mm(left + s), ctx.mm(top + s / 2), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left), ctx.mm(top), ctx.mm(left + s), ctx.mm(top + s), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left + s), ctx.mm(top), ctx.mm(left), ctx.mm(top + s), ctx.thinPaint)
            left += s
        }
        top += s
    }
}

/** 英文书法纸：横线 + 斜线引导 */
private fun englishCalligraphy(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val slant = 55f // 斜线角度（度）
    val tan = kotlin.math.tan(Math.toRadians(slant.toDouble())).toFloat()
    // 横线
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
    // 斜线
    val step = s * 1.5f
    var x = ctx.contentLeft
    while (x <= ctx.contentRight + 0.01f) {
        val topX = x
        val bottomX = x + (ctx.contentBottom - ctx.contentTop) / tan
        ctx.canvas.drawLine(ctx.mm(topX), ctx.mm(ctx.contentTop), ctx.mm(bottomX), ctx.mm(ctx.contentBottom), ctx.thinPaint)
        x += step
    }
}

/** 巴西书法纸：三线组 */
private fun brazilianCalligraphy(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val rowH = s * 3f
    var top = ctx.contentTop
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        ctx.hLine(top, ctx.contentLeft, ctx.contentRight, ctx.linePaint)
        ctx.hLine(top + s, ctx.contentLeft, ctx.contentRight, ctx.thinPaint)
        ctx.hLine(top + s * 2, ctx.contentLeft, ctx.contentRight, ctx.thinPaint)
        ctx.hLine(top + s * 3, ctx.contentLeft, ctx.contentRight, ctx.linePaint)
        top += rowH + s * 1.5f
    }
}

/** 德式三线：顶线 + 中线虚线 + 底线 */
private fun germanPrimary(ctx: Ctx, bordered: Boolean) {
    val s = ctx.cfg.spacingMm
    val rowH = s * 4f
    if (bordered) ctx.rect(ctx.contentLeft, ctx.contentTop, ctx.contentRight, ctx.contentBottom, ctx.linePaint)
    var top = ctx.contentTop + if (bordered) s * 0.5f else 0f
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        ctx.hLine(top, ctx.contentLeft, ctx.contentRight, ctx.linePaint)          // 顶线
        ctx.hLine(top + rowH / 2, ctx.contentLeft, ctx.contentRight, ctx.thinPaint) // 中线
        ctx.hLine(top + rowH, ctx.contentLeft, ctx.contentRight, ctx.linePaint)     // 底线
        top += rowH + s
    }
}

/** 美式幼儿书写：顶实线 + 中虚线 + 底实线 */
private fun kinderHandwriting(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val rowH = s * 2f
    var top = ctx.contentTop
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        ctx.hLine(top, ctx.contentLeft, ctx.contentRight, ctx.linePaint)
        // 中间虚线
        val p = ctx.thinPaint
        var x = ctx.contentLeft
        while (x < ctx.contentRight) {
            ctx.canvas.drawLine(ctx.mm(x), ctx.mm(top + s), ctx.mm(x + s * 0.6f), ctx.mm(top + s), p)
            x += s * 1.1f
        }
        ctx.hLine(top + rowH, ctx.contentLeft, ctx.contentRight, ctx.linePaint)
        top += rowH + s * 1.5f
    }
}

/** 斜线引导：平行斜线 */
private fun slantGuide(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val slant = 60f
    val tan = kotlin.math.tan(Math.toRadians(slant.toDouble())).toFloat()
    val step = s / kotlin.math.sin(Math.toRadians(slant.toDouble())).toFloat()
    var x = ctx.contentLeft - (ctx.contentBottom - ctx.contentTop) / tan
    while (x <= ctx.contentRight + 0.01f) {
        val bottomX = x + (ctx.contentBottom - ctx.contentTop) / tan
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(ctx.contentTop), ctx.mm(bottomX), ctx.mm(ctx.contentBottom), ctx.linePaint)
        x += step
    }
}

/** 斜线格纸：横线 + 斜线 */
private fun slantRuled(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
    val slant = 65f
    val tan = kotlin.math.tan(Math.toRadians(slant.toDouble())).toFloat()
    val step = s * 2f / kotlin.math.sin(Math.toRadians(slant.toDouble())).toFloat()
    var x = ctx.contentLeft - (ctx.contentBottom - ctx.contentTop) / tan
    while (x <= ctx.contentRight + 0.01f) {
        val bottomX = x + (ctx.contentBottom - ctx.contentTop) / tan
        ctx.canvas.drawLine(ctx.mm(x), ctx.mm(ctx.contentTop), ctx.mm(bottomX), ctx.mm(ctx.contentBottom), ctx.thinPaint)
        x += step
    }
}
