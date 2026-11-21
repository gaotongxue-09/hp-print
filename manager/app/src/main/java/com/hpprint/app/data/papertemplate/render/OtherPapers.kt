package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType

/** 其他类纸张 */
fun drawOther(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.BRANDED -> branded(ctx)
        PaperType.CUSTOM -> custom(ctx)
        else -> {}
    }
}

/** 品牌定制纸：顶部品牌区（自定义商标）+ 边框 + 横线 */
private fun branded(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    ctx.rect(l, t, r, b, ctx.linePaint)

    // 顶部品牌区：自定义商标（图片优先，否则文字）
    val brandH = s * 2.5f
    ctx.hLine(t + brandH, l, r, ctx.accentPaint)
    val bmp = ctx.cfg.logoBitmap
    if (bmp != null) {
        ctx.drawLogoImage(bmp, (l + r) / 2f, t + brandH / 2f, ctx.contentWidth * 0.6f, brandH * 0.7f)
    } else {
        ctx.drawLogoText(ctx.cfg.logoText.ifBlank { "LOGO" }, (l + r) / 2f, t + brandH / 2f, s * 1.1f)
    }

    // 横线
    var y = t + brandH + s
    while (y < b) {
        ctx.hLine(y, l + s * 0.5f, r - s * 0.5f, ctx.thinPaint)
        y += s
    }
}

/** 自定义纸：通用横线 */
private fun custom(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
}
