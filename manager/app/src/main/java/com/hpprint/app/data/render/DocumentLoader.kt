package com.hpprint.app.data.render
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 文档 → 逐页可渲染 [Bitmap] 的统一抽象。
 *
 * 无论源格式如何，最终都渲染为页面位图：预览（低 dpi）与打印（高 dpi）
 * 各建一个实例，[withDpi] 共享解析模型，排版代码一致，保证"预览即所得"。
 */
interface DocumentPages : AutoCloseable {
    val dpi: Int
    val pageCount: Int
    fun renderPage(index: Int): Bitmap

    /** 以不同 dpi 重建（模型可复用时共享解析结果） */
    fun withDpi(dpi: Int): DocumentPages
}

/**
 * 文档加载入口：按 MIME / 扩展名分流。load 为 suspend（HTML 需离屏 WebView）。
 */
object DocumentLoader {

    // -------- 页码范围（1 基输入 → 0 基、去重、升序） --------

    fun parseRange(spec: String?, total: Int): List<Int> {
        if (total <= 0) throw IllegalArgumentException("文档没有可打印的页面")
        if (spec.isNullOrBlank()) return (0 until total).toList()

        val result = sortedSetOf<Int>()
        spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains("-")) {
                val parts = token.split("-")
                if (parts.size != 2) throw IllegalArgumentException("范围格式错误：$token")
                val a = parts[0].trim()
                val b = parts[1].trim()
                val start = if (a.isEmpty()) 1 else a.toIntOrNull()
                    ?: throw IllegalArgumentException("范围格式错误：$token")
                val end = if (b.isEmpty()) total else b.toIntOrNull()
                    ?: throw IllegalArgumentException("范围格式错误：$token")
                if (start < 1 || end > total || start > end)
                    throw IllegalArgumentException("范围超出 1-$total：$token")
                for (p in start..end) result.add(p - 1)
            } else {
                val p = token.toIntOrNull()
                    ?: throw IllegalArgumentException("页码格式错误：$token")
                if (p < 1 || p > total)
                    throw IllegalArgumentException("页码超出 1-$total：$p")
                result.add(p - 1)
            }
        }
        if (result.isEmpty()) throw IllegalArgumentException("未选择任何页面")
        return result.toList()
    }

    // -------- 加载 --------

    suspend fun load(context: Context, uri: Uri, mime: String?, dpi: Int): DocumentPages {
        val name = queryName(context, uri).lowercase()
        val m = (mime ?: "").lowercase()

        return when {
            m == "application/pdf" || name.endsWith(".pdf") ->
                openPdf(context, uri, dpi)

            m.startsWith("image/") || imageExt(name) ->
                openImage(context, uri, dpi)

            m == "text/html" || name.endsWith(".html") || name.endsWith(".htm") -> {
                val pdf = HtmlRenderer.toPdfFile(context, uri)
                openPdf(context, Uri.fromFile(pdf), dpi)
            }

            m.startsWith("text/") || textExt(name) ->
                openText(context, uri, name, dpi)

            name.endsWith(".docx") ->
                DocxParser(OoxmlPackage.read(context, uri)).parse().pages(dpi)
            name.endsWith(".xlsx") ->
                XlsxParser(OoxmlPackage.read(context, uri)).parse().pages(dpi)
            name.endsWith(".pptx") ->
                PptxParser(OoxmlPackage.read(context, uri)).parse().pages(dpi)

            name.endsWith(".doc") ->
                TextPages(LegacyOffice.extractDoc(readBytes(context, uri)), dpi, name)
            name.endsWith(".xls") ->
                TextPages(LegacyOffice.extractXls(readBytes(context, uri)), dpi, name)
            name.endsWith(".ppt") ->
                TextPages(LegacyOffice.extractPpt(readBytes(context, uri)), dpi, name)
            name.endsWith(".rtf") ->
                TextPages(LegacyOffice.extractRtf(readBytes(context, uri)), dpi, name)

            else -> throw IllegalArgumentException(
                "暂不支持该文件格式。支持：PDF、Word(doc/docx)、Excel(xls/xlsx)、" +
                    "PPT(ppt/pptx)、TXT/RTF/HTML、图片；也可将文件另存为 PDF"
            )
        }
    }

    private fun imageExt(n: String) =
        n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".bmp")

    private fun textExt(n: String) =
        n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".csv") ||
            n.endsWith(".json") || n.endsWith(".log") || n.endsWith(".xml") ||
            n.endsWith(".ini") || n.endsWith(".conf")

    private fun monoExt(n: String) =
        n.endsWith(".csv") || n.endsWith(".json") || n.endsWith(".log") ||
            n.endsWith(".xml") || n.endsWith(".ini") || n.endsWith(".conf") ||
            n.endsWith(".md")

    private fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取该文件")

    private fun openPdf(context: Context, uri: Uri, dpi: Int): DocumentPages {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("无法读取该 PDF 文件")
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw IllegalStateException("PDF 已损坏或无法打开：${e.message}")
        }
        if (renderer.pageCount <= 0) {
            renderer.close(); pfd.close()
            throw IllegalStateException("该 PDF 没有页面")
        }
        return PdfPages(renderer, pfd, dpi)
    }

    private fun openImage(context: Context, uri: Uri, dpi: Int): DocumentPages {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取该图片")
        val bmp = decodeSampled(data, 3508)
            ?: throw IllegalStateException("图片解码失败")
        return BitmapPages(bmp, dpi)
    }

    private fun openText(
        context: Context, uri: Uri, name: String, dpi: Int
    ): DocumentPages {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取该文本文件")
        val text = String(bytes, HtmlRenderer.sniffCharset(bytes))
        if (text.isBlank()) throw IllegalStateException("该文件内容为空")
        return TextPages(text, dpi, name)
    }

    private fun queryName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "doc"
        return try {
            context.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use {
                if (it.moveToFirst()) it.getString(0) ?: "doc" else "doc"
            } ?: "doc"
        } catch (e: Exception) { "doc" }
    }
}

