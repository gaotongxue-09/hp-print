package com.hpprint.app.data.render
import android.graphics.Color
import org.xmlpull.v1.XmlPullParser

// ===================== 模型 =====================

data class XSharedRun(
    val text: String,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val color: Int? = null,
    val sizePt: Float? = null
)

data class XCellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: Int? = null,
    val sizePt: Float? = null,
    val fill: Int? = null,
    val border: Boolean = false,
    val hAlign: Int = 0,             // 0常规 1左 2中 3右
    val wrap: Boolean = false,
    val numFmtId: Int = 0,
    val numFmtCode: String? = null
)

data class XCell(
    val col: Int,
    val row: Int,
    val text: String? = null,
    val rich: List<XSharedRun>? = null,
    val numValue: Double? = null,
    val style: XCellStyle? = null
)

data class XRow(val num: Int, val heightPt: Float?, val cells: Map<Int, XCell>)

data class XCol(val widthChars: Float, val hidden: Boolean)

data class XRange(val c1: Int, val r1: Int, val c2: Int, val r2: Int)

data class XSheet(
    val name: String,
    val part: String,
    val rows: Map<Int, XRow>,
    val cols: Map<Int, XCol>,         // col 号(1基) → 定义
    val merges: List<XRange>,
    val pictures: List<String>        // media 路径
)

data class XWorkbook(val sheets: List<XSheet>)

class XlsxDoc(val model: XWorkbook, private val pkg: OoxmlPackage) {
    fun pages(dpi: Int): XlsxPages = XlsxPages(model, pkg, dpi)
    fun close() {}
}

// ===================== 解析 =====================

class XlsxParser(private val pkg: OoxmlPackage) {

    fun parse(): XlsxDoc {
        // 1) 共享字符串
        val shared = parseShared()
        // 2) 样式
        val styles = parseStyles()
        // 3) workbook → sheet 部件
        val sheetParts = parseWorkbookSheets()   // List<Pair<name, part>>
        if (sheetParts.isEmpty())
            throw IllegalArgumentException("无法解析该 xlsx（未找到工作表），建议另存为 PDF")

        val sheets = sheetParts.map { (name, part) ->
            parseSheet(name, part, shared, styles)
        }
        return XlsxDoc(XWorkbook(sheets), pkg)
    }

    // ---------- 共享字符串 ----------

    private fun parseShared(): List<List<XSharedRun>> {
        val bytes = pkg.entry("xl/sharedStrings.xml") ?: return emptyList()
        val out = mutableListOf<List<XSharedRun>>()
        val p = Px.pull(bytes).toStart()
        p.forEachChild {
            if (p.name == "si") {
                val runs = mutableListOf<XSharedRun>()
                p.forEachChild {
                    when (p.name) {
                        "t" -> runs.add(XSharedRun(p.textBody()))
                        "r" -> {
                            var rs = XSharedRun("")
                            p.forEachChild {
                                when (p.name) {
                                    "rPr" -> {
                                        p.forEachChild {
                                            when (p.name) {
                                                "b" -> rs = rs.copy(bold = true)
                                                "i" -> rs = rs.copy(italic = true)
                                                "color" -> rs = rs.copy(
                                                    color = parseColorAttr(p) ?: rs.color
                                                )
                                                "sz" -> rs = rs.copy(
                                                    sizePt = p.attr("val")?.toFloatOrNull()
                                                        ?: rs.sizePt
                                                )
                                            }
                                            p.skip()
                                        }
                                    }
                                    "t" -> rs = rs.copy(text = p.textBody())
                                    else -> p.skip()
                                }
                            }
                            runs.add(rs)
                        }
                        else -> p.skip()
                    }
                }
                if (runs.isEmpty()) runs.add(XSharedRun(""))
                out.add(runs)
            } else p.skip()
        }
        return out
    }

    // ---------- 样式 ----------

    private data class FontDef(
        val bold: Boolean, val italic: Boolean,
        val color: Int?, val sizePt: Float?
    )

    private class StyleContext(
        val fonts: List<FontDef>,
        val fills: List<Int?>,
        val xfs: List<XCellStyle>
    )

