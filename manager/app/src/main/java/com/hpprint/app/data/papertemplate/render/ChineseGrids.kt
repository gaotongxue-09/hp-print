package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.floor

/** 中文练字类纸张 */
fun drawChinese(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.CHINESE_TIANZI -> tianzi(ctx)
        PaperType.MIZIGE -> mizige(ctx)
        PaperType.JIUGONGGE -> jiugongge(ctx)
        PaperType.HUIGONGGE -> huigongge(ctx)
        PaperType.HARDPEN_HUIGONGGE -> hardpenHuigongge(ctx)
        PaperType.CROSSGRID -> crossgrid(ctx)
        PaperType.PINYIN_TIANZI -> pinyinTianzi(ctx)
        PaperType.COMPOSITION -> composition(ctx)
        PaperType.GENKOU_YOUSHI -> genkouYoushi(ctx)
        PaperType.SEYES -> seyes(ctx)
        PaperType.CN_EN_WORD -> cnEnWord(ctx)
        PaperType.CN_PREVIEW_CARD -> cnPreviewCard(ctx)
        PaperType.ERROR_CORRECTION -> errorCorrection(ctx)
        else -> {}
    }
}

/** 田字格：方格 + 十字虚线 */
private fun tianzi(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var top = ctx.contentTop
    while (top + s <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + s, top + s, ctx.linePaint)
            // 十字虚线
            ctx.canvas.drawLine(ctx.mm(left + s / 2), ctx.mm(top), ctx.mm(left + s / 2), ctx.mm(top + s), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left), ctx.mm(top + s / 2), ctx.mm(left + s), ctx.mm(top + s / 2), ctx.thinPaint)
            left += s
        }
        top += s
    }
}

/** 米字格：方格 + 十字 + 对角虚线 */
private fun mizige(ctx: Ctx) {
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

/** 九宫格：大格（粗线）内 3×3 小格（细线） */
private fun jiugongge(ctx: Ctx) {
    val big = ctx.cfg.spacingMm
    val small = big / 3f
    var top = ctx.contentTop
    while (top + big <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + big <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + big, top + big, ctx.linePaint)
            for (i in 1..2) {
                ctx.canvas.drawLine(ctx.mm(left + small * i), ctx.mm(top), ctx.mm(left + small * i), ctx.mm(top + big), ctx.thinPaint)
                ctx.canvas.drawLine(ctx.mm(left), ctx.mm(top + small * i), ctx.mm(left + big), ctx.mm(top + small * i), ctx.thinPaint)
            }
            left += big
        }
        top += big
    }
}

/** 回宫格：外框 + 偏上内框（回字） */
private fun huigongge(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val inner = s * 0.68f
    val offset = (s - inner) * 0.35f
    var top = ctx.contentTop
    while (top + s <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + s, top + s, ctx.linePaint)
            ctx.rect(left + offset, top + offset, left + offset + inner, top + offset + inner, ctx.thinPaint)
            left += s
        }
        top += s
    }
}

/** 硬笔回宫格：长方形回宫格 */
private fun hardpenHuigongge(ctx: Ctx) {
    val h = ctx.cfg.spacingMm
    val w = h * 1.3f
    val innerW = w * 0.68f
    val innerH = h * 0.68f
    val ox = (w - innerW) / 2f
    val oy = (h - innerH) * 0.4f
    var top = ctx.contentTop
    while (top + h <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + w <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + w, top + h, ctx.linePaint)
            ctx.rect(left + ox, top + oy, left + ox + innerW, top + oy + innerH, ctx.thinPaint)
            left += w
        }
        top += h
    }
}

/** 交叉格：方格 + 对角交叉线 */
private fun crossgrid(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    var top = ctx.contentTop
    while (top + s <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + s, top + s, ctx.linePaint)
            ctx.canvas.drawLine(ctx.mm(left), ctx.mm(top), ctx.mm(left + s), ctx.mm(top + s), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left + s), ctx.mm(top), ctx.mm(left), ctx.mm(top + s), ctx.thinPaint)
            left += s
        }
        top += s
    }
}

