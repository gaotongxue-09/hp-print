package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType

/** 笔记手账类纸张 */
fun drawNote(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.CORNELL -> cornell(ctx)
        PaperType.MEETING_NOTE -> meetingNote(ctx)
        PaperType.STUDENT_NOTE -> studentNote(ctx)
        PaperType.MINIMAL_JOURNAL -> minimalJournal(ctx)
        PaperType.VINTAGE_NOTE -> vintageNote(ctx)
        PaperType.CREATIVE_LOG -> creativeLog(ctx)
        PaperType.SKETCH -> sketch(ctx)
        else -> {}
    }
}

/** 康奈尔笔记：线索栏 + 笔记区 + 总结区 */
private fun cornell(ctx: Ctx) {
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val cueW = ctx.contentWidth * 0.28f
    val summaryH = ctx.contentHeight * 0.2f
    val s = ctx.cfg.spacingMm

    // 顶部标题线
    ctx.hLine(t, l, r, ctx.linePaint)
    // 线索栏分隔线
    ctx.vLine(l + cueW, t, b, ctx.linePaint)
    // 总结区分隔线
    ctx.hLine(b - summaryH, l, r, ctx.linePaint)

    // 笔记区横线
    var y = t + s
    while (y < b - summaryH) {
        ctx.hLine(y, l + cueW, r, ctx.thinPaint)
        y += s
    }
    // 线索栏横线
    y = t + s
    while (y < b - summaryH) {
        ctx.hLine(y, l, l + cueW, ctx.thinPaint)
        y += s
    }
    // 总结区横线
    y = b - summaryH + s
    while (y < b) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
}

/** 会议笔记：标题栏 + 日期 + 横线 */
private fun meetingNote(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    // 标题区
    ctx.hLine(t, l, r, ctx.linePaint)
    ctx.hLine(t + s * 1.5f, l, r, ctx.linePaint)
    ctx.textLeft("会议主题：", l, t + s * 0.75f, s * 0.9f)
    ctx.textLeft("日期：", (l + r) / 2f, t + s * 0.75f, s * 0.9f)
    // 内容横线
    var y = t + s * 2.5f
    while (y < ctx.contentBottom) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
}

/** 学生笔记：标题栏 + 横线 */
private fun studentNote(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    ctx.hLine(t, l, r, ctx.linePaint)
    ctx.hLine(t + s * 1.4f, l, r, ctx.accentPaint)
    var y = t + s * 2.4f
    while (y < ctx.contentBottom) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
}

/** 简约手账：日期 + 横线 */
private fun minimalJournal(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    ctx.textLeft("____ / ____ / ____", l, t + s, s * 0.9f)
    var y = t + s * 2f
    while (y < ctx.contentBottom) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
}

/** 复古笔记：双层装饰边框 + 横线 */
private fun vintageNote(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    ctx.rect(l, t, r, b, ctx.linePaint)
    ctx.rect(l + 2f, t + 2f, r - 2f, b - 2f, ctx.thinPaint)
    var y = t + s
    while (y < b) {
        ctx.hLine(y, l + 2f, r - 2f, ctx.thinPaint)
        y += s
    }
}

/** 创意记录：大间距横线 */
private fun creativeLog(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop
    while (y <= ctx.contentBottom + 0.01f) {
        ctx.hLine(y)
        y += s
    }
}

/** 草图纸：细网格 */
private fun sketch(ctx: Ctx) {
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
