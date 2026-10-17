package com.hpprint.app.data.render
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ImageSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import org.xmlpull.v1.XmlPullParser
import android.graphics.drawable.BitmapDrawable

// ===================== 模型 =====================

/** run 字符样式；null 字段表示继承 */
data class RunStyle(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val sizePt: Float? = null,
    val color: Int? = null,
    val highlight: Int? = null,
    val vertical: Int? = null        // 1=下标 2=上标
) {
    /** 后者覆盖前者（非空字段） */
    fun merge(o: RunStyle?): RunStyle {
        if (o == null) return this
        return RunStyle(
            bold = o.bold ?: bold,
            italic = o.italic ?: italic,
            underline = o.underline ?: underline,
            strike = o.strike ?: strike,
            sizePt = o.sizePt ?: sizePt,
            color = o.color ?: color,
            highlight = o.highlight ?: highlight,
            vertical = o.vertical ?: vertical
        )
    }
}

sealed class DocRun
data class TxtRun(val text: String, val s: RunStyle) : DocRun()
data class ImgRun(val mediaPath: String, val cx: Long, val cy: Long) : DocRun()
object PageBreakRun : DocRun()

/** 段落级样式（长度单位 twips） */
data class ParStyle(
    val align: Int? = null,          // 0左 1中 2右 3两端
    val indLeftTwips: Int? = null,
    val firstLineTwips: Int? = null,
    val hangingTwips: Int? = null,
    val beforeTwips: Int? = null,
    val afterTwips: Int? = null,
    val lineMult: Float? = null,     // lineRule=auto：line/240
    val lineExactPt: Float? = null,  // lineRule=exact/atLeast：line/20
    val run: RunStyle? = null
) {
    fun merge(o: ParStyle?): ParStyle {
        if (o == null) return this
        return ParStyle(
            align = o.align ?: align,
            indLeftTwips = o.indLeftTwips ?: indLeftTwips,
            firstLineTwips = o.firstLineTwips ?: firstLineTwips,
            hangingTwips = o.hangingTwips ?: hangingTwips,
            beforeTwips = o.beforeTwips ?: beforeTwips,
            afterTwips = o.afterTwips ?: afterTwips,
            lineMult = o.lineMult ?: lineMult,
            lineExactPt = o.lineExactPt ?: lineExactPt,
            run = (run ?: RunStyle()).merge(o.run)
        )
    }
}

data class DocPar(
    val runs: List<DocRun>,
    val style: ParStyle,
    val listPrefix: String? = null,
    val listLevel: Int = 0,
    val breakBefore: Boolean = false
)

data class DocCell(
    val blocks: List<DocBlock>,
    val gridSpan: Int,
    val vMerge: Int,                 // 0无 1restart 2continue
    val shade: Int?,
    val widthTwips: Int
)

data class DocTableRow(val cells: List<DocCell>)

data class DocTable(
    val rows: List<DocTableRow>,
    val gridCols: List<Int>,         // twips
    val borders: TableBorders,
    val cellMar: CellMargin
)

data class TableBorders(val color: Int, val widthEighthPt: Int, val none: Boolean)

data class CellMargin(val left: Int, val right: Int, val top: Int, val bottom: Int) {
    companion object { val DEFAULT = CellMargin(108, 108, 40, 40) }
}

sealed class DocBlock {
    data class P(val par: DocPar) : DocBlock()
    data class T(val table: DocTable) : DocBlock()
}

data class DocxModel(
    val blocks: List<DocBlock>,
    val pageWIn: Float,
    val pageHIn: Float,
    val marginIn: Float,
    val footnotes: List<String>,
    val header: List<DocBlock>? = null,
    val footer: List<DocBlock>? = null
)

// ===================== 样式表 =====================

private class StyleDef(val basedOn: String?, val par: ParStyle)

class DocxStyles(bytes: ByteArray?) {
    private val map = HashMap<String, StyleDef>()
    private val defaultRun: RunStyle

