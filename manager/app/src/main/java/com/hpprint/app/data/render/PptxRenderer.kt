package com.hpprint.app.data.render
import org.xmlpull.v1.XmlPullParser

// ===================== 模型 =====================

data class PPRun(
    val text: String,
    val sizePt: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val color: Int? = null
)

data class PPPar(val runs: List<PPRun>, val align: Int, val lvl: Int)  // align 0左1中2右3两端

sealed class PPElement

data class PPShape(
    val x: Long, val y: Long, val cx: Long, val cy: Long,
    val paragraphs: List<PPPar>,
    val fill: Int?,
    val lineColor: Int?,
    val phType: String?,
    val anchor: Int                    // 0顶 1中 2底
) : PPElement()

data class PPPicture(
    val x: Long, val y: Long, val cx: Long, val cy: Long,
    val mediaPath: String
) : PPElement()

data class PPTable(
    val x: Long, val y: Long,
    val cols: List<Long>,
    val rows: List<PPTableRow>
) : PPElement()

data class PPTableRow(val h: Long, val cells: List<PPTableCell>)

data class PPTableCell(val paragraphs: List<PPPar>, val fill: Int?)

data class PPGroup(
    val x: Long, val y: Long, val cx: Long, val cy: Long,
    val chX: Long, val chY: Long, val chCx: Long, val chCy: Long,
    val elements: List<PPElement>
) : PPElement()

data class PPSlide(val part: String, val bg: Int?, val elements: List<PPElement>)

data class PPresentation(
    val slideW: Long, val slideH: Long,
    val slides: List<PPSlide>
)

class PptxDoc(val model: PPresentation, private val pkg: OoxmlPackage) {
    fun pages(dpi: Int): PptxPages = PptxPages(this, pkg, dpi)
    fun close() {}
}

// ===================== 解析 =====================

class PptxParser(private val pkg: OoxmlPackage) {

    companion object { private const val PRES = "ppt/presentation.xml" }

    fun parse(): PptxDoc {
        val presBytes = pkg.entry(PRES)
            ?: throw IllegalArgumentException("无法解析该 pptx（缺少 presentation.xml），建议另存为 PDF")

        var slideW = 12192000L; var slideH = 6858000L
        val slideRefs = mutableListOf<String>()   // r:id 顺序

        val p = Px.pull(presBytes).toStart()
        p.forEachChild {
            when (p.name) {
                "sldSz" -> {
                    slideW = p.attr("cx")?.toLongOrNull() ?: slideW
                    slideH = p.attr("cy")?.toLongOrNull() ?: slideH
                    p.skip()
                }
                "sldIdLst" -> {
                    p.forEachChild {
                        if (p.name == "sldId") p.attr("r:id")?.let { slideRefs.add(it) }
                        p.skip()
                    }
                }
                else -> p.skip()
            }
        }

        val rels = pkg.relsOf(PRES)
        val slides = slideRefs.mapNotNull { rid ->
            val rel = rels[rid] ?: return@mapNotNull null
            val part = pkg.resolveTarget(PRES, rel) ?: return@mapNotNull null
            parseSlide(part)
        }
        if (slides.isEmpty())
            throw IllegalArgumentException("该 pptx 未找到幻灯片，建议另存为 PDF")

        return PptxDoc(PPresentation(slideW, slideH, slides), pkg)
    }

    private fun parseSlide(part: String): PPSlide {
        val bytes = pkg.entry(part)
            ?: throw IllegalArgumentException("无法读取幻灯片部件")
        val p = Px.pull(bytes).toStart()
        var bg: Int? = null
        val elements = mutableListOf<PPElement>()

        p.forEachChild {
            if (p.name == "cSld") {
                p.forEachChild {
                    when (p.name) {
                        "bg" -> { bg = readBg(p); }
                        "spTree" -> parseSpTree(p, part, elements)
                        else -> p.skip()
                    }
                }
            } else p.skip()
        }
        return PPSlide(part, bg, elements)
    }