    private fun parseStyles(): StyleContext {
        val bytes = pkg.entry("xl/styles.xml") ?: return StyleContext(
            listOf(FontDef(false, false, null, null)), listOf(null), listOf(XCellStyle())
        )
        val p = Px.pull(bytes).toStart()

        val customFmts = HashMap<Int, String>()
        val fonts = mutableListOf<FontDef>()
        val fills = mutableListOf<Int?>()
        val rawXfs = mutableListOf<XFDef>()

        p.forEachChild {
            when (p.name) {
                "numFmts" -> {
                    p.forEachChild {
                        if (p.name == "numFmt") {
                            val id = p.attr("numFmtId")?.toIntOrNull()
                            val code = p.attr("formatCode")
                            if (id != null && code != null) customFmts[id] = code
                        }
                        p.skip()
                    }
                }
                "fonts" -> {
                    p.forEachChild {
                        if (p.name == "font") {
                            var b = false; var i = false
                            var color: Int? = null; var sz: Float? = null
                            p.forEachChild {
                                when (p.name) {
                                    "b" -> b = true
                                    "i" -> i = true
                                    "color" -> color = parseColorAttr(p)
                                    "sz" -> sz = p.attr("val")?.toFloatOrNull() ?: sz
                                }
                                p.skip()
                            }
                            fonts.add(FontDef(b, i, color, sz))
                        } else p.skip()
                    }
                }
                "fills" -> {
                    p.forEachChild {
                        if (p.name == "fill") {
                            var fc: Int? = null
                            p.forEachChild {
                                if (p.name == "patternFill") {
                                    p.forEachChild {
                                        if (p.name == "fgColor") fc = parseColorAttr(p) ?: fc
                                        p.skip()
                                    }
                                } else p.skip()
                            }
                            fills.add(fc)
                        } else p.skip()
                    }
                }
                "borders" -> { /* 只需知道某 xf 是否有边框，遍历记录到 borderFlags */
                    borderList.clear()
                    p.forEachChild {
                        if (p.name == "border") {
                            var has = false; var bc: Int? = null
                            p.forEachChild {
                                if (p.name in setOf("left", "right", "top", "bottom")) {
                                    val st = p.attr("style")
                                    if (st != null && st != "none") {
                                        has = true
                                        if (bc == null) bc = parseColorAttr(p)
                                    }
                                }
                                p.skip()
                            }
                            borderList.add(has to bc)
                        } else p.skip()
                    }
                }
                "cellXfs" -> {
                    p.forEachChild {
                        if (p.name == "xf") {
                            rawXfs.add(XFDef(
                                numFmtId = p.attr("numFmtId")?.toIntOrNull() ?: 0,
                                fontId = p.attr("fontId")?.toIntOrNull() ?: 0,
                                fillId = p.attr("fillId")?.toIntOrNull() ?: 0,
                                borderId = p.attr("borderId")?.toIntOrNull() ?: 0,
                                hAlign = 0, wrap = false
                            ).also { xf ->
                                // alignment 子元素
                                p.forEachChild {
                                    if (p.name == "alignment") {
                                        xf.hAlign = when (p.attr("horizontal")) {
                                            "left" -> 1; "center", "centerContinuous" -> 2
                                            "right" -> 3; else -> 0
                                        }
                                        xf.wrap = p.attr("wrapText") == "1"
                                    }
                                    p.skip()
                                }
                            })
                        } else p.skip()
                    }
                }
                else -> p.skip()
            }
        }

        if (fonts.isEmpty()) fonts.add(FontDef(false, false, null, null))
        if (fills.isEmpty()) fills.add(null)

        val xfs = rawXfs.map { xf ->
            val font = fonts.getOrNull(xf.fontId)
            val fill = fills.getOrNull(xf.fillId)
            val border = borderList.getOrNull(xf.borderId)
            XCellStyle(
                bold = font?.bold ?: false,
                italic = font?.italic ?: false,
                color = font?.color,
                sizePt = font?.sizePt,
                fill = fill,
                border = border?.first ?: false,
                hAlign = xf.hAlign,
                wrap = xf.wrap,
                numFmtId = xf.numFmtId,
                numFmtCode = customFmts[xf.numFmtId]
            )
        }.ifEmpty { listOf(XCellStyle()) }

        return StyleContext(fonts, fills, xfs)
    }

