package com.hpprint.app.data.papertemplate.render

import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.floor

/** 计划管理类纸张 */
fun drawPlanner(ctx: Ctx, type: PaperType) {
    when (type) {
        PaperType.WEEKLY_PLANNER -> weeklyPlanner(ctx)
        PaperType.DAILY_PLANNER -> dailyPlanner(ctx)
        PaperType.MONTHLY_CALENDAR -> monthlyCalendar(ctx)
        PaperType.TASK_PLANNING -> taskPlanning(ctx)
        PaperType.PROJECT_PLANNING -> projectPlanning(ctx)
        PaperType.MEAL_PLANNER -> mealPlanner(ctx)
        PaperType.HABIT_TRACKER -> habitTracker(ctx)
        PaperType.WORKOUT_LOG -> workoutLog(ctx)
        PaperType.BUDGET_TRACKER -> budgetTracker(ctx)
        PaperType.MEDICATION_TRACKER -> medicationTracker(ctx)
        PaperType.GOAL_THERMOMETER -> goalThermometer(ctx)
        PaperType.CHECKLIST -> checklist(ctx)
        PaperType.RECIPE_CARD -> recipeCard(ctx)
        else -> {}
    }
}

private val WEEK_CN = listOf("一", "二", "三", "四", "五", "六", "日")

/** 通用表格：rows 行 × cols 列，带表头 */
private fun table(ctx: Ctx, headers: List<String>?, rows: Int, cols: Int, headerH: Float, rowH: Float) {
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val colW = ctx.contentWidth / cols
    val headH = if (headers != null) headerH else 0f

    // 表头
    if (headers != null) {
        ctx.rect(l, t, r, t + headH, ctx.linePaint)
        for (c in 0 until cols) {
            val cx = l + colW * c + colW / 2
            ctx.vLine(l + colW * c, t, t + headH, ctx.linePaint)
            ctx.text(headers[c], cx, t + headH / 2, headH * 0.5f)
        }
        ctx.vLine(r, t, t + headH, ctx.linePaint)
    }

    // 行
    var y = t + headH
    for (row in 0 until rows) {
        ctx.hLine(y, l, r, ctx.linePaint)
        y += rowH
    }
    // 列竖线
    for (c in 1 until cols) {
        ctx.vLine(l + colW * c, t + headH, y - rowH, ctx.linePaint)
    }
    // 外框
    ctx.rect(l, t, r, y - rowH, ctx.linePaint)
}

/** 周计划表 */
private fun weeklyPlanner(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val headH = s * 1.4f
    val rows = floor((ctx.contentHeight - headH) / s).toInt().coerceAtLeast(5)
    table(ctx, WEEK_CN, rows, 7, headH, s)
}

/** 日计划表：时间轴 */
private fun dailyPlanner(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val timeW = ctx.contentWidth * 0.2f
    ctx.vLine(l + timeW, t, ctx.contentBottom, ctx.linePaint)
    var y = t
    var hour = 6
    while (y < ctx.contentBottom) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        ctx.textLeft("$hour:00", l + 2f, y + s * 0.4f, s * 0.6f)
        y += s
        hour++
        if (hour > 23) hour = 0
    }
}

/** 月历 */
private fun monthlyCalendar(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val headH = s * 1.5f
    val rowH = (ctx.contentHeight - headH) / 6f
    val fullWeek = listOf("日", "一", "二", "三", "四", "五", "六")
    table(ctx, fullWeek, 6, 7, headH, rowH)
}

/** 任务计划表：复选框 + 横线 */
private fun taskPlanning(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    var y = ctx.contentTop + s * 0.5f
    while (y + s < ctx.contentBottom) {
        // 复选框
        ctx.rect(l, y, l + s * 0.8f, y + s * 0.8f, ctx.linePaint)
        ctx.hLine(y + s * 0.8f, l + s * 1.4f, r, ctx.thinPaint)
        y += s * 1.6f
    }
}

/** 项目规划纸：分区标题 + 横线 */
private fun projectPlanning(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val secs = listOf("项目目标", "里程碑", "待办事项", "备注")
    var y = t
    for (sec in secs) {
        ctx.textLeft(sec, l, y + s, s * 0.9f)
        ctx.hLine(y + s * 1.6f, l, r, ctx.accentPaint)
        y += s * 1.6f
        val lines = 3
        for (i in 0 until lines) {
            y += s
            if (y >= ctx.contentBottom) break
            ctx.hLine(y, l, r, ctx.thinPaint)
        }
        y += s
    }
}