    private fun readBg(p: XmlPullParser): Int? {
        var c: Int? = null
        p.forEachChild {
            when (p.name) {
                "bgPr" -> {
                    p.forEachChild {
                        if (p.name == "solidFill") c = readSolidFill(p)
                        p.skip()
                    }
                }
                "bgRef" -> {
                    p.forEachChild {
                        if (p.name == "schemeClr" || p.name == "srgbClr")
                            c = readColorEl(p)
                        p.skip()
                    }
                }
                else -> p.skip()
            }
        }
        return c
    }

    private fun parseSpTree(
        p: XmlPullParser,
        part: String,
        out: MutableList<PPElement>
    ) {
        p.forEachChild {
            when (p.name) {
                "sp" -> parseShape(p)?.let { out.add(it) }
                "pic" -> parsePic(p, part)?.let { out.add(it) }
                "grpSp" -> parseGroup(p, part)?.let { out.add(it) }
                "graphicFrame" -> parseGraphicFrame(p)?.let { out.add(it) }
                "cxnSp" -> { /* 连接线，简化忽略 */ p.skip() }
                else -> p.skip()
            }
        }
    }

    // ---------- 形状 ----------

    private fun parseShape(p: XmlPullParser): PPShape? {
        var x = 0L; var y = 0L; var cx = 0L; var cy = 0L
        var fill: Int? = null; var line: Int? = null
        var phType: String? = null
        var anchor = 0
        val paragraphs = mutableListOf<PPPar>()

        p.forEachChild {
            when (p.name) {
                "nvSpPr" -> {
                    scanForTag(p, "ph") { ph ->
                        phType = ph.attr("type") ?: "body"
                    }
                    p.skip()
                }
                "spPr" -> {
                    p.forEachChild {
                        when (p.name) {
                            "xfrm" -> {
                                p.forEachChild {
                                    when (p.name) {
                                        "off" -> {
                                            x = p.attr("x")?.toLongOrNull() ?: x
                                            y = p.attr("y")?.toLongOrNull() ?: y
                                        }
                                        "ext" -> {
                                            cx = p.attr("cx")?.toLongOrNull() ?: cx
                                            cy = p.attr("cy")?.toLongOrNull() ?: cy
                                        }
                                    }
                                    p.skip()
                                }
                            }
                            "solidFill" -> fill = readSolidFill(p)
                            "ln" -> {
                                p.forEachChild {
                                    if (p.name == "solidFill") line = readSolidFill(p)
                                    p.skip()
                                }
                                if (line == null) line = 0xFF666666.toInt()
                            }
                            "noFill" -> { fill = null; p.skip() }
                            else -> p.skip()
                        }
                    }
                }
                "txBody" -> {
                    p.forEachChild {
                        when (p.name) {
                            "bodyPr" -> {
                                anchor = when (p.attr("anchor")) {
                                    "ctr" -> 1; "b" -> 2; else -> 0
                                }
                                p.skip()
                            }
                            "p" -> paragraphs.add(readParagraph(p, phType))
                            else -> p.skip()
                        }
                    }
                }
                else -> p.skip()
            }
        }

        if (paragraphs.isEmpty() && fill == null) return null
        return PPShape(x, y, cx.coerceAtLeast(1), cy.coerceAtLeast(1),
            paragraphs, fill, line, phType, anchor)
    }

    /** a:p → PPPar；phType 用于缺省字号 */
    private fun readParagraph(p: XmlPullParser, phType: String?): PPPar {
        var align = 0; var lvl = 0
        val runs = mutableListOf<PPRun>()
        p.forEachChild {
            when (p.name) {
                "pPr" -> {
                    align = when (p.attr("algn")) {
                        "ctr" -> 1; "r" -> 2; "just" -> 3; else -> 0
                    }
                    lvl = p.attr("lvl")?.toIntOrNull() ?: 0
                    p.skip()
                }
                "r" -> runs.add(readRun(p))
                "br" -> { runs.add(PPRun("\n")); p.skip() }
                "fld" -> {
                    // 字段：取 t 元素
                    var txt = ""
                    p.forEachChild {
                        if (p.name == "t") txt = p.textBody()
                        p.skip()
                    }
                    runs.add(PPRun(txt))
                }
                "endParaRPr" -> p.skip()
                else -> p.skip()
            }
        }
        if (runs.isEmpty()) runs.add(PPRun("", sizePt = defaultSize(phType)))
        return PPPar(runs, align, lvl)
    }

