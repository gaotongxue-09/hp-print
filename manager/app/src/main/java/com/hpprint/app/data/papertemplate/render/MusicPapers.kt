package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType

/** 音乐乐谱类纸张 */
fun drawMusic(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.MUSIC -> staff(ctx, 5)
        PaperType.GUITAR_TAB -> staff(ctx, 6)
        PaperType.BASS_TAB -> staff(ctx, 4)
        PaperType.UKULELE -> staff(ctx, 4)
        PaperType.GUITAR_CHORD_STAFF -> chordStaff(ctx)
        else -> {}
    }
}

/** 通用 N 线谱：每组 N 条平行线，组间留白 */
private fun staff(ctx: Ctx, n: Int) {
    val gap = ctx.cfg.spacingMm            // 相邻线间距
    val systemH = gap * (n - 1)            // 一组系统高度
    val systemGap = gap * (n - 1) * 1.4f   // 组间留白
    var top = ctx.contentTop
    while (top + systemH <= ctx.contentBottom + 0.01f) {
        for (i in 0 until n) {
            ctx.hLine(top + gap * i)
        }
        top += systemH + systemGap
    }
}

/** 吉他和弦五线谱：顶部和弦方格 + 五线谱 */
private fun chordStaff(ctx: Ctx) {
    val gap = ctx.cfg.spacingMm
    val systemH = gap * 4f
    val chordBox = gap * 5f
    val systemGap = gap * 4f * 1.3f
    val left = ctx.contentLeft
    val right = ctx.contentRight

    var top = ctx.contentTop
    while (top + systemH + chordBox <= ctx.contentBottom + 0.01f) {
        // 和弦框行（每行若干小方格）
        val boxSize = chordBox
        var bx = left
        while (bx + boxSize <= right + 0.01f) {
            ctx.rect(bx, top, bx + boxSize, top + chordBox, ctx.thinPaint)
            bx += boxSize
        }
        // 五线谱
        val staffTop = top + chordBox + gap
        for (i in 0 until 5) {
            ctx.hLine(staffTop + gap * i)
        }
        top += chordBox + systemH + systemGap
    }
}
