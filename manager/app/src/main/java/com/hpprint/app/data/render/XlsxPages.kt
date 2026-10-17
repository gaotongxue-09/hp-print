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
import java.util.Calendar

/**
 * xlsx 排版器：工作表网格 → A4 页面。
 * 超宽整体缩放；行方向自动分页并重复表头行；合并单元格；图片置于表后。
 */
class XlsxPages(
    private val model: XWorkbook,
    private val pkg: OoxmlPackage,
    override val dpi: Int
) : BaseRenderedPages(dpi) {

    private val flow = PageFlow(dpi, 8.27f, 11.69f, 0.35f)
    init {
        model.sheets.forEachIndexed { i, sheet ->
            if (i > 0) flow.pageBreak()
            renderSheet(sheet, model.sheets.size > 1)
        }
        pageDefs.addAll(flow.finish())
    }

    override fun withDpi(dpi: Int): DocumentPages = XlsxPages(model, pkg, dpi)
    override fun close() {}

    private fun renderSheet(sheet: XSheet, showTitle: Boolean) {
        if (showTitle) {
            headLine(sheet.name, 13f, true)
            flow.gap(dpi / 16)
        }

        // 使用范围
        val populated = sheet.rows.values.filter { it.cells.isNotEmpty() }
        if (populated.isEmpty()) {
            sheet.pictures.forEach { path ->
                pkg.bitmap(path, flow.cw)?.let { flow.image(it, flow.cw, (it.height * flow.cw / it.width)) }
            }
            return
        }

        val minR = populated.minOf { it.num }
        val maxR = populated.maxOf { it.num }
        var minC = Int.MAX_VALUE; var maxC = 0
        populated.forEach { r ->
            r.cells.keys.let { ks ->
                minC = minOf(minC, ks.min()); maxC = maxOf(maxC, ks.max())
            }
        }

        // 列宽 px（隐藏列宽 0）
        val colW = HashMap<Int, Int>()
        var totalW = 0
        for (c in minC..maxC) {
            val hidden = sheet.cols[c]?.hidden == true
            val w = if (hidden) 0
            else {
                val chars = sheet.cols[c]?.widthChars ?: 8.43f
                ((chars * 7f + 5f) * dpi / 96f).toInt()
            }
            colW[c] = w; totalW += w
        }
        val s0 = if (totalW > flow.cw) flow.cw.toFloat() / totalW else 1f

        val colX = HashMap<Int, Int>()
        var acc = 0
        for (c in minC..maxC) { colX[c] = acc; acc += (colW[c]!! * s0).toInt() }
        val rightEdge = acc

        // 行高 px ×s0
        fun rowH(r: XRow): Int {
            val pt = r.heightPt ?: 15f
            return (pt * dpi / 72f * s0).toInt().coerceAtLeast((dpi / 48f).toInt())
        }

        // 合并索引：左上 → range；非左上位置集合
        val mergeTop = HashMap<Pair<Int, Int>, XRange>()
        val mergeSkip = HashSet<Pair<Int, Int>>()
        sheet.merges.forEach { rg ->
            mergeTop[rg.r1 to rg.c1] = rg
            for (r in rg.r1..rg.r2) for (c in rg.c1..rg.c2)
                if (r != rg.r1 || c != rg.c1) mergeSkip.add(r to c)
        }

        // ---------- 分页绘制 ----------

        val headerRow = sheet.rows[minR]
        val headerH = rowH(headerRow!!)

        fun drawHeader(y: Int) {
            drawDataRow(sheet, headerRow, y, colX, colW, s0, minC, maxC,
                mergeTop, mergeSkip, isHeader = true)
        }

        // 首页表头
        flow.ensure(headerH)
        var y = flow.curY
        drawHeader(y)
        flow.gap(headerH)

        var r = minR + 1
        while (r <= maxR) {
            val row = sheet.rows[r]
            if (row == null || row.cells.isEmpty()) { r++; continue }
            val h = rowH(row)

            // 跨行合并区域：尽量不被分页切断
            val mergeHere = row.cells.keys.mapNotNull { c ->
                mergeTop[r to c]?.takeIf { it.r2 > r }
            }
            var blockH = h
            mergeHere.forEach { rg ->
                var bh = 0
                for (rr in rg.r1..rg.r2)
                    bh += sheet.rows[rr]?.let { rowH(it) } ?: (dpi / 40)
                blockH = maxOf(blockH, bh)
            }

            if (flow.remaining < h) {
                flow.pageBreak()
                flow.ensure(headerH)
                val hy = flow.curY
                drawHeader(hy)
                flow.gap(headerH)
            }
            val ry = flow.curY
            drawDataRow(sheet, row, ry, colX, colW, s0, minC, maxC,
                mergeTop, mergeSkip, isHeader = false)
            flow.gap(h)
            r++
        }

        // 表格外框（右/底边界由单元格边框覆盖，此处无需额外）

        // 图片置于表后
        if (sheet.pictures.isNotEmpty()) {
            flow.gap(dpi / 12)
            sheet.pictures.forEach { path ->
                val bmp = pkg.bitmap(path, flow.cw) ?: return@forEach
                var w = flow.cw; var h = (bmp.height.toFloat() / bmp.width * w).toInt()
                if (h > flow.ch) { h = flow.ch; w = (bmp.width.toFloat() / bmp.height * h).toInt() }
                flow.image(bmp, w, h)
            }
        }
    }

    private fun headLine(text: String, sizePt: Float, bold: Boolean) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePt * dpi / 72f
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, flow.cw)
            .setIncludePad(false).build()
        flow.text(layout)
    }

    /** 绘制一行数据（含合并单元格、底色、边框、文本） */
    private fun drawDataRow(
        sheet: XSheet,
        row: XRow,
        y: Int,
        colX: Map<Int, Int>,
        colW: Map<Int, Int>,
        scale: Float,
        minC: Int, maxC: Int,
        mergeTop: Map<Pair<Int, Int>, XRange>,
        mergeSkip: Set<Pair<Int, Int>>,
        isHeader: Boolean
    ) {
        // 先画整行浅网格背景边框（逐列）
        val gridPaint = Paint().apply {
            color = 0xFFE2E2E2.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        flow.draw { c ->
            var cx = 0
            for (col in minC..maxC) {
                val w = (colW[col]!! * scale).toInt()
                if (w > 0) c.drawRect(cx.toFloat(), y.toFloat(),
                    (cx + w).toFloat(), (y + rowHeightPx(row, scale)).toFloat(), gridPaint)
                cx += w
            }
        }

        row.cells.forEach { (col, cell) ->
            if (mergeSkip.contains(row.num to col)) return@forEach
            val x = colX[col] ?: return@forEach

            // 合并区域尺寸
            val rg = mergeTop[row.num to col]
            val w: Int; val regionH: Int
            if (rg != null) {
                val x2 = colX[rg.c2]!! + (colW[rg.c2]!! * scale).toInt()
                w = x2 - x
                var hh = 0
                for (rr in rg.r1..rg.r2)
                    hh += sheet.rows[rr]?.let { rowHeightPx(it, scale) } ?: (dpi / 40)
                regionH = hh
            } else {
                w = (colW[col]!! * scale).toInt()
                regionH = rowHeightPx(row, scale)
            }

            val st = cell.style
            // 底色
            val fill = st?.fill
            if (fill != null) {
                val p = Paint().apply { color = fill; style = Paint.Style.FILL }
                flow.draw { c ->
                    c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(),
                        (y + regionH).toFloat(), p)
                }
            }

            // 显式边框
            if (st?.border == true) {
                val p = Paint().apply {
                    color = 0xFF555555.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f
                }
                flow.draw { c ->
                    c.drawRect(x + 1f, y + 1f, x + w - 1f, y + regionH - 1f, p)
                }
            }

            // 文本
            val sizePt = st?.sizePt ?: 11f
            val textPx = (sizePt * dpi / 72f * scale).coerceAtLeast(dpi / 30f)
            val pad = (dpi / 60f * scale).toInt().coerceAtLeast(2)
            val innerW = (w - pad * 2).coerceAtLeast(dpi / 8)

            val align = when {
                st?.hAlign != null && st.hAlign != 0 -> st.hAlign
                cell.numValue != null -> 3
                else -> 1
            }

            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = textPx
                st?.color?.let { color = it }
                if (st?.bold == true) typeface =
                    if (st.italic) Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                    else Typeface.DEFAULT_BOLD
                else if (st?.italic == true)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }

            // 富文本或纯文本
            val sb = SpannableStringBuilder()
            val rich = cell.rich
            if (rich != null) {
                rich.forEach { rr ->
                    val start = sb.length
                    sb.append(rr.text)
                    if (rr.bold == true || rr.italic == true)
                        sb.setSpan(StyleSpan(
                            when { rr.bold==true && rr.italic==true -> Typeface.BOLD_ITALIC
                                rr.bold==true -> Typeface.BOLD; else -> Typeface.ITALIC }
                        ), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    rr.color?.let { c ->
                        sb.setSpan(ForegroundColorSpan(c), start, sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    rr.sizePt?.let { sp ->
                        sb.setSpan(RelativeSizeSpan(sp / sizePt), start, sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            } else {
                val txt = when {
                    cell.numValue != null -> XNumber.format(cell.numValue, st)
                    else -> cell.text ?: ""
                }
                sb.append(txt)
            }

            val wrap = st?.wrap == true
            val layoutW = if (wrap) innerW else
                innerW.coerceAtLeast((sb.length * textPx * 0.6f).toInt())
            val alignment = when (align) {
                2 -> Layout.Alignment.ALIGN_CENTER
                3 -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = StaticLayout.Builder
                .obtain(sb, 0, sb.length, tp, layoutW)
                .setAlignment(alignment)
                .setIncludePad(false)
                .apply { if (!wrap) setMaxLines(1) }
                .setLineSpacing(0f, 1.1f)
                .build()

            // 垂直：顶对齐（wrap 时自然；单行近似居中）
            val textY = if (!wrap && layout.height < regionH)
                y + (regionH - layout.height) / 2 else y + pad
            flow.draw { c ->
                c.save()
                c.translate((x + pad).toFloat(), textY.toFloat())
                c.clipRect(0f, -pad.toFloat(), innerW.toFloat(),
                    (regionH + pad).toFloat())
                layout.draw(c)
                c.restore()
            }
        }
    }

    private fun rowHeightPx(row: XRow, scale: Float): Int {
        val pt = row.heightPt ?: 15f
        return (pt * dpi / 72f * scale).toInt().coerceAtLeast((dpi / 48f).toInt())
    }
}

// ===================== 数字 / 日期格式化 =====================

object XNumber {

    private val DATE_IDS = setOf(
        14, 15, 16, 17, 18, 19, 20, 21, 22,
        27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        45, 46, 47, 50, 51, 52, 53, 54, 55, 56, 57, 58
    )

    fun format(v: Double, style: XCellStyle?): String {
        val id = style?.numFmtId ?: 0
        val rawCode = style?.numFmtCode
        val code = rawCode?.substringBefore(';')?.trim()

        if (DATE_IDS.contains(id) || (code != null && isDateCode(code)))
            return date(v, id, code)

        if (code?.contains('%') == true || id == 9 || id == 10) {
            val dec = code?.let { decimals(it) }?.let { if (it < 0) 0 else it }
                ?: if (id == 9) 0 else 2
            return roundStr(v * 100, dec) + "%"
        }

        val grouped = code?.contains(',') == true
        val sym = code?.let { currencySymbol(it) }
        val dec0 = code?.let { decimals(it) } ?: -1

        if (sym != null || grouped || id in setOf(37, 38, 39, 40) || id in 164..181) {
            val dec = when {
                dec0 >= 0 -> dec0
                id in setOf(37, 39, 40) -> 0
                else -> 2
            }
            val num = groupAndRound(v, dec)
            val s = sym ?: if (id in 164..181) "¥" else ""
            return s + num
        }

        if (dec0 >= 0) return roundStr(v, dec0)

        return if (v == v.toLong().toDouble())
            groupAndRound(v, 0)
        else roundStr(v, 6).trimEnd('0').trimEnd('.')
    }

    fun isDateCode(code: String): Boolean {
        val lower = code.lowercase()
        if (lower.contains("#") || lower.contains("0.0") || lower.contains("%")) return false
        return (lower.contains("yy") || lower.contains("d") || lower.contains("h") ||
            lower.contains("m") || lower.contains("s"))
    }

    private fun decimals(code: String): Int {
        val m = Regex("0+\\.(0+)").find(code) ?: return -1
        return m.groupValues[1].length
    }

    private fun currencySymbol(code: String): String? = when {
        code.contains('¥') || code.contains('￥') -> "¥"
        code.contains('$') -> "$"
        code.contains('€') -> "€"
        code.contains('£') -> "£"
        else -> null
    }

    fun roundStr(v: Double, dec: Int): String {
        val factor = Math.pow(10.0, dec.toDouble())
        val rounded = Math.round(v * factor).toDouble() / factor
        if (dec == 0) return rounded.toLong().toString()
        val parts = rounded.toString().split('.')
        val frac = parts.getOrElse(1) { "" }
        return parts[0] + "." + frac.padEnd(dec, '0')
    }

    fun groupAndRound(v: Double, dec: Int): String {
        val neg = v < 0
        val rounded = roundStr(if (neg) -v else v, dec)
        val parts = rounded.split('.')
        val intPart = parts[0]
        val sb = StringBuilder()
        intPart.forEachIndexed { i, ch ->
            if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        var out = sb.toString()
        if (parts.size > 1) out += "." + parts[1]
        return (if (neg) "-" else "") + out
    }

    // ---------- 日期 ----------

    private fun date(v: Double, id: Int, code: String?): String {
        var days = Math.floor(v).toLong()
        var frac = v - days
        if (frac < 0) { frac += 1.0; days -= 1 }

        val cal = Calendar.getInstance().apply {
            clear()
            set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            add(Calendar.DAY_OF_YEAR, days.toInt())
            val totalSec = Math.round(frac * 86400.0).toInt()
            set(Calendar.HOUR_OF_DAY, totalSec / 3600)
            set(Calendar.MINUTE, (totalSec / 60) % 60)
            set(Calendar.SECOND, totalSec % 60)
        }

        val effectiveCode = code ?: defaultDateCode(id)
        return renderDate(cal, effectiveCode, id)
    }

    private fun defaultDateCode(id: Int): String = when (id) {
        14, 15 -> "yyyy/m/d"
        16 -> "yyyy/m"
        17 -> "yyyy/m"
        18, 19 -> "h:mm AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        else -> "yyyy/m/d h:mm"
    }

    private fun renderDate(cal: Calendar, code: String, id: Int): String {
        val lower = code.lowercase()
        val hasDate = lower.contains('y') || lower.contains('d') ||
            (lower.contains('m') && !lower.contains('h'))
        val hasTime = lower.contains('h') || lower.contains('s') ||
            (lower.contains('m') && lower.contains('h'))

        val sb = StringBuilder()
        var i = 0
        var minuteConsumed = false
        while (i < code.length) {
            val c = code[i]
            val cl = c.lowercaseChar()
            when (cl) {
                'y' -> {
                    var n = 1
                    while (i + n < code.length && code[i + n].lowercaseChar() == 'y') n++
                    val y = cal.get(Calendar.YEAR)
                    sb.append(if (n >= 4) y else (y % 100).toString().padStart(2, '0'))
                    i += n
                }
                'm' -> {
                    var n = 1
                    while (i + n < code.length && code[i + n].lowercaseChar() == 'm') n++
                    // h 之后的 m 为分钟
                    val before = code.substring(0, i).lowercase()
                    val isMinute = before.contains('h') || before.contains("am")
                    if (isMinute) {
                        val m = cal.get(Calendar.MINUTE)
                        sb.append(if (n >= 2) m.toString().padStart(2, '0') else m)
                        minuteConsumed = true
                    } else {
                        val m = cal.get(Calendar.MONTH) + 1
                        sb.append(if (n >= 2) m.toString().padStart(2, '0') else m)
                    }
                    i += n
                }
                'd' -> {
                    var n = 1
                    while (i + n < code.length && code[i + n].lowercaseChar() == 'd') n++
                    val d = cal.get(Calendar.DAY_OF_MONTH)
                    sb.append(if (n >= 2) d.toString().padStart(2, '0') else d)
                    i += n
                }
                'h' -> {
                    var n = 1
                    while (i + n < code.length && code[i + n].lowercaseChar() == 'h') n++
                    var h = cal.get(Calendar.HOUR_OF_DAY)
                    val ampm = lower.contains("am/pm") || lower.contains("a/p")
                    if (ampm) h = if (h % 12 == 0) 12 else h % 12
                    sb.append(if (n >= 2) h.toString().padStart(2, '0') else h)
                    i += n
                }
                's' -> {
                    var n = 1
                    while (i + n < code.length && code[i + n].lowercaseChar() == 's') n++
                    val s = cal.get(Calendar.SECOND)
                    sb.append(if (n >= 2) s.toString().padStart(2, '0') else s)
                    i += n
                }
                'a' -> {
                    if (i + 6 < code.length &&
                        code.substring(i, i + 6).equals("AM/PM", ignoreCase = true)) {
                        sb.append(if (cal.get(Calendar.HOUR_OF_DAY) < 12) "上午" else "下午")
                        i += 5
                    } else { sb.append(c); i++ }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