    init {
        var dRun = RunStyle(sizePt = 11f)
        if (bytes != null) {
            val p = Px.pull(bytes).toStart()
            p.forEachChild {
                when (p.name) {
                    "docDefaults" -> {
                        scanForTag(p, "rPr") { rpr ->
                            dRun = parseRunStyle(rpr).merge(dRun)
                        }
                        p.skip()
                    }
                    "style" -> {
                        val sid = p.attr("w:styleId")
                        var basedOn: String? = null
                        var par = ParStyle()
                        p.forEachChild {
                            when (p.name) {
                                "basedOn" -> { basedOn = p.attr("w:val"); p.skip() }
                                "pPr" -> { par = parseParStyle(p).merge(par); p.skip() }
                                "rPr" -> { par = par.copy(run = parseRunStyle(p).merge(par.run)); p.skip() }
                                else -> p.skip()
                            }
                        }
                        if (sid != null) map[sid] = StyleDef(basedOn, par)
                    }
                    else -> p.skip()
                }
            }
        }
        defaultRun = dRun
    }

    fun baseRun(): RunStyle = defaultRun

    fun parStyle(id: String?): ParStyle {
        if (id == null) return ParStyle()
        val chain = mutableListOf<String>()
        var cur: String? = id
        while (cur != null && chain.size < 20) {
            chain.add(cur)
            cur = map[cur]?.basedOn
        }
        var acc = ParStyle()
        chain.asReversed().forEach { sid -> map[sid]?.let { acc = acc.merge(it.par) } }
        return acc
    }
}

// ===================== 列表编号 =====================

private class LvlDef(
    val start: Int, val fmt: String, val lvlText: String,
    val leftTwips: Int, val hangingTwips: Int
)

class DocxNumbering(bytes: ByteArray?) {
    private val numToAbstract = HashMap<String, String>()
    private val abstracts = HashMap<String, MutableMap<Int, LvlDef>>()
    private val counters = HashMap<String, IntArray>()

    init {
        if (bytes != null) {
            val p = Px.pull(bytes).toStart()
            p.forEachChild {
                when (p.name) {
                    "num" -> {
                        val nid = p.attr("w:numId")
                        var abs: String? = null
                        p.forEachChild {
                            if (p.name == "abstractNumId") abs = p.attr("w:val")
                            p.skip()
                        }
                        if (nid != null && abs != null) numToAbstract[nid] = abs!!
                    }
                    "abstractNum" -> {
                        val aid = p.attr("w:abstractNumId")
                        val lvls = HashMap<Int, LvlDef>()
                        p.forEachChild {
                            if (p.name == "lvl") {
                                val ilvl = p.attr("w:ilvl")?.toIntOrNull() ?: 0
                                var start = 1; var fmt = "decimal"; var txt = "%1."
                                var left = ilvl * 420; var hang = 360
                                p.forEachChild {
                                    when (p.name) {
                                        "start" -> start = p.attr("w:val")?.toIntOrNull() ?: 1
                                        "numFmt" -> fmt = p.attr("w:val") ?: "decimal"
                                        "lvlText" -> txt = p.attr("w:val") ?: "%1."
                                        "ind" -> {
                                            left = p.attr("w:left")?.toIntOrNull() ?: left
                                            hang = p.attr("w:hanging")?.toIntOrNull() ?: hang
                                        }
                                    }
                                    p.skip()
                                }
                                lvls[ilvl] = LvlDef(start, fmt, txt, left, hang)
                            }
                            p.skip()
                        }
                        if (aid != null) abstracts[aid] = lvls
                    }
                    else -> p.skip()
                }
            }
        }
    }

