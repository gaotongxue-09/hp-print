package com.hpprint.app.data.render
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan

/**
 * docx 排版器：[DocxModel] → 页面 [PageDef]。
 * 预览（低 dpi）与打印（高 dpi）各建一个实例，排版代码完全相同。
 * 页眉/页脚先排到固定层（[StaticLayer]），正文在每页固定层之间流动。
 */
class DocxPages(
    private val doc: DocxDoc,
    override val dpi: Int
) : BaseRenderedPages(dpi) {

    private val model = doc.model
    private val imageCache = HashMap<String, Bitmap?>()
    private lateinit var flow: PageFlow

    init {
        val pageHPx = (model.pageHIn * dpi).toInt()
        val marginPx = (model.marginIn * dpi).toInt()
        val cw = (model.pageWIn * dpi).toInt() - marginPx * 2
        val layerCap = pageHPx / 4          // 页眉/页脚高度上限，防止异常文档挤压正文

        val headerLayer = composeLayer(model.header, cw, layerCap)
        val footerLayer = composeLayer(model.footer, cw, layerCap)

        flow = PageFlow(
            dpi, model.pageWIn, model.pageHIn, model.marginIn,
            headerLayer.second, footerLayer.second
        )
        flow.headerOps = headerLayer.first
        flow.footerOps = footerLayer.first

        layoutBlocks(model.blocks, flow)
        if (model.footnotes.isNotEmpty()) {
            flow.gap(dpi / 4)
            simplePar("脚注", bold = true, sizePt = 10f)
            model.footnotes.forEach { simplePar(it, sizePt = 9f) }
        }
        pageDefs.addAll(flow.finish())
    }

    /** 把页眉/页脚块排到一个不分页的固定层，返回绘制指令与实际高度。 */
    private fun composeLayer(
        blocks: List<DocBlock>?,
        widthPx: Int,
        maxH: Int
    ): Pair<List<(Canvas) -> Unit>, Int> {
        if (blocks.isNullOrEmpty()) return emptyList<(Canvas) -> Unit>() to 0
        val layer = StaticLayer(dpi, widthPx, maxH)
        layoutBlocks(blocks, layer)
        val (ops, h) = layer.result()
        return ops to h.coerceAtMost(maxH)
    }

    override fun withDpi(dpi: Int): DocumentPages = doc.pages(dpi)

    override fun close() {
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
    }

    private fun layoutBlocks(blocks: List<DocBlock>, f: FlowContext) {
        blocks.forEach { b ->
            when (b) {
                is DocBlock.P -> renderPar(b.par, f)
                is DocBlock.T -> renderTable(b.table, f)
            }
        }
    }

    private fun simplePar(text: String, bold: Boolean = false, sizePt: Float = 11f) {
        val par = DocPar(
            listOf(TxtRun(text, RunStyle(bold = bold, sizePt = sizePt))),
            ParStyle(beforeTwips = 40)
        )
        renderPar(par, flow)
    }

    // ===================== 段落 =====================

    private fun renderPar(par: DocPar, f: FlowContext) {
        if (par.breakBefore) f.pageBreak()
        val st = par.style

        val before = st.beforeTwips ?: 0
        if (before != 0) f.gap((twipsToInch(before) * dpi).toInt())

        val leftPx = ((st.indLeftTwips ?: 0).let { twipsToInch(it) * dpi }).toInt()
            .coerceAtMost(f.cw / 2)
        val hangingPx = (twipsToInch(st.hangingTwips ?: 0) * dpi).toInt()
        val firstPx = (twipsToInch(st.firstLineTwips ?: 0) * dpi).toInt()

        val contentWidth = (f.cw - leftPx).coerceAtLeast(dpi / 2)

        val xPad: (Int) -> Int = { firstDrawn ->
            when {
                firstDrawn != 0 -> leftPx
                st.hangingTwips != null -> (leftPx - hangingPx).coerceAtLeast(0)
                st.firstLineTwips != null -> leftPx + firstPx
                else -> leftPx
            }
        }

        // runs 分段：文本/小图 → StaticLayout；大图 → 独立图片块
        val textBuf = mutableListOf<DocRun>()
        fun flushText() {
            if (textBuf.isEmpty()) return
            val layout = buildLayout(par, textBuf, contentWidth, f)
            f.text(layout, xPad)
            textBuf.clear()
        }

        par.runs.forEach { r ->
            when (r) {
                is TxtRun -> textBuf.add(r)
                is ImgRun -> {
                    val img = prepareImage(r, f, f.cw)
                    if (img != null && img.h > dpi) {
                        // 块级大图
                        flushText()
                        f.image(img.bmp, img.w, img.h)
                    } else if (img != null) textBuf.add(r)
                }
                PageBreakRun -> { flushText(); f.pageBreak() }
            }
        }
        flushText()

        val after = st.afterTwips ?: 80
        if (after != 0) f.gap((twipsToInch(after) * dpi).toInt())
    }

    private data class PreparedImage(val bmp: Bitmap, val w: Int, val h: Int)

    private fun prepareImage(run: ImgRun, f: FlowContext, maxWidth: Int): PreparedImage? {
        val cached = imageCache.getOrPut(run.mediaPath) {
            // 请求尺寸未知时先按页宽解码
            doc.image(run.mediaPath, (maxWidth * 1.2f).toInt())
        }

        // 期望显示尺寸（像素）
        var w: Int; var h: Int
        if (run.cx > 0 && run.cy > 0) {
            w = (emuToInch(run.cx) * dpi).toInt()
            h = (emuToInch(run.cy) * dpi).toInt()
        } else if (cached != null) {
            // 尺寸缺失（VML 位图）：按原始像素以 96dpi 估物理尺寸，再超宽缩放
            w = (cached.width / 96f * dpi).toInt()
            h = (cached.height / 96f * dpi).toInt()
        } else {
            // 无尺寸信息且无法解码（EMF/WMF VML）：给一个 3:2 默认占位尺寸
            w = (2.2f * dpi).toInt(); h = (1.45f * dpi).toInt()
        }
        if (w > maxWidth) {
            val s = maxWidth.toFloat() / w
            w = maxWidth; h = (h * s).toInt()
        }
        if (h > f.ch) {
            val s = f.ch.toFloat() / h
            h = f.ch; w = (w * s).toInt()
        }

        // 解码失败（EMF/WMF 纯矢量、损坏图等）：生成占位图，如实保留位置与大小，
        // 而不是静默留白；EMF/WMF 内嵌位图与 SVG 已能在解码阶段还原。
        val bmp = cached ?: run {
            val label = isUnsupportedImageFormat(run.mediaPath) ?: "图片"
            val ph = makePlaceholderBitmap(w, h, dpi, label)
            imageCache[run.mediaPath] = ph
            ph
        }
        return PreparedImage(bmp, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    /** 一段连续 run → StaticLayout */
    private fun buildLayout(
        par: DocPar,
        runs: List<DocRun>,
        widthPx: Int,
        f: FlowContext
    ): StaticLayout {
        val st = par.style
        val basePt = st.run?.sizePt ?: 11f
        val basePx = basePt * dpi / 72f

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = basePx
            st.run?.color?.let { color = it }
        }

        val sb = SpannableStringBuilder()
        // 列表前缀
        par.listPrefix?.let {
            val start = sb.length
            sb.append(it).append('\t')
            sb.setSpan(
                ForegroundColorSpan(st.run?.color ?: Color.BLACK),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        runs.forEach { r ->
            when (r) {
                is TxtRun -> appendText(sb, r, basePt)
                is ImgRun -> {
                    prepareImage(r, f, widthPx)?.let { img ->
                        val d = BitmapDrawable(img.bmp).apply { setBounds(0, 0, img.w, img.h) }
                        val start = sb.length
                        sb.append("\uFFFC")
                        sb.setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM),
                            start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                PageBreakRun -> {}
            }
        }

        val alignment = when (st.align) {
            1 -> Layout.Alignment.ALIGN_CENTER
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val b = StaticLayout.Builder.obtain(sb, 0, sb.length, tp, widthPx)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)

        // 行距
        st.lineExactPt?.let { b.setLineSpacing(it * dpi / 72f, 1f) }
            ?: st.lineMult?.let { b.setLineSpacing(0f, it.coerceIn(0.5f, 4f)) }
            ?: b.setLineSpacing(0f, 1.15f)

        // 两端对齐（API26+）
        if (st.align == 3 && android.os.Build.VERSION.SDK_INT >= 26)
            b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)

        return b.build()
    }

    private fun appendText(sb: SpannableStringBuilder, r: TxtRun, basePt: Float) {
        val start = sb.length
        sb.append(r.text)
        val end = sb.length
        val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        val s = r.s

        s.bold?.let { b ->
            s.italic?.let { i ->
                sb.setSpan(StyleSpan(
                    if (b && i) Typeface.BOLD_ITALIC
                    else if (b) Typeface.BOLD else Typeface.ITALIC
                ), start, end, flag)
            } ?: sb.setSpan(StyleSpan(Typeface.BOLD), start, end, flag)
        } ?: s.italic?.let { sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, flag) }

        s.sizePt?.let { sb.setSpan(RelativeSizeSpan(it / basePt), start, end, flag) }
        s.color?.let { sb.setSpan(ForegroundColorSpan(it), start, end, flag) }
        s.highlight?.let { sb.setSpan(BackgroundColorSpan(it), start, end, flag) }
        s.underline?.let { if (it) sb.setSpan(UnderlineSpan(), start, end, flag) }
        s.strike?.let { if (it) sb.setSpan(StrikethroughSpan(), start, end, flag) }
        s.vertical?.let {
            sb.setSpan(if (it == 1) SubscriptSpan() else SuperscriptSpan(), start, end, flag)
        }
    }

    // ===================== 表格 =====================

    private fun renderTable(table: DocTable, f: FlowContext) {
        f.gap(dpi / 10)
        val cw = f.cw

        // 列宽（twips → px）
        val totalTwips = table.gridCols.sum().coerceAtLeast(1)
        var totalPx = (twipsToInch(totalTwips) * dpi).toInt()
        // 兜底：gridCols 缺失时按页宽
        if (table.gridCols.isEmpty()) totalPx = cw
        val scale = if (totalPx > cw) cw.toFloat() / totalPx else 1f

        val colWs = if (table.gridCols.isNotEmpty())
            table.gridCols.map { (twipsToInch(it) * dpi * scale).toInt().coerceAtLeast(2) }
        else List(table.rows.first().cells.size) { cw / table.rows.first().cells.size }

        val marL = (twipsToInch(table.cellMar.left) * dpi * scale).toInt()
        val marR = (twipsToInch(table.cellMar.right) * dpi * scale).toInt()
        val marT = (twipsToInch(table.cellMar.top) * dpi * scale).toInt()
        val marB = (twipsToInch(table.cellMar.bottom) * dpi * scale).toInt()

        val borderW = (table.borders.widthEighthPt / 8f * dpi / 72f * scale)
            .toInt().coerceIn(1, 6)
        val borderColor = table.borders.color
        val gridColor = 0xFFDDDDDD.toInt()

        // 列 x 起点
        val colX = IntArray(colWs.size + 1)
        for (i in colWs.indices) colX[i + 1] = colX[i] + colWs[i]

        // vMerge 活动区域：col → startY
        val activeVMerge = HashMap<Int, Int>()

        table.rows.forEach { row ->
            // 为每个单元格构建内容并测高
            data class CellDraw(
                val x: Int, val w: Int,
                val lines: List<CellLine>, val shade: Int?,
                val vMerge: Int, val colIdx: Int
            )
            val draws = mutableListOf<CellDraw>()
            var colCursor = 0
            var rowH = marT + marB + dpi / 18

            row.cells.forEach { cell ->
                val span = cell.gridSpan.coerceAtLeast(1)
                val startCol = colCursor
                val endCol = (colCursor + span).coerceAtMost(colWs.size)
                val x = colX[startCol]
                val w = colX[endCol] - colX[startCol]
                colCursor = endCol

                val innerW = (w - marL - marR).coerceAtLeast(dpi / 4)
                val lines = if (cell.vMerge == 2) emptyList()
                else buildCellLines(cell.blocks, innerW, f)
                val contentH = lines.sumOf { it.h }
                rowH = maxOf(rowH, contentH + marT + marB)

                draws.add(CellDraw(x, w, lines, cell.shade, cell.vMerge, startCol))
            }

            // 换页：行高不超整页时整体下移
            if (rowH < f.ch) f.ensure(rowH)
            val y = f.curY

            draws.forEach { d ->
                // 底色
                d.shade?.let { c ->
                    val p = Paint().apply { color = c; style = Paint.Style.FILL }
                    f.draw { cc ->
                        cc.drawRect(d.x.toFloat(), y.toFloat(),
                            (d.x + d.w).toFloat(), (y + rowH).toFloat(), p)
                    }
                }

                // 内容
                var ly = y + marT
                d.lines.forEach { line ->
                    val yy = ly
                    f.draw { cc ->
                        cc.save()
                        cc.translate((d.x + marL).toFloat(), yy.toFloat())
                        line.layout.draw(cc)
                        cc.restore()
                    }
                    ly += line.h
                }

                // vMerge 记录
                when (d.vMerge) {
                    1 -> activeVMerge[d.colIdx] = y
                    2 -> { /* 续接单元格：简化：不画该单元格上下内框 */ }
                }

                // 边框
                val useColor = if (table.borders.none) gridColor else borderColor
                val bw = if (table.borders.none) 1 else borderW
                val p = Paint().apply {
                    color = useColor; style = Paint.Style.STROKE; strokeWidth = bw.toFloat()
                }
                val inset = bw / 2f
                f.draw { cc ->
                    if (d.vMerge == 2) {
                        // 续接单元格：只画左右竖线
                        cc.drawLine(d.x.toFloat(), y.toFloat(), d.x.toFloat(),
                            (y + rowH).toFloat(), p)
                        cc.drawLine((d.x + d.w).toFloat(), y.toFloat(),
                            (d.x + d.w).toFloat(), (y + rowH).toFloat(), p)
                    } else {
                        cc.drawRect(d.x + inset, y + inset, d.x + d.w - inset,
                            y + rowH - inset, p)
                    }
                }
            }

            f.gap(rowH)
        }

        // vMerge 补外框：对 restart 列，从 startY 到当前 curY 画外矩形（覆盖内部横线）
        val endY = f.curY
        activeVMerge.forEach { (colIdx, startY) ->
            val p = Paint().apply {
                color = if (table.borders.none) gridColor else borderColor
                style = Paint.Style.STROKE
                strokeWidth = (if (table.borders.none) 1 else borderW).toFloat()
            }
            val idx = colIdx.coerceIn(0, colWs.size - 1)
            val x = colX[idx]; val w = colWs[idx]
            f.draw { cc ->
                val i = p.strokeWidth / 2f
                cc.drawRect(x + i, startY + i, x + w - i, endY - i, p)
            }
        }

        f.gap(dpi / 8)
    }

    private class CellLine(val layout: StaticLayout, val h: Int)

    /** 单元格内块 → 行布局（嵌套表格简化为文本） */
    private fun buildCellLines(blocks: List<DocBlock>, widthPx: Int, f: FlowContext): List<CellLine> {
        val out = mutableListOf<CellLine>()
        blocks.forEach { b ->
            when (b) {
                is DocBlock.P -> {
                    val par = b.par
                    val layout = buildLayout(par, par.runs, widthPx, f)
                    val padT = (par.style.beforeTwips ?: 0).let { (twipsToInch(it) * dpi).toInt() }
                    val padB = (par.style.afterTwips ?: 40).let { (twipsToInch(it) * dpi).toInt() }
                    out.add(CellLine(layout, layout.height + padT + padB))
                }
                is DocBlock.T -> {
                    // 嵌套表格：逐单元格文字拼接
                    b.table.rows.forEach { row ->
                        row.cells.forEach { cell ->
                            cell.blocks.filterIsInstance<DocBlock.P>().forEach { pb ->
                                val layout = buildLayout(pb.par, pb.par.runs, widthPx, f)
                                out.add(CellLine(layout, layout.height + dpi / 20))
                            }
                        }
                    }
                }
            }
        }
        return out
    }
}
