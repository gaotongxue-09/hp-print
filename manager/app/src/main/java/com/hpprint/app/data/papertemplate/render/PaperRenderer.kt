package com.hpprint.app.data.papertemplate.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hpprint.app.data.papertemplate.model.BackgroundPattern
import com.hpprint.app.data.papertemplate.model.PaperConfig
import com.hpprint.app.data.papertemplate.model.PaperType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 给颜色设置 alpha（0-255），保留 RGB */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

/**
 * 绘制上下文：统一封装 Canvas + 毫米/像素换算 + Paint 复用，
 * 让屏幕预览与 PDF 导出共用同一套绘制逻辑。
 */
class Ctx(
    val canvas: Canvas,
    val cfg: PaperConfig,
    val mmScale: Float, // 每毫米对应的像素数
) {
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = cfg.lineColor
        strokeWidth = mm(cfg.lineWidthMm).coerceAtLeast(0.5f)
        strokeCap = Paint.Cap.ROUND
    }
    val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = cfg.lineColor
        strokeWidth = (mm(cfg.lineWidthMm) * 0.6f).coerceAtLeast(0.3f)
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = cfg.accentColor
        strokeWidth = mm(cfg.lineWidthMm).coerceAtLeast(0.5f)
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cfg.lineColor
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cfg.textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    val widthMm: Float get() = cfg.contentWidthMm
    val heightMm: Float get() = cfg.contentHeightMm
    val widthPx: Float get() = canvas.width.toFloat()
    val heightPx: Float get() = canvas.height.toFloat()
    val marginMm: Float get() = cfg.marginMm

    val contentLeft: Float get() = marginMm
    val contentTop: Float get() = marginMm
    val contentRight: Float get() = widthMm - marginMm
    val contentBottom: Float get() = heightMm - marginMm
    val contentWidth: Float get() = contentRight - contentLeft
    val contentHeight: Float get() = contentBottom - contentTop

    fun mm(v: Float): Float = v * mmScale
    fun px(v: Float): Float = v / mmScale

    fun fillBackground() {
        canvas.drawColor(cfg.paperColor)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint = linePaint) {
        canvas.drawLine(mm(x1), mm(y1), mm(x2), mm(y2), paint)
    }

    fun hLine(y: Float, x0: Float = contentLeft, x1: Float = contentRight, paint: Paint = linePaint) {
        canvas.drawLine(mm(x0), mm(y), mm(x1), mm(y), paint)
    }

    fun vLine(x: Float, y0: Float = contentTop, y1: Float = contentBottom, paint: Paint = linePaint) {
        canvas.drawLine(mm(x), mm(y0), mm(x), mm(y1), paint)
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, paint: Paint = linePaint) {
        canvas.drawRect(mm(l), mm(t), mm(r), mm(b), paint)
    }

    fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int = cfg.lineColor) {
        val p = fillPaint
        val old = p.color
        p.color = color
        canvas.drawRect(mm(l), mm(t), mm(r), mm(b), p)
        p.color = old
    }

    fun circle(cx: Float, cy: Float, rMm: Float, paint: Paint = linePaint) {
        canvas.drawCircle(mm(cx), mm(cy), mm(rMm), paint)
    }

    fun dot(cx: Float, cy: Float, rMm: Float, color: Int = cfg.lineColor) {
        val p = fillPaint
        val old = p.color
        p.color = color
        canvas.drawCircle(mm(cx), mm(cy), mm(rMm), p)
        p.color = old
    }

    fun path(p: Path, paint: Paint = linePaint) {
        canvas.drawPath(p, paint)
    }

    fun text(s: String, cxMm: Float, cyMm: Float, sizeMm: Float, paint: Paint = textPaint) {
        val old = paint.textSize
        val oldAlign = paint.textAlign
        paint.textSize = mm(sizeMm)
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = mm(cyMm) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(s, mm(cxMm), baseline, paint)
        paint.textSize = old
        paint.textAlign = oldAlign
    }

    fun textLeft(s: String, xMm: Float, cyMm: Float, sizeMm: Float, paint: Paint = textPaint) {
        val old = paint.textSize
        val oldAlign = paint.textAlign
        paint.textSize = mm(sizeMm)
        paint.textAlign = Paint.Align.LEFT
        val fm = paint.fontMetrics
        val baseline = mm(cyMm) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(s, mm(xMm), baseline, paint)
        paint.textSize = old
        paint.textAlign = oldAlign
    }

    fun drawMarginGuides() {
        if (!cfg.showMargin) return
        val p = thinPaint
        val old = p.color
        p.color = cfg.accentColor
        rect(contentLeft, contentTop, contentRight, contentBottom, p)
        p.color = old
    }

    /** 背景图案叠加（浅色纹理） */
    fun drawPattern() {
        val pat = cfg.pattern
        if (pat == BackgroundPattern.NONE) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cfg.lineColor, 26)
            strokeWidth = mm(0.3f).coerceAtLeast(0.4f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cfg.lineColor, 20)
            style = Paint.Style.FILL
        }
        val l = contentLeft
        val t = contentTop
        val r = contentRight
        val b = contentBottom
        val step = 5f
        val dense = 2f

        when (pat) {
            BackgroundPattern.GRID, BackgroundPattern.GRAPH -> {
                var x = l; while (x <= r) { canvas.drawLine(mm(x), mm(t), mm(x), mm(b), p); x += step }
                var y = t; while (y <= b) { canvas.drawLine(mm(l), mm(y), mm(r), mm(y), p); y += step }
            }
            BackgroundPattern.DOTS -> {
                var y = t; while (y <= b) { var x = l; while (x <= r) { canvas.drawCircle(mm(x), mm(y), mm(0.5f), fill); x += step }; y += step }
            }
            BackgroundPattern.DENSE_DOTS -> {
                var y = t; while (y <= b) { var x = l; while (x <= r) { canvas.drawCircle(mm(x), mm(y), mm(0.35f), fill); x += dense }; y += dense }
            }
            BackgroundPattern.LINES -> {
                var y = t; while (y <= b) { canvas.drawLine(mm(l), mm(y), mm(r), mm(y), p); y += step }
            }
            BackgroundPattern.VERTICAL -> {
                var x = l; while (x <= r) { canvas.drawLine(mm(x), mm(t), mm(x), mm(b), p); x += step }
            }
            BackgroundPattern.DIAGONAL, BackgroundPattern.DIAGONAL_REVERSE, BackgroundPattern.CROSSHATCH -> {
                val h = contentHeight
                var x = l - h
                while (x <= r + h) {
                    canvas.drawLine(mm(x), mm(t), mm(x + h), mm(b), p)
                    if (pat == BackgroundPattern.CROSSHATCH) {
                        canvas.drawLine(mm(x), mm(b), mm(x + h), mm(t), p)
                    }
                    x += step * 2f
                }
            }
            BackgroundPattern.CHECKER -> {
                val s = step * 1.4f
                var y = t; var row = 0
                while (y <= b) {
                    var x = l; var col = 0
                    while (x <= r) {
                        if ((row + col) % 2 == 0) {
                            canvas.drawRect(mm(x), mm(y), mm(x + s), mm(y + s), fill)
                        }
                        x += s; col++
                    }
                    y += s; row++
                }
            }
            BackgroundPattern.SOLID -> {
                canvas.drawRect(mm(l), mm(t), mm(r), mm(b), fill)
            }
            BackgroundPattern.NONE -> {}
        }
    }

    /** 水印（居中，斜排） */
    fun drawWatermark() {
        if (!cfg.watermarkEnabled || cfg.watermarkText.isBlank()) return
        canvas.save()
        val cx = mm(widthMm / 2f)
        val cy = mm(heightMm / 2f)
        canvas.rotate(-30f, cx, cy)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cfg.lineColor, 40)
            textSize = mm(34f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(cfg.watermarkText, cx, cy, p)
        canvas.restore()
    }

    /** 在指定中心按比例绘制 logo 图片 */
    fun drawLogoImage(bmp: Bitmap, centerX: Float, centerY: Float, maxW: Float, maxH: Float) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = minOf(maxW / bw, maxH / bh)
        val w = bw * scale
        val h = bh * scale
        val dst = RectF(mm(centerX - w / 2), mm(centerY - h / 2), mm(centerX + w / 2), mm(centerY + h / 2))
        canvas.drawBitmap(bmp, null, dst, null)
    }

    /** 在指定中心绘制文字 logo */
    fun drawLogoText(text: String, centerX: Float, centerY: Float, sizeMm: Float) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.accentColor
            textSize = mm(sizeMm)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val fm = tp.fontMetrics
        canvas.drawText(text, mm(centerX), mm(centerY) - (fm.ascent + fm.descent) / 2f, tp)
    }

    /** Logo（右上角标识） */
    fun drawLogo() {
        if (!cfg.logoEnabled) return
        val cx = contentRight - 10f
        val cy = contentTop + 6f
        val bmp = cfg.logoBitmap
        if (bmp != null) {
            drawLogoImage(bmp, cx, cy, 18f, 12f)
        } else {
            drawLogoText(cfg.logoText.ifBlank { "LOGO" }, cx, cy, 7f)
        }
    }
}