    fun prefix(numId: String, ilvl: Int): Triple<String, Int, Int>? {
        val abs = numToAbstract[numId] ?: return null
        val lvls = abstracts[abs] ?: return null
        val lvl = lvls[ilvl] ?: lvls[0] ?: return null

        val arr = counters.getOrPut(numId) { IntArray(9) }
        if (arr[ilvl] == 0) arr[ilvl] = lvl.start
        arr[ilvl]++
        val n = arr[ilvl] - 1
        for (k in (ilvl + 1)..8) arr[k] = 0

        val token = when (lvl.fmt) {
            "decimal" -> n.toString()
            "upperLetter", "lowerLetter" -> {
                val c = 'a' + ((n - 1).coerceAtLeast(0) % 26)
                if (lvl.fmt == "upperLetter") c.uppercase() else c.toString()
            }
            "upperRoman" -> roman(n).uppercase()
            "lowerRoman" -> roman(n)
            "bullet" -> when (ilvl) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
            "none" -> ""
            else -> n.toString()
        }
        var text = lvl.lvlText
        for (k in 0..8) text = text.replace("%${k + 1}", token)
        if (lvl.fmt == "bullet") text = token
        return Triple(text, lvl.leftTwips, lvl.hangingTwips)
    }

    private fun roman(n: Int): String {
        if (n <= 0) return "i"
        var x = n; val sb = StringBuilder()
        listOf(10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i").forEach { (v, s) ->
            while (x >= v) { sb.append(s); x -= v }
        }
        return sb.toString()
    }
}

// ===================== rPr / pPr 解析 =====================

private fun boolVal(p: XmlPullParser): Boolean {
    val v = p.attr("w:val") ?: return true
    return !(v == "0" || v == "false" || v == "off")
}

/** 进入 rPr START，遍历后停在其 END_TAG，返回 RunStyle */
fun parseRunStyle(p: XmlPullParser): RunStyle {
    var s = RunStyle()
    p.forEachChild {
        when (p.name) {
            "b" -> { s = s.copy(bold = boolVal(p)); p.skip() }
            "i" -> { s = s.copy(italic = boolVal(p)); p.skip() }
            "u" -> {
                val v = p.attr("w:val")
                s = s.copy(underline = v == null || v != "none"); p.skip()
            }
            "strike", "dstrike" -> { s = s.copy(strike = boolVal(p)); p.skip() }
            "sz", "szCs" -> {
                p.attr("w:val")?.toFloatOrNull()?.let { s = s.copy(sizePt = it / 2f) }
                p.skip()
            }
            "color" -> {
                val c = OoxmlColor.hex(p.attr("w:val"))?.argb
                    ?: OoxmlColor.scheme(p.attr("w:themeColor"))?.argb
                if (c != null) s = s.copy(color = c)
                p.skip()
            }
            "highlight" -> {
                highlightColor(p.attr("w:val"))?.let { s = s.copy(highlight = it) }
                p.skip()
            }
            "vertAlign" -> {
                when (p.attr("w:val")) {
                    "subscript" -> s = s.copy(vertical = 1)
                    "superscript" -> s = s.copy(vertical = 2)
                }
                p.skip()
            }
            else -> p.skip()
        }
    }
    return s
}

fun highlightColor(name: String?): Int? = when (name) {
    "yellow" -> 0xFFFFFF00.toInt()
    "green" -> 0xFF00FF00.toInt()
    "cyan" -> 0xFF00FFFF.toInt()
    "magenta", "pink" -> 0xFFFF00FF.toInt()
    "blue" -> 0xFF0000FF.toInt()
    "red" -> 0xFFFF0000.toInt()
    "darkGray" -> 0xFFA9A9A9.toInt()
    "lightGray" -> 0xFFD3D3D3.toInt()
    "white" -> Color.WHITE
    "black" -> Color.BLACK
    else -> null
}