/** 拼音田字格：每行 = 顶部拼音三线 + 下方田字格 */
private fun pinyinTianzi(ctx: Ctx) {
    val s = ctx.cfg.spacingMm          // 田字格边长
    val pyH = ctx.cfg.secondaryMm      // 拼音线高度
    val rowH = s + pyH
    var top = ctx.contentTop
    while (top + rowH <= ctx.contentBottom + 0.01f) {
        // 拼音三线（四线三格去掉首线）
        for (i in 0..2) {
            ctx.canvas.drawLine(ctx.mm(ctx.contentLeft), ctx.mm(top + pyH * i / 2f), ctx.mm(ctx.contentRight), ctx.mm(top + pyH * i / 2f), ctx.thinPaint)
        }
        // 田字格行
        val gy = top + pyH
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, gy, left + s, gy + s, ctx.linePaint)
            ctx.canvas.drawLine(ctx.mm(left + s / 2), ctx.mm(gy), ctx.mm(left + s / 2), ctx.mm(gy + s), ctx.thinPaint)
            ctx.canvas.drawLine(ctx.mm(left), ctx.mm(gy + s / 2), ctx.mm(left + s), ctx.mm(gy + s / 2), ctx.thinPaint)
            left += s
        }
        top += rowH
    }
}

/** 作文纸：边框 + 等距横线 */
private fun composition(ctx: Ctx) {
    ctx.rect(ctx.contentLeft, ctx.contentTop, ctx.contentRight, ctx.contentBottom, ctx.linePaint)
    val s = ctx.cfg.spacingMm
    var y = ctx.contentTop + s
    while (y < ctx.contentBottom) {
        ctx.hLine(y)
        y += s
    }
}

/** 原稿用纸：顶部留白 + 方格 */
private fun genkouYoushi(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val header = ctx.contentTop + s * 2f
    ctx.hLine(header, ctx.contentLeft, ctx.contentRight, ctx.accentPaint)
    var top = header + s
    while (top + s <= ctx.contentBottom + 0.01f) {
        var left = ctx.contentLeft
        while (left + s <= ctx.contentRight + 0.01f) {
            ctx.rect(left, top, left + s, top + s, ctx.thinPaint)
            left += s
        }
        top += s
    }
}

/** 法式 Seyes 格线：每组 4 条细线 + 组间粗线 + 左侧竖线 */
private fun seyes(ctx: Ctx) {
    val group = ctx.cfg.spacingMm   // 组距
    val lines = 4
    var top = ctx.contentTop
    while (top < ctx.contentBottom) {
        ctx.hLine(top, ctx.contentLeft, ctx.contentRight, ctx.linePaint) // 组基线
        for (i in 1 until lines) {
            ctx.hLine(top + group * i / lines, ctx.contentLeft, ctx.contentRight, ctx.thinPaint)
        }
        top += group
    }
    // 左侧竖线（留白提示线）
    ctx.vLine(ctx.contentLeft + ctx.mm(ctx.cfg.spacingMm), ctx.contentTop, ctx.contentBottom, ctx.thinPaint)
}

/** 汉英单词练习纸：左右两栏 */
private fun cnEnWord(ctx: Ctx) {
    val mid = (ctx.contentLeft + ctx.contentRight) / 2f
    val s = ctx.cfg.spacingMm
    // 表头
    ctx.hLine(ctx.contentTop, ctx.contentLeft, ctx.contentRight, ctx.linePaint)
    ctx.text("中文", (ctx.contentLeft + mid) / 2f, ctx.contentTop - s / 2, s * 0.7f)
    ctx.text("English", (mid + ctx.contentRight) / 2f, ctx.contentTop - s / 2, s * 0.7f)
    // 分隔线
    ctx.vLine(mid, ctx.contentTop, ctx.contentBottom, ctx.linePaint)
    // 行
    var y = ctx.contentTop + s
    while (y < ctx.contentBottom) {
        ctx.hLine(y)
        y += s
    }
}

/** 语文预习卡：标题区 + 若干书写区 */
private fun cnPreviewCard(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    // 顶部标题行
    ctx.hLine(t, l, r, ctx.linePaint)
    ctx.hLine(t + s, l, r, ctx.linePaint)
    ctx.textLeft("课文题目：", l, t + s / 2, s * 0.8f)
    // 分区
    val sections = listOf("生字新词", "主要内容", "我的疑问")
    var y = t + s * 2f
    for (sec in sections) {
        ctx.textLeft(sec, l, y + s * 0.6f, s * 0.8f)
        ctx.hLine(y + s, l, r, ctx.thinPaint)
        y += s * 4f
    }
}

/** 错题本：题目区 + 错因分析 + 正确解答 */
private fun errorCorrection(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val third = (ctx.contentBottom - t) / 3f

    val zones = listOf(
        "题目 / 错解" to t,
        "错因分析" to t + third,
        "正确解答" to t + third * 2,
    )
    for ((name, zt) in zones) {
        ctx.textLeft(name, l, zt + s, s * 0.9f)
        var y = zt + s * 2f
        while (y < zt + third - s) {
            ctx.hLine(y, l, r, ctx.thinPaint)
            y += s
        }
        if (zt > t) ctx.hLine(zt, l, r, ctx.accentPaint)
    }
}