/**
 * 绘制引擎入口。负责填充背景、换算比例，并按纸张类型分发到具体绘制函数。
 */
object PaperRenderer {

    fun draw(canvas: Canvas, type: PaperType, config: PaperConfig) {
        val widthPx = canvas.width.toFloat()
        val heightPx = canvas.height.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return

        val mmScale = widthPx / config.contentWidthMm
        val ctx = Ctx(canvas, config, mmScale)

        ctx.fillBackground()
        ctx.drawPattern()
        ctx.drawMarginGuides()

        when (type) {
            PaperType.LINED, PaperType.VERTICAL_LINED, PaperType.GRID, PaperType.DOT,
            PaperType.BLANK, PaperType.GRID_LINED_SPLIT, PaperType.PINK_MILLIMETER_GRID ->
                drawBasic(ctx, type)

            PaperType.CHINESE_TIANZI, PaperType.MIZIGE, PaperType.JIUGONGGE,
            PaperType.HUIGONGGE, PaperType.HARDPEN_HUIGONGGE, PaperType.CROSSGRID,
            PaperType.PINYIN_TIANZI, PaperType.COMPOSITION, PaperType.GENKOU_YOUSHI,
            PaperType.SEYES, PaperType.CN_EN_WORD, PaperType.CN_PREVIEW_CARD,
            PaperType.ERROR_CORRECTION ->
                drawChinese(ctx, type)

            PaperType.MUSIC, PaperType.GUITAR_TAB, PaperType.BASS_TAB,
            PaperType.UKULELE, PaperType.GUITAR_CHORD_STAFF ->
                drawMusic(ctx, type)

            PaperType.ENGLISH_PAPER, PaperType.PINYIN_PAPER, PaperType.ARITHMETIC,
            PaperType.PRACTICE, PaperType.NUMBER_LINE, PaperType.FRACTION_BARS,
            PaperType.TEN_FRAME, PaperType.CLOCK_FACES, PaperType.BRAILLE ->
                drawEducational(ctx, type)

            PaperType.CALLIGRAPHY, PaperType.ENGLISH_CALLIGRAPHY, PaperType.BRAZILIAN_CALLIGRAPHY,
            PaperType.GERMAN_PRIMARY, PaperType.GERMAN_PRIMARY_BORDERED, PaperType.KINDER_HANDWRITING,
            PaperType.SLANT_GUIDE, PaperType.SLANT_RULED ->
                drawCalligraphy(ctx, type)

            PaperType.CORNELL, PaperType.MEETING_NOTE, PaperType.STUDENT_NOTE,
            PaperType.MINIMAL_JOURNAL, PaperType.VINTAGE_NOTE, PaperType.CREATIVE_LOG,
            PaperType.SKETCH ->
                drawNote(ctx, type)

            PaperType.WEEKLY_PLANNER, PaperType.DAILY_PLANNER, PaperType.MONTHLY_CALENDAR,
            PaperType.TASK_PLANNING, PaperType.PROJECT_PLANNING, PaperType.MEAL_PLANNER,
            PaperType.HABIT_TRACKER, PaperType.WORKOUT_LOG, PaperType.BUDGET_TRACKER,
            PaperType.MEDICATION_TRACKER, PaperType.GOAL_THERMOMETER, PaperType.CHECKLIST,
            PaperType.RECIPE_CARD ->
                drawPlanner(ctx, type)

            PaperType.ISOMETRIC, PaperType.HEXAGONAL, PaperType.POLAR_GRID,
            PaperType.LOG_LOG, PaperType.SEMI_LOG, PaperType.KNITTING_GRAPH,
            PaperType.SEWING_GRID, PaperType.PERSPECTIVE, PaperType.STORYBOARD,
            PaperType.SWOT, PaperType.CHILDREN_DRAWING ->
                drawSpecial(ctx, type)

            PaperType.BRANDED, PaperType.CUSTOM ->
                drawOther(ctx, type)
        }

        ctx.drawWatermark()
        ctx.drawLogo()
    }
}