/** 进入 pPr START，遍历后停在其 END_TAG；不处理 pStyle/numPr（由调用方处理） */
fun parseParStyle(p: XmlPullParser): ParStyle {
    var s = ParStyle()
    p.forEachChild {
        when (p.name) {
            "jc" -> {
                s = when (p.attr("w:val")) {
                    "center" -> s.copy(align = 1)
                    "right" -> s.copy(align = 2)
                    "both", "distribute" -> s.copy(align = 3)
                    else -> s.copy(align = 0)
                }
                p.skip()
            }
            "ind" -> {
                s = s.copy(
                    indLeftTwips = (p.attr("w:left") ?: p.attr("w:start"))?.toIntOrNull()
                        ?: s.indLeftTwips,
                    firstLineTwips = p.attr("w:firstLine")?.toIntOrNull() ?: s.firstLineTwips,
                    hangingTwips = p.attr("w:hanging")?.toIntOrNull() ?: s.hangingTwips
                )
                p.skip()
            }
            "spacing" -> {
                val line = p.attr("w:line")?.toFloatOrNull()
                val rule = p.attr("w:lineRule")
                s = s.copy(
                    beforeTwips = p.attr("w:before")?.toIntOrNull() ?: s.beforeTwips,
                    afterTwips = p.attr("w:after")?.toIntOrNull() ?: s.afterTwips,
                    lineMult = if (line != null && (rule == null || rule == "auto")) line / 240f
                    else s.lineMult,
                    lineExactPt = if (line != null && rule != null && rule != "auto") line / 20f
                    else s.lineExactPt
                )
                p.skip()
            }
            "rPr" -> { s = s.copy(run = parseRunStyle(p).merge(s.run)); p.skip() }
            else -> p.skip()
        }
    }
    return s
}

/**
 * 在当前元素子树内扫描，每遇到 [tagName]（START_TAG）回调；
 * 回调内 parser 停在该元素 START，block 须自行消费；整体结束时停在当前元素 END_TAG。
 */