// ===================== PDF 页 =====================

private class PdfPages(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    override val dpi: Int,
    private val shared: Boolean = false
) : DocumentPages {

    override val pageCount: Int get() = renderer.pageCount

    override fun renderPage(index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            var scale = dpi / 72f
            val longPts = maxOf(page.width, page.height)
            val cap = if (dpi >= 250) 3508 else 2600
            if (longPts * scale > cap) scale = cap.toFloat() / longPts
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    override fun withDpi(dpi: Int): DocumentPages =
        PdfPages(renderer, pfd, dpi, shared = true)

    override fun close() {
        if (shared) return
        try { renderer.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }
}

// ===================== 单图片页 =====================

private class BitmapPages(private val bmp: Bitmap, override val dpi: Int) : DocumentPages {
    override val pageCount = 1
    override fun renderPage(index: Int): Bitmap = bmp
    override fun withDpi(dpi: Int): DocumentPages = this
    override fun close() { bmp.recycle() }
}

// ===================== 纯文本页 =====================

/**
 * 文本分页排版。代码/表格类（csv/json/log 等）用等宽字体小字号；
 * 纸张与方向由 [fileName] 无法决定，默认 A4 纵向（文本类通用）。
 */
class TextPages(
    private val text: String,
    override val dpi: Int,
    private val fileName: String = "",
    private val pageWIn0: Float = 8.27f,
    private val pageHIn0: Float = 11.69f
) : BaseRenderedPages(dpi) {

    init {
        val n = fileName.lowercase()
        val mono = n.endsWith(".csv") || n.endsWith(".json") || n.endsWith(".log") ||
            n.endsWith(".xml") || n.endsWith(".ini") || n.endsWith(".conf") ||
            n.endsWith(".md")
        val sizePt = if (mono) 9f else 11f

        val flow = PageFlow(dpi, pageWIn0, pageHIn0, 0.5f)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePt * dpi / 72f
            color = Color.BLACK
            if (mono) typeface = Typeface.MONOSPACE
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, flow.cw)
            .setLineSpacing(0f, if (mono) 1.25f else 1.32f)
            .setIncludePad(false)
            .build()
        flow.text(layout)
        pageDefs.addAll(flow.finish())
    }

    override fun withDpi(dpi: Int): DocumentPages =
        TextPages(text, dpi, fileName, pageWIn0, pageHIn0)

    override fun close() {}
}