/** 膳食计划表：7 列 × 早中晚 */
private fun mealPlanner(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val headH = s * 1.5f
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val colW = ctx.contentWidth / 8f // 1 标签列 + 7 天
    // 表头
    ctx.rect(l, t, r, t + headH, ctx.linePaint)
    for (c in 0 until 8) {
        ctx.vLine(l + colW * c, t, t + headH, ctx.linePaint)
        val label = if (c == 0) "" else WEEK_CN[c - 1]
        ctx.text(label, l + colW * c + colW / 2, t + headH / 2, headH * 0.5f)
    }
    val meals = listOf("早", "午", "晚")
    var y = t + headH
    val rowH = (ctx.contentHeight - headH) / 3f
    for (i in 0 until 3) {
        ctx.hLine(y + rowH, l, r, ctx.linePaint)
        ctx.text(meals[i], l + colW / 2, y + rowH / 2, s * 0.8f)
        for (c in 1 until 8) ctx.vLine(l + colW * c, y, y + rowH, ctx.linePaint)
        y += rowH
    }
}

/** 习惯打卡表：网格 */
private fun habitTracker(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val labelW = ctx.contentWidth * 0.3f
    val days = 31
    val dayW = (ctx.contentWidth - labelW) / days
    val rows = floor((ctx.contentHeight) / s).toInt().coerceAtLeast(5)
    ctx.vLine(l + labelW, t, ctx.contentBottom, ctx.linePaint)
    for (row in 0..rows) {
        val y = t + row * s
        ctx.hLine(y, l, r, ctx.thinPaint)
    }
    for (d in 0..days) {
        val x = l + labelW + d * dayW
        ctx.vLine(x, t, ctx.contentBottom, ctx.thinPaint)
    }
}

/** 健身日志：字段 + 横线 */
private fun workoutLog(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val fields = listOf("日期", "训练部位", "动作 / 组数 / 次数", "重量", "备注")
    var y = t
    for (f in fields) {
        ctx.textLeft(f + "：", l, y + s, s * 0.8f)
        ctx.hLine(y + s * 1.4f, l, r, ctx.thinPaint)
        y += s * 2.2f
    }
    while (y < ctx.contentBottom) {
        ctx.hLine(y, l, r, ctx.thinPaint)
        y += s
    }
}

/** 家庭预算表 */
private fun budgetTracker(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val headH = s * 1.5f
    val headers = listOf("类别", "预算", "实际", "差额")
    table(ctx, headers, 14, 4, headH, s)
}

/** 用药追踪表 */
private fun medicationTracker(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val headH = s * 1.5f
    val headers = listOf("药品", "剂量", "时间", "备注")
    table(ctx, headers, 12, 4, headH, s)
}

/** 目标温度计：竖条温度计带刻度 */
private fun goalThermometer(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    val b = ctx.contentBottom
    val tubeW = ctx.contentWidth * 0.08f
    val cx = (l + r) / 2f
    val bulbR = tubeW * 2f
    val tubeTop = t + s * 2f
    val tubeBottom = b - bulbR

    // 直管
    ctx.rect(cx - tubeW / 2, tubeTop, cx + tubeW / 2, tubeBottom, ctx.linePaint)
    // 底部球
    ctx.circle(cx, tubeBottom + bulbR, bulbR, ctx.linePaint)
    // 刻度
    var y = tubeTop
    var v = 100
    while (y <= tubeBottom) {
        val major = v % 10 == 0
        val tickW = if (major) tubeW * 0.9f else tubeW * 0.5f
        ctx.canvas.drawLine(ctx.mm(cx + tubeW / 2), ctx.mm(y), ctx.mm(cx + tubeW / 2 + tickW), ctx.mm(y), ctx.linePaint)
        if (major) ctx.textLeft("$v", cx + tubeW / 2 + tickW + 1.5f, y, s * 0.5f)
        y += (tubeBottom - tubeTop) / 10f
        v -= 10
    }
}

/** 清单表：复选框列表 */
private fun checklist(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    var y = ctx.contentTop + s * 0.5f
    while (y + s < ctx.contentBottom) {
        ctx.rect(l, y, l + s * 0.8f, y + s * 0.8f, ctx.linePaint)
        ctx.hLine(y + s * 0.8f, l + s * 1.4f, r, ctx.thinPaint)
        y += s * 1.6f
    }
}

/** 菜谱卡：卡片布局 */
private fun recipeCard(ctx: Ctx) {
    val s = ctx.cfg.spacingMm
    val l = ctx.contentLeft
    val r = ctx.contentRight
    val t = ctx.contentTop
    // 卡片外框
    ctx.rect(l, t, r, ctx.contentBottom, ctx.linePaint)
    // 标题
    ctx.text("菜谱名称", (l + r) / 2f, t + s * 1.2f, s * 1.1f)
    ctx.hLine(t + s * 2f, l + s, r - s, ctx.linePaint)
    // 配料
    ctx.textLeft("配料：", l + s, t + s * 3f, s * 0.8f)
    var y = t + s * 4f
    repeat(4) {
        ctx.hLine(y, l + s, r - s, ctx.thinPaint)
        y += s
    }
    // 步骤
    ctx.textLeft("步骤：", l + s, y + s, s * 0.8f)
    y += s * 2f
    while (y < ctx.contentBottom - s) {
        ctx.hLine(y, l + s, r - s, ctx.thinPaint)
        y += s
    }
}