inline fun scanForTag(p: XmlPullParser, tagName: String, block: (XmlPullParser) -> Unit) {
    var depth = 0
    while (true) {
        when (p.next()) {
            XmlPullParser.START_TAG -> {
                if (p.name == tagName) {
                    block(p)
                    // block 约定消费整个元素并停在其 END_TAG：该 START/END 已配对，
                    // 不能再 depth++，否则深度错乱、扫描无法在容器 END 处正常结束。
                    if (p.eventType == XmlPullParser.END_TAG && p.name == tagName) {
                        // 已完整消费
                    } else {
                        depth++
                    }
                } else depth++
            }
            XmlPullParser.END_TAG -> {
                depth--
                if (depth < 0) return
            }
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

// ===================== 文档解析器 =====================

class DocxDoc(val model: DocxModel, private val pkg: OoxmlPackage) {
    fun pages(dpi: Int): DocxPages = DocxPages(this, dpi)
    fun image(mediaPath: String, reqLongEdge: Int): Bitmap? = pkg.bitmap(mediaPath, reqLongEdge)
    fun close() {}
}

class DocxParser(private val pkg: OoxmlPackage) {

    private val styles = DocxStyles(pkg.entry("word/styles.xml"))
    private val numbering = DocxNumbering(pkg.entry("word/numbering.xml"))

    companion object { private const val PART = "word/document.xml" }

    fun parse(): DocxDoc {
        val docBytes = pkg.entry(PART)
            ?: throw IllegalArgumentException("无法解析该 docx（缺少 document.xml），建议另存为 PDF")

        var pageW = 8.27f; var pageH = 11.69f; var margin = 0.75f
        var headerRid: String? = null; var footerRid: String? = null
        val blocks = mutableListOf<DocBlock>()

        val p = Px.pull(docBytes).toStart()
        p.forEachChild {
            if (p.name == "body") {
                p.forEachChild {
                    when (p.name) {
                        "p" -> parsePar(p, PART)?.let { blocks.add(DocBlock.P(it)) }
                        "tbl" -> blocks.add(DocBlock.T(parseTable(p, PART)))
                        "sectPr" -> {
                            readSect(p) { w, h, m, hr, fr ->
                                pageW = w; pageH = h; margin = m
                                headerRid = hr; footerRid = fr
                            }
                        }
                        else -> p.skip()
                    }
                }
            } else p.skip()
        }

        val footnotes = collectFootnotes()
        if (blocks.isEmpty() && footnotes.isEmpty() && headerRid == null && footerRid == null)
            throw IllegalArgumentException("该 docx 未解析到内容，建议另存为 PDF")

        val header = headerRid?.let { resolvePart(it) }?.let { parseHeaderFooter(it) }
        val footer = footerRid?.let { resolvePart(it) }?.let { parseHeaderFooter(it) }

        return DocxDoc(
            DocxModel(blocks, pageW, pageH, margin, footnotes,
                header?.takeIf { it.isNotEmpty() }, footer?.takeIf { it.isNotEmpty() }),
            pkg
        )
    }

    /** 按 document.xml 的关系 rId 解析包内部件路径 */
    private fun resolvePart(rid: String): String? {
        val rel = pkg.relsOf(PART)[rid] ?: return null
        return pkg.resolveTarget(PART, rel)
    }

    /** 解析页眉（w:hdr）/页脚（w:ftr）部件，结构同正文 body（段落/表格） */
    private fun parseHeaderFooter(part: String): List<DocBlock>? {
        val bytes = pkg.entry(part) ?: return null
        val out = mutableListOf<DocBlock>()
        val p = Px.pull(bytes).toStart()
        p.forEachChild {
            when (p.name) {
                "p" -> parsePar(p, part)?.let { out.add(DocBlock.P(it)) }
                "tbl" -> out.add(DocBlock.T(parseTable(p, part)))
                else -> p.skip()
            }
        }
        return out
    }

    /** sectPr 已在 START_TAG，读完其 END_TAG 后回调页面参数与页眉/页脚关系 rId */
    private fun readSect(p: XmlPullParser, out: (Float, Float, Float, String?, String?) -> Unit) {
        var w = 11906; var h = 16838; var ml = 1440; var mr = 1440
        var headerDefault: String? = null; var headerOther: String? = null
        var footerDefault: String? = null; var footerOther: String? = null
        p.forEachChild {
            when (p.name) {
                "pgSz" -> {
                    w = p.attr("w:w")?.toIntOrNull() ?: w
                    h = p.attr("w:h")?.toIntOrNull() ?: h
                    p.skip()
                }
                "pgMar" -> {
                    ml = p.attr("w:left")?.toIntOrNull() ?: ml
                    mr = p.attr("w:right")?.toIntOrNull() ?: mr
                    p.skip()
                }
                "headerReference" -> {
                    val rid = p.attr("r:id")
                    val t = p.attr("w:type")
                    // 无 type 属性时 Word 视为 default
                    if (t == null || t == "default") { if (rid != null) headerDefault = rid }
                    else if (headerOther == null && rid != null) headerOther = rid
                    p.skip()
                }
                "footerReference" -> {
                    val rid = p.attr("r:id")
                    val t = p.attr("w:type")
                    if (t == null || t == "default") { if (rid != null) footerDefault = rid }
                    else if (footerOther == null && rid != null) footerOther = rid
                    p.skip()
                }
                else -> p.skip()
            }
        }
        out(
            twipsToInch(w), twipsToInch(h), (twipsToInch(ml) + twipsToInch(mr)) / 2f,
            headerDefault ?: headerOther, footerDefault ?: footerOther
        )
    }

    private fun parsePar(p: XmlPullParser, part: String = PART): DocPar? {
        var styleId: String? = null
        var own = ParStyle()
        var numId: String? = null
        var ilvl = 0
        var breakBefore = false
        val runs = mutableListOf<DocRun>()

        p.forEachChild {
            when (p.name) {
                "pPr" -> {
                    p.forEachChild {
                        when (p.name) {
                            "pStyle" -> { styleId = p.attr("w:val"); p.skip() }
                            "pageBreakBefore" -> { breakBefore = true; p.skip() }
                            "numPr" -> {
                                p.forEachChild {
                                    when (p.name) {
                                        "numId" -> numId = p.attr("w:val")
                                        "ilvl" -> ilvl = p.attr("w:val")?.toIntOrNull() ?: 0
                                    }
                                    p.skip()
                                }
                            }
                            "jc", "ind", "spacing" -> {
                                // 单个元素应用到 own：临时借助 parseParStyle 不便，直接就地解析
                                own = applySingleParTag(p, own)
                            }
                            "rPr" -> { own = own.copy(run = parseRunStyle(p).merge(own.run)); p.skip() }
                            else -> p.skip()
                        }
                    }
                }
                "r" -> runs.addAll(parseRun(p, styles.baseRun(), part))
                else -> p.skip()
            }
        }

        // 样式继承：链样式 → 本段 own
        var style = styles.parStyle(styleId).merge(own)

        // 列表编号
        var prefixText: String? = null
        var listLevel = 0
        if (numId != null) {
            val pre = numbering.prefix(numId!!, ilvl)
            if (pre != null) {
                prefixText = pre.first; listLevel = ilvl
                if (style.indLeftTwips == null)
                    style = style.copy(indLeftTwips = pre.second)
                if (style.hangingTwips == null && style.firstLineTwips == null)
                    style = style.copy(hangingTwips = pre.third)
            }
        }

        if (runs.isEmpty() && style.run == null && prefixText == null) {
            // 空段落：保留为空行（Word 中的空行也是排版信息）
            return DocPar(
                listOf(TxtRun("", style.run ?: styles.baseRun())),
                style, breakBefore = breakBefore
            )
        }
        return DocPar(runs, style, prefixText, listLevel, breakBefore)
    }

    /** 处理 pPr 内单个 jc/ind/spacing 元素（START→END） */
    private fun applySingleParTag(p: XmlPullParser, s: ParStyle): ParStyle {
        var out = s
        when (p.name) {
            "jc" -> {
                out = when (p.attr("w:val")) {
                    "center" -> out.copy(align = 1)
                    "right" -> out.copy(align = 2)
                    "both", "distribute" -> out.copy(align = 3)
                    else -> out.copy(align = 0)
                }
                p.skip()
            }
            "ind" -> {
                out = out.copy(
                    indLeftTwips = (p.attr("w:left") ?: p.attr("w:start"))?.toIntOrNull()
                        ?: out.indLeftTwips,
                    firstLineTwips = p.attr("w:firstLine")?.toIntOrNull() ?: out.firstLineTwips,
                    hangingTwips = p.attr("w:hanging")?.toIntOrNull() ?: out.hangingTwips
                )
                p.skip()
            }
            "spacing" -> {
                val line = p.attr("w:line")?.toFloatOrNull()
                val rule = p.attr("w:lineRule")
                out = out.copy(
                    beforeTwips = p.attr("w:before")?.toIntOrNull() ?: out.beforeTwips,
                    afterTwips = p.attr("w:after")?.toIntOrNull() ?: out.afterTwips,
                    lineMult = if (line != null && (rule == null || rule == "auto")) line / 240f
                    else out.lineMult,
                    lineExactPt = if (line != null && rule != null && rule != "auto") line / 20f
                    else out.lineExactPt
                )
                p.skip()
            }
        }
        return out
    }

    /** w:r → 一个或多个 DocRun；inherited 为段落有效 run 样式 */
    private fun parseRun(p: XmlPullParser, inherited: RunStyle, part: String = PART): List<DocRun> {
        var style = inherited
        val result = mutableListOf<DocRun>()
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) { result.add(TxtRun(text.toString(), style)); text.setLength(0) }
        }
        p.forEachChild {
            when (p.name) {
                "rPr" -> { flushText(); style = parseRunStyle(p).merge(style); p.skip() }
                "t" -> text.append(p.textBody())
                "br" -> {
                    if (p.attr("w:type") == "page") {
                        flushText(); result.add(PageBreakRun)
                    } else text.append("\n")
                    p.skip()
                }
                "cr" -> { text.append("\n"); p.skip() }
                "tab" -> { text.append("\t"); p.skip() }
                "drawing" -> { flushText(); scanDrawing(p, part)?.let { result.add(it) } }
                "pict" -> { flushText(); scanVml(p, part)?.let { result.add(it) } }
                "AlternateContent" -> {
                    // 浮动图/文本框/SVG 等常被 mc:AlternateContent 包裹（Choice 新格式 + Fallback 旧格式）
                    flushText(); parseAlternateContent(p, part).forEach { result.add(it) }
                }
                else -> p.skip()
            }
        }
        flushText()
        return result
    }

    /** w:drawing START → ImgRun?，停在 w:drawing END */
    private fun scanDrawing(p: XmlPullParser, part: String = PART): ImgRun? {
        var embed: String? = null; var cx = 0L; var cy = 0L
        p.forEachChild {
            when (p.name) {
                "inline", "anchor" -> {
                    p.forEachChild {
                        when (p.name) {
                            "extent" -> {
                                val x = p.attr("cx")?.toLongOrNull(); val y = p.attr("cy")?.toLongOrNull()
                                if (x != null && y != null && x > 0) { cx = x; cy = y }
                                p.skip()
                            }
                            "graphic" -> {
                                scanForTag(p, "blip") { blip ->
                                    blip.attr("r:embed")?.let { embed = it }
                                }
                                p.skip()
                            }
                            else -> p.skip()
                        }
                    }
                }
                else -> p.skip()
            }
        }
        val path = embed?.let { e ->
            val rel = pkg.relsOf(part)[e] ?: return@let null
            pkg.resolveTarget(part, rel)
        } ?: return null
        return ImgRun(path, cx, cy)
    }

    /** VML w:pict：扫 imagedata r:id */
    private fun scanVml(p: XmlPullParser, part: String = PART): ImgRun? {
        var embed: String? = null
        scanForTag(p, "imagedata") { id ->
            id.attr("r:id")?.let { embed = it }
        }
        val e = embed ?: return null
        val rel = pkg.relsOf(part)[e] ?: return null
        val path = pkg.resolveTarget(part, rel) ?: return null
        return ImgRun(path, 0L, 0L)
    }

    /**
     * mc:AlternateContent START → 其中包含的图片，停在其 END_TAG。
     * 优先取 mc:Choice（DrawingML，通常是 PNG/JPG），为空再用 mc:Fallback（VML/EMF）。
     */
    private fun parseAlternateContent(p: XmlPullParser, part: String = PART): List<ImgRun> {
        var choice: List<ImgRun> = emptyList()
        var fallback: List<ImgRun> = emptyList()
        p.forEachChild {
            when (p.name) {
                "Choice" -> {
                    val l = mutableListOf<ImgRun>(); collectDrawings(p, l, part); choice = l
                }
                "Fallback" -> {
                    val l = mutableListOf<ImgRun>(); collectDrawings(p, l, part); fallback = l
                }
                else -> p.skip()
            }
        }
        return choice.ifEmpty { fallback }
    }

    /**
     * 在当前元素（如 mc:Choice / mc:Fallback）子树内深度收集所有 w:drawing / w:pict 图片。
     * 进入时停在容器 START_TAG，结束停在其 END_TAG。
     */
    private fun collectDrawings(p: XmlPullParser, sink: MutableList<ImgRun>, part: String = PART) {
        var depth = 0
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    when (p.name) {
                        "drawing" -> {
                            scanDrawing(p, part)?.let { sink.add(it) }
                            // scanDrawing 已消费到 drawing END，配对完成，不额外加深
                            if (!(p.eventType == XmlPullParser.END_TAG && p.name == "drawing")) depth++
                        }
                        "pict" -> {
                            scanVml(p, part)?.let { sink.add(it) }
                            if (!(p.eventType == XmlPullParser.END_TAG && p.name == "pict")) depth++
                        }
                        else -> depth++
                    }
                }
                XmlPullParser.END_TAG -> { depth--; if (depth < 0) return }
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    // ---------- 表格 ----------

    private fun parseTable(p: XmlPullParser, part: String = PART): DocTable {
        val gridCols = mutableListOf<Int>()
        var borders = TableBorders(0xFFBBBBBB.toInt(), 4, false)
        var cellMar = CellMargin.DEFAULT
        val rows = mutableListOf<DocTableRow>()

        p.forEachChild {
            when (p.name) {
                "tblGrid" -> {
                    p.forEachChild {
                        if (p.name == "gridCol") p.attr("w:w")?.toIntOrNull()?.let { gridCols.add(it) }
                        p.skip()
                    }
                }
                "tblPr" -> {
                    p.forEachChild {
                        when (p.name) {
                            "tblBorders" -> borders = readBorders(p, borders)
                            "tblCellMar" -> cellMar = readCellMar(p)
                            else -> p.skip()
                        }
                    }
                }
                "tr" -> rows.add(parseRow(p, part))
                else -> p.skip()
            }
        }
        if (rows.isEmpty())
            throw IllegalArgumentException("该 docx 含空表格，建议另存为 PDF")
        return DocTable(rows, gridCols, borders, cellMar)
    }

    /** tblBorders START → END */
    private fun readBorders(p: XmlPullParser, def: TableBorders): TableBorders {
        var color = def.color; var sz = def.widthEighthPt; var none = false
        p.forEachChild {
            if (p.name in setOf("top", "left", "bottom", "right", "insideH", "insideV")) {
                val v = p.attr("w:val")
                if (v == "none" || v == "nil") none = true
                p.attr("w:color")?.let { OoxmlColor.hex(it)?.argb }?.let { color = it }
                p.attr("w:sz")?.toIntOrNull()?.let { sz = it }
            }
            p.skip()
        }
        return TableBorders(color, sz, none)
    }

    private fun readCellMar(p: XmlPullParser): CellMargin {
        var l = 108; var r = 108; var t = 40; var b = 40
        p.forEachChild {
            if (p.name in setOf("top", "left", "bottom", "right")) {
                val w = p.attr("w:w")?.toIntOrNull()
                when (p.name) {
                    "left" -> l = w ?: l; "right" -> r = w ?: r
                    "top" -> t = w ?: t; "bottom" -> b = w ?: b
                }
            }
            p.skip()
        }
        return CellMargin(l, r, t, b)
    }

    private fun parseRow(p: XmlPullParser, part: String = PART): DocTableRow {
        val cells = mutableListOf<DocCell>()
        p.forEachChild {
            when (p.name) {
                "tc" -> cells.add(parseCell(p, part))
                else -> p.skip()
            }
        }
        return DocTableRow(cells)
    }

    private fun parseCell(p: XmlPullParser, part: String = PART): DocCell {
        var span = 1; var vMerge = 0; var shade: Int? = null; var width = 0
        val blocks = mutableListOf<DocBlock>()
        p.forEachChild {
            when (p.name) {
                "tcPr" -> {
                    p.forEachChild {
                        when (p.name) {
                            "gridSpan" -> { span = p.attr("w:val")?.toIntOrNull() ?: 1; p.skip() }
                            "vMerge" -> {
                                val v = p.attr("w:val")
                                vMerge = if (v == null || v == "restart") 1 else 2
                                p.skip()
                            }
                            "shd" -> {
                                shade = OoxmlColor.hex(p.attr("w:fill"))?.argb
                                    ?: OoxmlColor.scheme(p.attr("w:themeFill"))?.argb
                                p.skip()
                            }
                            "tcW" -> { width = p.attr("w:w")?.toIntOrNull() ?: 0; p.skip() }
                            else -> p.skip()
                        }
                    }
                }
                "p" -> parsePar(p, part)?.let { blocks.add(DocBlock.P(it)) }
                "tbl" -> blocks.add(DocBlock.T(parseTable(p, part)))
                else -> p.skip()
            }
        }
        return DocCell(blocks, span, vMerge, shade, width)
    }

    // ---------- 脚注 ----------

    private fun collectFootnotes(): List<String> {
        val bytes = pkg.entry("word/footnotes.xml") ?: return emptyList()
        val out = mutableListOf<String>()
        val p = Px.pull(bytes).toStart()
        scanForTag(p, "p") { ppp ->
            val sb = StringBuilder()
            scanForTag(ppp, "t") { t -> sb.append(t.textBody()) }
            if (sb.isNotBlank()) out.add(sb.toString())
        }
        return out
    }
}