    private class XFDef(
        val numFmtId: Int, val fontId: Int, val fillId: Int, val borderId: Int,
        var hAlign: Int, var wrap: Boolean
    )

    private val borderList = mutableListOf<Pair<Boolean, Int?>>()

    // ---------- workbook 部件表 ----------

    private fun parseWorkbookSheets(): List<Pair<String, String>> {
        val wbBytes = pkg.entry("xl/workbook.xml") ?: return emptyList()
        val rels = pkg.relsOf("xl/workbook.xml")
        val out = mutableListOf<Pair<String, String>>()
        val p = Px.pull(wbBytes).toStart()
        p.forEachChild {
            if (p.name == "sheets") {
                p.forEachChild {
                    if (p.name == "sheet") {
                        val name = p.attr("name") ?: "工作表"
                        val rid = p.attr("r:id")
                        val rel = rid?.let { rels[it] }
                        val target = rel?.let { pkg.resolveTarget("xl/workbook.xml", it) }
                        if (target != null) out.add(name to target)
                    }
                    p.skip()
                }
            } else p.skip()
        }
        return out
    }

    // ---------- 工作表 ----------

    private fun parseSheet(
        name: String,
        part: String,
        shared: List<List<XSharedRun>>,
        styles: StyleContext
    ): XSheet {
        val bytes = pkg.entry(part)
            ?: throw IllegalArgumentException("无法读取工作表 $name")
        val p = Px.pull(bytes).toStart()

        val rows = sortedMapOf<Int, XRow>()
        val cols = HashMap<Int, XCol>()
        val merges = mutableListOf<XRange>()
        var drawingRid: String? = null

        p.forEachChild {
            when (p.name) {
                "cols" -> {
                    p.forEachChild {
                        if (p.name == "col") {
                            val min = p.attr("min")?.toIntOrNull() ?: 1
                            val max = p.attr("max")?.toIntOrNull() ?: min
                            val width = p.attr("width")?.toFloatOrNull() ?: 8.43f
                            val hidden = p.attr("hidden") == "1"
                            for (c in min..max) cols[c] = XCol(width, hidden)
                        }
                        p.skip()
                    }
                }
                "sheetData" -> {
                    p.forEachChild {
                        if (p.name == "row") {
                            val num = p.attr("r")?.toIntOrNull()
                            val ht = p.attr("ht")?.toFloatOrNull()
                            val hidden = p.attr("hidden") == "1"
                            val cells = HashMap<Int, XCell>()
                            p.forEachChild {
                                if (p.name == "c") {
                                    val cell = parseCell(p, shared, styles)
                                    if (cell != null) cells[cell.col] = cell
                                } else p.skip()
                            }
                            if (num != null && !hidden)
                                rows[num] = XRow(num, ht, cells)
                        } else p.skip()
                    }
                }
                "mergeCells" -> {
                    p.forEachChild {
                        if (p.name == "mergeCell") {
                            p.attr("ref")?.let { ref -> parseA1Range(ref)?.let { merges.add(it) } }
                        }
                        p.skip()
                    }
                }
                "drawing" -> { drawingRid = p.attr("r:id"); p.skip() }
                else -> p.skip()
            }
        }

        // 图片：drawing rels 中的全部 image
        val pictures = mutableListOf<String>()
        if (drawingRid != null) {
            val drawRel = pkg.relsOf(part)[drawingRid]
            val drawPart = drawRel?.let { pkg.resolveTarget(part, it) }
            if (drawPart != null) {
                pkg.relsOf(drawPart).values.forEach { r ->
                    if (r.kind == "image") {
                        pkg.resolveTarget(drawPart, r)?.let { path -> pictures.add(path) }
                    }
                }
            }
        }

        if (rows.values.none { it.cells.isNotEmpty() } && pictures.isEmpty())
            throw IllegalArgumentException("工作表「$name」未解析到内容，建议另存为 PDF")

        return XSheet(name, part, rows, cols, merges, pictures)
    }

