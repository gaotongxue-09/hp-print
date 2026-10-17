package com.hpprint.app.data.render
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan

/**
 * pptx 渲染器：每张幻灯片一页，元素按 DrawingML EMU 绝对坐标等比映射。
 */
class PptxPages(
    private val doc: PptxDoc,
    private val pkg: OoxmlPackage,
    override val dpi: Int
) : BaseRenderedPages(dpi) {

    private val pres = doc.model
    private val imageCache = HashMap<String, Bitmap?>()

    init {
        val wIn = emuToInch(pres.slideW)
        val hIn = emuToInch(pres.slideH)
        pres.slides.forEach { slide ->
            pageDefs.add(PageDef(wIn, hIn) { c -> drawSlide(c, slide) })
        }
    }

    override fun withDpi(dpi: Int): DocumentPages = PptxPages(doc, pkg, dpi)

    override fun close() {
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
    }

    private fun k(): Float = dpi / 914400f
    private fun emuPx(v: Long): Float = v * k()

    private fun drawSlide(c: Canvas, slide: PPSlide) {
        slide.bg?.let { bg ->
            c.drawRect(0f, 0f, emuPx(pres.slideW), emuPx(pres.slideH),
                Paint().apply { color = bg; style = Paint.Style.FILL })
        }
        slide.elements.forEach { drawElement(c, it) }
    }

    private fun drawElement(c: Canvas, el: PPElement) {
        when (el) {
            is PPShape -> drawShape(c, el)
            is PPPicture -> drawPicture(c, el)
            is PPTable -> drawTable(c, el)
            is PPGroup -> drawGroup(c, el)
        }
    }

    // ---------- 形状 ----------

    private fun drawShape(c: Canvas, s: PPShape) {
        val left = emuPx(s.x); val top = emuPx(s.y)
        val w = emuPx(s.cx); val h = emuPx(s.cy)

        s.fill?.let { f ->
            c.drawRect(left, top, left + w, top + h,
                Paint().apply { color = f; style = Paint.Style.FILL })
        }
        s.lineColor?.let { lc ->
            c.drawRect(left + 0.5f, top + 0.5f, left + w - 0.5f, top + h - 0.5f,
                Paint().apply {
                    color = lc; style = Paint.Style.STROKE; strokeWidth = 1.5f
                })
        }

        if (s.paragraphs.isEmpty()) return

        val pad = emuPx(91440).coerceAtLeast(4f)
        val innerW = (w - pad * 2).coerceAtLeast(dpi / 10f)
        val basePt = when (s.phType) {
            "title", "ctrTitle" -> 32f; "subTitle" -> 20f; else -> 16f
        }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = basePt * dpi / 72f }

        // 所有段落拼成一个 StaticLayout（段间用换行，对齐以段为单位 → 分段 layout）
        c.save()
        c.translate(left, top)
        c.clipRect(0f, 0f, w, h)

        var yy = pad
        s.paragraphs.forEach { pp ->
            val sb = SpannableStringBuilder()
            pp.runs.forEach { r ->
                val start = sb.length
                sb.append(r.text)
                val end = sb.length
                val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                r.sizePt?.let { sb.setSpan(RelativeSizeSpan(it / basePt), start, end, flag) }
                if (r.bold == true || r.italic == true)
                    sb.setSpan(StyleSpan(
                        when { r.bold==true && r.italic==true -> Typeface.BOLD_ITALIC
                            r.bold==true -> Typeface.BOLD; else -> Typeface.ITALIC }
                    ), start, end, flag)
                r.color?.let { sb.setSpan(ForegroundColorSpan(it), start, end, flag) }
                if (r.underline == true) sb.setSpan(UnderlineSpan(), start, end, flag)
            }

            val alignment = when (pp.align) {
                1 -> Layout.Alignment.ALIGN_CENTER
                2 -> Layout.Alignment.ALIGN_OPPOSITE
                3 -> Layout.Alignment.ALIGN_NORMAL
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = StaticLayout.Builder
                .obtain(sb, 0, sb.length, tp, innerW.toInt())
                .setAlignment(alignment)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.12f)
                .build()

            // 段落起始 x（lvl 缩进）
            val lvlPad = pp.lvl * dpi / 30f
            c.save()
            c.translate(lvlPad, 0f)
            // anchor 仅对整体处理：简化为顶对齐；单段且 anchor 中/底时偏移
            var drawY = yy
            if (s.paragraphs.size == 1) {
                if (s.anchor == 1) drawY = (h - layout.height) / 2f
                if (s.anchor == 2) drawY = h - pad - layout.height
            }
            c.translate(0f, drawY)
            layout.draw(c)
            c.restore()

            yy += layout.height + basePt * dpi / 72f * 0.35f
        }
        c.restore()
    }

    // ---------- 图片 ----------

    private fun drawPicture(c: Canvas, p: PPPicture) {
        val bmp = imageCache.getOrPut(p.mediaPath) {
            val req = maxOf(emuPx(p.cx), emuPx(p.cy)).toInt()
            pkg.bitmap(p.mediaPath, (req * 1.2f).toInt())
        } ?: return
        c.drawBitmap(bmp, null,
            Rect(emuPx(p.x).toInt(), emuPx(p.y).toInt(),
                (emuPx(p.x) + emuPx(p.cx)).toInt(),
                (emuPx(p.y) + emuPx(p.cy)).toInt()), null)
    }

    // ---------- 表格 ----------

    private fun drawTable(c: Canvas, t: PPTable) {
        val pad = emuPx(45720).coerceAtLeast(3f)
        val colWs = t.cols.map { emuPx(it) }
        val totalW = colWs.sum().coerceAtLeast(1f)

        val border = Paint().apply {
            color = 0xFFAAAAAA.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * dpi / 72f }

        var yy = emuPx(t.y)
        t.rows.forEach { row ->
            // 每单元格内容测量
            val layouts = row.cells.mapIndexed { idx, cell ->
                val cw = colWs.getOrElse(idx) { emuPx(914400) } - pad * 2
                val sb = SpannableStringBuilder()
                cell.paragraphs.forEachIndexed { pi, pp ->
                    if (pi > 0) sb.append('\n')
                    pp.runs.forEach { r ->
                        val st = sb.length
                        sb.append(r.text)
                        r.sizePt?.let {
                            sb.setSpan(RelativeSizeSpan(it / 12f), st, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        if (r.bold == true)
                            sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        r.color?.let {
                            sb.setSpan(ForegroundColorSpan(it), st, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                StaticLayout.Builder
                    .obtain(sb, 0, sb.length, tp, cw.toInt().coerceAtLeast(dpi / 12))
                    .setIncludePad(false).setLineSpacing(0f, 1.1f).build()
            }
            val contentH = layouts.maxOf { it.height }
            val rowH = maxOf(emuPx(row.h), contentH + pad * 2)

            var xx = emuPx(t.x)
            row.cells.forEachIndexed { idx, cell ->
                val cw = colWs.getOrElse(idx) { emuPx(914400) }
                cell.fill?.let { f ->
                    c.drawRect(xx, yy, xx + cw, yy + rowH,
                        Paint().apply { color = f; style = Paint.Style.FILL })
                } ?: run {
                    c.drawRect(xx, yy, xx + cw, yy + rowH,
                        Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
                }
                c.save()
                c.translate(xx + pad, yy + pad)
                c.clipRect(0f, 0f, cw - pad * 2, rowH - pad * 2)
                layouts[idx].draw(c)
                c.restore()
                c.drawRect(xx + 0.5f, yy + 0.5f, xx + cw - 0.5f, yy + rowH - 0.5f, border)
                xx += cw
            }
            yy += rowH
        }
    }

    // ---------- 组合 ----------

    private fun drawGroup(c: Canvas, g: PPGroup) {
        c.save()
        c.translate(emuPx(g.x), emuPx(g.y))
        c.scale(g.cx.toFloat() / g.chCx, g.cy.toFloat() / g.chCy)
        c.translate(-emuPx(g.chX), -emuPx(g.chY))
        g.elements.forEach { drawElement(c, it) }
        c.restore()
    }
}