    private fun readRun(p: XmlPullParser): PPRun {
        var size: Float? = null; var bold: Boolean? = null
        var italic: Boolean? = null; var underline: Boolean? = null
        var color: Int? = null
        var text = ""
        p.forEachChild {
            when (p.name) {
                "rPr" -> {
                    p.attr("sz")?.toFloatOrNull()?.let { size = it / 100f }
                    p.attr("b")?.let { bold = it != "0" }
                    p.attr("i")?.let { italic = it != "0" }
                    p.attr("u")?.let { underline = it != "none" }
                    p.forEachChild {
                        if (p.name == "solidFill") color = readSolidFill(p)
                        p.skip()
                    }
                    p.skip()
                }
                "t" -> text = p.textBody()
                else -> p.skip()
            }
        }
        return PPRun(text, size, bold, italic, underline, color)
    }

    private fun defaultSize(phType: String?): Float = when (phType) {
        "title", "ctrTitle" -> 32f
        "subTitle" -> 20f
        else -> 16f
    }

    // ---------- 图片 ----------

    private fun parsePic(p: XmlPullParser, part: String): PPPicture? {
        var x = 0L; var y = 0L; var cx = 0L; var cy = 0L
        var embed: String? = null
        p.forEachChild {
            when (p.name) {
                "blipFill" -> {
                    p.forEachChild {
                        if (p.name == "blip") embed = p.attr("r:embed")
                        p.skip()
                    }
                }
                "spPr" -> {
                    p.forEachChild {
                        if (p.name == "xfrm") {
                            p.forEachChild {
                                when (p.name) {
                                    "off" -> {
                                        x = p.attr("x")?.toLongOrNull() ?: x
                                        y = p.attr("y")?.toLongOrNull() ?: y
                                    }
                                    "ext" -> {
                                        cx = p.attr("cx")?.toLongOrNull() ?: cx
                                        cy = p.attr("cy")?.toLongOrNull() ?: cy
                                    }
                                }
                                p.skip()
                            }
                        } else p.skip()
                    }
                }
                else -> p.skip()
            }
        }
        val e = embed ?: return null
        val rel = pkg.relsOf(part)[e] ?: return null
        val mediaPath = pkg.resolveTarget(part, rel) ?: return null
        return PPPicture(x, y, cx.coerceAtLeast(1), cy.coerceAtLeast(1), mediaPath)
    }

    // ---------- 组合 ----------

    private fun parseGroup(p: XmlPullParser, part: String): PPGroup? {
        var x = 0L; var y = 0L; var cx = 0L; var cy = 0L
        var chX = 0L; var chY = 0L; var chCx = 1L; var chCy = 1L
        val elements = mutableListOf<PPElement>()
        p.forEachChild {
            when (p.name) {
                "grpSpPr" -> {
                    scanForTag(p, "xfrm") { xp ->
                        xp.forEachChild {
                            when (p.name) {
                                "off" -> {
                                    x = p.attr("x")?.toLongOrNull() ?: x
                                    y = p.attr("y")?.toLongOrNull() ?: y
                                }
                                "ext" -> {
                                    cx = p.attr("cx")?.toLongOrNull() ?: cx
                                    cy = p.attr("cy")?.toLongOrNull() ?: cy
                                }
                                "chOff" -> {
                                    chX = p.attr("x")?.toLongOrNull() ?: chX
                                    chY = p.attr("y")?.toLongOrNull() ?: chY
                                }
                                "chExt" -> {
                                    chCx = p.attr("cx")?.toLongOrNull() ?: chCx
                                    chCy = p.attr("cy")?.toLongOrNull() ?: chCy
                                }
                            }
                            p.skip()
                        }
                    }
                    p.skip()
                }
                "sp" -> parseShape(p)?.let { elements.add(it) }
                "pic" -> parsePic(p, part)?.let { elements.add(it) }
                "grpSp" -> parseGroup(p, part)?.let { elements.add(it) }
                "graphicFrame" -> parseGraphicFrame(p)?.let { elements.add(it) }
                else -> p.skip()
            }
        }
        if (elements.isEmpty()) return null
        return PPGroup(x, y, cx.coerceAtLeast(1), cy.coerceAtLeast(1),
            chX, chY, chCx.coerceAtLeast(1), chCy.coerceAtLeast(1), elements)
    }