    private fun parseCell(
        p: org.xmlpull.v1.XmlPullParser,
        shared: List<List<XSharedRun>>,
        styles: StyleContext
    ): XCell? {
        val ref = p.attr("r") ?: return null
        val (col, row) = parseA1(ref) ?: return null
        val t = p.attr("t")
        val sIdx = p.attr("s")?.toIntOrNull()
        val style = sIdx?.let { styles.xfs.getOrNull(it) }

        var vText: String? = null
        var rich: List<XSharedRun>? = null
        var num: Double? = null

        p.forEachChild {
            when (p.name) {
                "v" -> vText = p.textBody().trim().ifEmpty { null }
                "is" -> {
                    val runs = mutableListOf<XSharedRun>()
                    p.forEachChild {
                        when (p.name) {
                            "t" -> runs.add(XSharedRun(p.textBody()))
                            "r" -> {
                                var rs = XSharedRun("")
                                p.forEachChild {
                                    when (p.name) {
                                        "rPr" -> {
                                            p.forEachChild {
                                                when (p.name) {
                                                    "b" -> rs = rs.copy(bold = true)
                                                    "i" -> rs = rs.copy(italic = true)
                                                    "color" -> rs = rs.copy(
                                                        color = parseColorAttr(p) ?: rs.color
                                                    )
                                                }
                                                p.skip()
                                            }
                                        }
                                        "t" -> rs = rs.copy(text = p.textBody())
                                        else -> p.skip()
                                    }
                                }
                                runs.add(rs)
                            }
                            else -> p.skip()
                        }
                    }
                    if (runs.isNotEmpty()) rich = runs
                }
                "f" -> p.skip()
                else -> p.skip()
            }
        }

        when (t) {
            "s" -> {
                val idx = vText?.toIntOrNull()
                if (idx != null) {
                    val item = shared.getOrNull(idx)
                    if (item != null) {
                        if (item.size == 1 && item[0].bold == null && item[0].color == null)
                            vText = item.joinToString("") { it.text }
                        else rich = item
                    }
                }
            }
            "b" -> vText = if (vText == "1") "TRUE" else "FALSE"
            "str", "inlineStr", null -> {
                if (t == null && vText != null) num = vText?.toDoubleOrNull()
            }
            else -> {}
        }
        if (t == "inlineStr") { vText = rich?.joinToString("") { it.text } ?: vText }

        if (vText == null && rich == null) return null
        return XCell(col, row, vText, rich, num, style)
    }
}

// ===================== A1 引用 / 颜色工具 =====================

/** "B12" → (col=2,row=12) */
fun parseA1(ref: String): Pair<Int, Int>? {
    val m = Regex("([A-Za-z]+)([0-9]+)").matchEntire(ref) ?: return null
    var col = 0
    m.groupValues[1].forEach { c -> col = col * 26 + (c.uppercaseChar() - 'A' + 1) }
    val row = m.groupValues[2].toIntOrNull() ?: return null
    return col to row
}

/** "B2:D5" → XRange */
fun parseA1Range(ref: String): XRange? {
    val parts = ref.split(':')
    val a = parseA1(parts[0]) ?: return null
    val b = if (parts.size > 1) parseA1(parts[1]) ?: a else a
    return XRange(minOf(a.first, b.first), minOf(a.second, b.second),
        maxOf(a.first, b.first), maxOf(a.second, b.second))
}

/** 解析 xlsx 中 color 元素：rgb(ARGB/RGB) 或 theme（简化） */
fun parseColorAttr(p: XmlPullParser): Int? {
    p.attr("rgb")?.let { rgb ->
        var h = rgb
        if (h.length == 6) return OoxmlColor.hex(h)?.argb
        if (h.length == 8) {
            val v = h.toLongOrNull(16) ?: return null
            return (v and 0xFFFFFFFF).toInt()
        }
    }
    p.attr("indexed")?.toIntOrNull()?.let { idx ->
        // 常见 indexed 调色板：0-7 标准色
        return indexedColors.getOrNull(idx)
    }
    p.attr("theme")?.toIntOrNull()?.let { t ->
        return when (t) {
            0 -> Color.BLACK; 1 -> Color.WHITE
            2 -> 0xFF44546A.toInt(); 3 -> 0xFFE7E6E6.toInt()
            4 -> 0xFF4472C4.toInt(); else -> null
        }
    }
    return null
}

private val indexedColors = listOf(
    Color.BLACK, Color.WHITE, Color.RED, 0xFF00FF00.toInt(),
    Color.BLUE, 0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
    Color.BLACK, Color.WHITE
)
