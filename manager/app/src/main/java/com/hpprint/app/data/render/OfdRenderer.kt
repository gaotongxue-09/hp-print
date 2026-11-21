package com.hpprint.app.data.render

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader
import kotlin.math.roundToInt
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * OFD（开放版式文档，中文电子发票标准格式）→ PDF 的轻量渲染器。
 *
 * OFD 本质是一个 ZIP：根目录 [OFD.xml] 通过 DocRoot 指向文档根 Document.xml，
 * Document.xml 内逐个 [Page BaseLoc] 指向每页 Content.xml。每页内容以绝对坐标
 * （单位毫米）摆放 [TextCode] 文本块。
 *
 * 本实现只提取文本（TextCode）并按其毫米坐标/字号绘制到 PDF 页，足够把电子发票的
 * 抬头、金额、税号、明细等关键字段打印出来；复杂的矢量图形、印章位图暂不渲染。
 */
object OfdRenderer {

    private const val MM_TO_PT = 72f / 25.4f

    private data class TextItem(val text: String, val x: Float, val y: Float, val size: Float)

    private data class PageSpec(val contentPath: String, val wMm: Float, val hMm: Float)

    /** 把 OFD 文件渲染为 PDF 字节（多页）；失败返回 null。 */
    fun toPdfBytes(context: Context, uri: String): ByteArray? = runCatching {
        val bytes = if (uri.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } else {
            java.io.File(uri).takeIf { it.exists() }?.readBytes()
        } ?: return null

        val pages = parsePages(bytes)
        if (pages.isEmpty()) return null

        val doc = PdfDocument()
        val out = ByteArrayOutputStream()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT
        }
        for (page in pages) {
            val texts = parseText(bytes, page.contentPath)
            val pageW = mmToPt(page.wMm)
            val pageH = mmToPt(page.hMm)
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            val p = doc.startPage(info)
            p.canvas.drawColor(Color.WHITE)
            for (it in texts) {
                paint.textSize = it.size * MM_TO_PT
                p.canvas.drawText(it.text, it.x * MM_TO_PT, (it.y + it.size) * MM_TO_PT, paint)
            }
            doc.finishPage(p)
        }
        doc.writeTo(out)
        doc.close()
        out.toByteArray()
    }.getOrNull()

    private fun mmToPt(mm: Float): Int = (mm * MM_TO_PT).roundToInt().coerceAtLeast(1)

    private fun parsePages(bytes: ByteArray): List<PageSpec> {
        val docRoot = readEntry(bytes, "OFD.xml")?.let { xml -> firstText(xml, "DocRoot") } ?: return emptyList()
        val docXml = readEntry(bytes, docRoot) ?: return emptyList()
        val pages = mutableListOf<PageSpec>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(docXml))
        var event = parser.eventType
        var currentW = 210f
        var currentH = 297f
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && local(parser.name) == "Page") {
                parseBox(parser.getAttributeValue(null, "PhysicalBox"))?.let {
                    currentW = it[2]
                    currentH = it[3]
                }
                parser.getAttributeValue(null, "BaseLoc")?.let { base ->
                    pages.add(PageSpec(docRoot.substringBeforeLast('/') + "/" + base, currentW, currentH))
                }
            }
            event = parser.next()
        }
        return pages
    }

    private fun parseText(bytes: ByteArray, contentPath: String): List<TextItem> {
        val xml = readEntry(bytes, contentPath) ?: return emptyList()
        val items = mutableListOf<TextItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var currentSize = 4f
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (local(parser.name)) {
                    "TextObject" -> parser.getAttributeValue(null, "Size")?.toFloatOrNull()?.let { currentSize = it }
                    "TextCode" -> {
                        val x = parser.getAttributeValue(null, "X")?.toFloatOrNull() ?: 0f
                        val y = parser.getAttributeValue(null, "Y")?.toFloatOrNull() ?: 0f
                        val text = parser.nextText()
                        if (text.isNotBlank()) items.add(TextItem(text, x, y, currentSize))
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun readEntry(bytes: ByteArray, name: String): String? {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name.trimStart('/') == name.trimStart('/')) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun firstText(xml: String, tag: String): String? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && local(parser.name) == tag) {
                return parser.nextText().trim()
            }
            event = parser.next()
        }
        return null
    }

    /** 去掉命名空间前缀（DocRoot、Page、TextCode 等可带 ofd: 前缀）。 */
    private fun local(name: String): String = name.substringAfterLast(':')

    /** 解析 "xmin ymin xmax ymax"，返回 FloatArray(4)；失败返回 null。 */
    private fun parseBox(box: String?): FloatArray? {
        if (box.isNullOrBlank()) return null
        val v = box.trim().split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
        return if (v.size >= 4) floatArrayOf(v[0], v[1], v[2], v[3]) else null
    }
}