    // ---------- 表格（graphicFrame） ----------

    private fun parseGraphicFrame(p: XmlPullParser): PPTable? {
        var x = 0L; var y = 0L
        var tbl: PPTable? = null
        p.forEachChild {
            when (p.name) {
                "xfrm" -> {
                    p.forEachChild {
                        if (p.name == "off") {
                            x = p.attr("x")?.toLongOrNull() ?: x
                            y = p.attr("y")?.toLongOrNull() ?: y
                        }
                        p.skip()
                    }
                }
                "graphic" -> {
                    scanForTag(p, "tbl") { tp -> tbl = readTable(tp) }
                    p.skip()
                }
                else -> p.skip()
            }
        }
        return tbl?.let { PPTable(x, y, it.cols, it.rows) }
    }

    private fun readTable(p: XmlPullParser): PPTable {
        val cols = mutableListOf<Long>()
        val rows = mutableListOf<PPTableRow>()
        p.forEachChild {
            when (p.name) {
                "tblGrid" -> {
                    p.forEachChild {
                        if (p.name == "gridCol")
                            p.attr("w")?.toLongOrNull()?.let { cols.add(it) }
                        p.skip()
                    }
                }
                "tr" -> {
                    var h = p.attr("h")?.toLongOrNull() ?: 400000L
                    val cells = mutableListOf<PPTableCell>()
                    p.forEachChild {
                        when (p.name) {
                            "tc" -> cells.add(readTableCell(p))
                            else -> p.skip()
                        }
                    }
                    rows.add(PPTableRow(h, cells))
                }
                else -> p.skip()
            }
        }
        if (cols.isEmpty() && rows.isNotEmpty())
            repeat(rows[0].cells.size) { cols.add(914400L) }
        return PPTable(0L, 0L, cols, rows)
    }

    private fun readTableCell(p: XmlPullParser): PPTableCell {
        var fill: Int? = null
        val paragraphs = mutableListOf<PPPar>()
        p.forEachChild {
            when (p.name) {
                "tcPr" -> {
                    p.forEachChild {
                        when (p.name) {
                            "solidFill" -> fill = readSolidFill(p)
                            "noFill" -> { fill = null; p.skip() }
                            else -> p.skip()
                        }
                    }
                }
                "txBody" -> {
                    p.forEachChild {
                        if (p.name == "p") paragraphs.add(readParagraph(p, "body"))
                        p.skip()
                    }
                }
                else -> p.skip()
            }
        }
        return PPTableCell(paragraphs, fill)
    }

    // ---------- 颜色 ----------

    /** solidFill START → 颜色，停在 solidFill END */
    private fun readSolidFill(p: XmlPullParser): Int? {
        var c: Int? = null
        p.forEachChild {
            when (p.name) {
                "srgbClr", "schemeClr" -> c = readColorEl(p)
                "prstClr" -> { p.attr("val")?.let { OoxmlColor.hex(prstHex(it))?.argb }
                    ?.let { c = it }; p.skip() }
                else -> p.skip()
            }
        }
        return c
    }

    private fun readColorEl(p: XmlPullParser): Int? {
        return when (p.name) {
            "srgbClr" -> OoxmlColor.hex(p.attr("val"))?.argb
            "schemeClr" -> OoxmlColor.scheme(p.attr("val"))?.argb
            else -> null
        }.also { p.skip() }
    }

    private fun prstHex(name: String): String? = when (name) {
        "white" -> "FFFFFF"; "black" -> "000000"; "red" -> "FF0000"
        "blue" -> "0000FF"; "green" -> "008000"; "yellow" -> "FFFF00"
        else -> null
    }
}
