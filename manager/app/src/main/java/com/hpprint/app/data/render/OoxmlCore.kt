package com.hpprint.app.data.render
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// ===================== 物理单位换算（OOXML） =====================
//
// EMU   ：DrawingML 单位，914400 EMU = 1 英寸，12700 EMU = 1 磅(pt)
// Twip  ：Word 单位，1440 twips = 1 英寸，20 twips = 1 磅
// 磅 pt ：1/72 英寸

const val EMU_PER_INCH = 914400
const val EMU_PER_PT = 12700
const val TWIPS_PER_INCH = 1440
const val TWIPS_PER_PT = 20

fun emuToInch(v: Long): Float = v.toFloat() / EMU_PER_INCH
fun emuToPt(v: Long): Float = v.toFloat() / EMU_PER_PT
fun twipsToPt(v: Int): Float = v.toFloat() / TWIPS_PER_PT
fun twipsToInch(v: Int): Float = v.toFloat() / TWIPS_PER_INCH

// ===================== 颜色 =====================

/** srgbClr / schemeClr 统一解析结果（ARGB） */
@JvmInline
value class OoxmlColor(val argb: Int) {
    companion object {
        val BLACK = OoxmlColor(0xFF000000.toInt())
        val WHITE = OoxmlColor(0xFFFFFFFF.toInt())

        /** 解析 6 位十六进制（RRGGBB），也容忍 3 位 / 带 # */
        fun hex(hex: String?): OoxmlColor? {
            if (hex == null) return null
            var h = hex.trim().removePrefix("#")
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            if (h.length != 6) return null
            val v = h.toLongOrNull(16) ?: return null
            return OoxmlColor(0xFF000000.toInt() or v.toInt())
        }

        /**
         * Office 默认主题（Office 2013+ 的 Office 主题）。
         * 文档引用主题色但未显式给出 srgbClr 时使用，保证有合理颜色。
         */
        fun scheme(name: String?): OoxmlColor? = when (name) {
            // 浅色主题：bg1=lt1 背景为白；tx1=dk1 文字为黑
            "tx1", "dk1" -> BLACK
            "bg1", "lt1" -> WHITE
            "tx2", "dk2" -> hex("44546A")
            "bg2", "lt2" -> hex("E7E6E6")
            "accent1" -> hex("4472C4")
            "accent2" -> hex("ED7D31")
            "accent3" -> hex("A5A5A5")
            "accent4" -> hex("FFC000")
            "accent5" -> hex("5B9BD5")
            "accent6" -> hex("70AD47")
            "hlink" -> hex("0563C1")
            "folHlink" -> hex("954F72")
            else -> null
        }
    }
}

// ===================== OOXML 压缩包读取 =====================

/** 一条 relationship */
data class Rel(
    val id: String,
    val type: String,           // 完整类型 URI
    val target: String,         // 原始 target（相对 / 绝对）
    val external: Boolean
) {
    /** 类型末段，如 .../relationships/image → "image" */
    val kind: String get() = type.substringAfterLast('/')
}

/**
 * 一次性读入 OOXML 包全部条目（二进制安全，区别于旧 OfficeText 用 Reader 读文本）。
 * 提供部件关系解析与按 r:id 定位目标条目、图片降采样解码。
 */
class OoxmlPackage private constructor(val entries: Map<String, ByteArray>) {

    fun entry(path: String): ByteArray? = entries[normalize(path)]

    /** 包根关系 _rels/.rels */
    fun rootRels(): Map<String, Rel> = relsOf("_rels/.rels")

    /**
     * 某部件的关系表，如 part="word/document.xml" → word/_rels/document.xml.rels
     */
    fun relsOf(part: String): Map<String, Rel> {
        val dir = part.substringBeforeLast('/', "")
        val fileName = part.substringAfterLast('/')
        val relPath = if (dir.isEmpty()) "_rels/$fileName.rels"
        else "$dir/_rels/$fileName.rels"
        val bytes = entries[relPath] ?: return emptyMap()
        val map = LinkedHashMap<String, Rel>()
        val p = Px.pull(bytes)
        var ev = p.eventType
        while (ev != XmlPullParser.START_TAG) { ev = p.next() }
        p.forEachChild {
            if (p.name == "Relationship") {
                val id = p.attr("Id") ?: return@forEachChild
                val type = p.attr("Type") ?: ""
                val target = p.attr("Target") ?: ""
                val mode = p.attr("TargetMode")
                map[id] = Rel(id, type, target, mode == "External")
            }
            p.skip()
        }
        return map
    }

    /**
     * 把关系 target 解析成包内条目路径（处理相对路径与 ".."）。
     * @return 条目路径；外部目标返回 null
     */
    fun resolveTarget(part: String, rel: Rel): String? {
        if (rel.external) return null
        if (rel.target.startsWith("/")) return normalize(rel.target.removePrefix("/"))
        val dir = part.substringBeforeLast('/', "")
        return normalize(if (dir.isEmpty()) rel.target else "$dir/${rel.target}")
    }

    /** 按 r:id 直接取目标条目字节 */
    fun entryByRelId(part: String, relId: String?): ByteArray? {
        if (relId == null) return null
        val rel = relsOf(part)[relId] ?: return null
        val path = resolveTarget(part, rel) ?: return null
        return entries[path]
    }

    /** 降采样解码图片，长边尽量接近 [reqLongEdge]，控制内存 */
    fun bitmap(entryPath: String, reqLongEdge: Int): Bitmap? {
        val data = entries[normalize(entryPath)] ?: return null
        return decodeSampled(data, reqLongEdge)
    }

    fun bitmapByRelId(part: String, relId: String?, reqLongEdge: Int): Bitmap? {
        val data = entryByRelId(part, relId) ?: return null
        return decodeSampled(data, reqLongEdge)
    }

    private fun normalize(p: String): String {
        val stack = ArrayDeque<String>()
        p.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(seg)
            }
        }
        return stack.joinToString("/")
    }

    companion object {
        fun read(context: Context, uri: Uri): OoxmlPackage {
            val map = LinkedHashMap<String, ByteArray>()
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("无法读取该文件")
            input.use {
                ZipInputStream(it).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) map[e.name] = zis.readBytes()
                        zis.closeEntry()
                        e = zis.nextEntry
                    }
                }
            }
            if (map.isEmpty())
                throw IllegalArgumentException("无法按压缩包读取该文件（可能不是有效的 Office 文档）")
            return OoxmlPackage(map)
        }
    }
}

// ===================== 图片解码 =====================

/** 2 的幂降采样解码，使长边落在 reqLongEdge ~ 2×reqLongEdge */
fun decodeSampled(data: ByteArray, reqLongEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var sample = 1
        var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / 2 >= reqLongEdge) { longEdge /= 2; sample *= 2 }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)?.let { return it }
    }
    // BitmapFactory 不识别：尝试 EMF/WMF 内嵌位图、SVG 渲染
    return ImageDecoders.decode(data, reqLongEdge)
}

/**
 * 图片是否为 Android 位图解码不支持的矢量/特殊格式（EMF/WMF/SVG/TIFF 等）。
 * 这类资源 BitmapFactory 解码为 null，需要占位降级而非静默丢失。
 */
fun isUnsupportedImageFormat(entryPath: String): String? {
    val ext = entryPath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "emf", "wmf", "emz", "wmz" -> "EMF/WMF 矢量图"
        "svg", "svgz" -> "SVG 矢量图"
        "tif", "tiff" -> "TIFF 图片"
        "eps", "ai", "cdr" -> "矢量图"
        else -> null
    }
}

/**
 * 生成"无法解码图片"的占位位图（浅灰底 + 边框 + 居中文本），尺寸即最终显示尺寸。
 * 让预览/打印中如实保留图片位置与占位，而不是一片空白。
 */
fun makePlaceholderBitmap(wPx: Int, hPx: Int, dpi: Int, formatLabel: String): Bitmap {
    val w = wPx.coerceAtLeast(dpi / 2)
    val h = hPx.coerceAtLeast(dpi / 3)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFF4F5F7.toInt())

    val stroke = (dpi / 96f).coerceIn(1f, 3f)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8BEC7.toInt(); style = Paint.Style.STROKE; strokeWidth = stroke
    }
    c.drawRect(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2, border)

    // 简易图片图标（圆角矩形 + 太阳 + 山峦）
    val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC7CCD4.toInt(); style = Paint.Style.FILL
    }
    val iw = (w * 0.26f).coerceAtMost(dpi * 1.6f)
    val ih = iw * 0.72f
    val ix = (w - iw) / 2f
    val iy = h / 2f - ih - dpi * 0.06f
    c.drawRect(ix, iy, ix + iw, iy + ih, icon)
    val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE3B341.toInt(); style = Paint.Style.FILL
    }
    c.drawCircle(ix + iw * 0.26f, iy + ih * 0.3f, iw * 0.07f, sun)
    val tri = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA1AC.toInt(); style = Paint.Style.FILL
    }
    android.graphics.Path().apply {
        moveTo(ix + iw * 0.12f, iy + ih * 0.86f)
        lineTo(ix + iw * 0.42f, iy + ih * 0.48f)
        lineTo(ix + iw * 0.62f, iy + ih * 0.7f)
        lineTo(ix + iw * 0.8f, iy + ih * 0.5f)
        lineTo(ix + iw * 0.9f, iy + ih * 0.86f)
        close()
        c.drawPath(this, tri)
    }

    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6B7280.toInt(); textAlign = Paint.Align.CENTER
    }
    fun fit(text: String, maxSp: Float): Float {
        var s = maxSp; tp.textSize = s
        val maxW = w * 0.9f
        while (tp.measureText(text) > maxW && s > 7f) { s -= 0.5f; tp.textSize = s }
        return s
    }
    val title = "图片暂不支持预览"
    val sub = "$formatLabel · 建议另存为 PDF 后打印"
    val ts = fit(title, 11f * dpi / 72f)
    tp.textSize = ts
    val baseY = h / 2f + ih * 0.28f
    c.drawText(title, w / 2f, baseY, tp)
    tp.textSize = fit(sub, 8.5f * dpi / 72f)
    tp.color = 0xFF9AA1AC.toInt()
    c.drawText(sub, w / 2f, baseY + ts * 1.5f, tp)
    return bmp
}

// ===================== XmlPullParser 扩展 =====================

object Px {
    fun pull(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
}

/**
 * 属性值（命名空间不敏感）。
 *
 * 注意：Android 的 KXmlParser 由 [android.util.Xml.newPullParser] 创建后是命名空间感知的，
 * 元素名/属性名只暴露**本地名**（不带前缀）。因此 attr("w:val")、attr("r:embed") 这类
 * 带前缀写法用整体名查会恒为 null。这里做兼容：
 *  1. 先按原写法整体匹配（命中无前缀属性，如 cx、cy、Id、Target）；
 *  2. 取不到时，若是"前缀:本地名"，回退用冒号后的本地名匹配（命名空间通配）。
 * OOXML 单个元素上不存在同本地名、不同前缀的冲突属性，故通配是安全的。
 */
fun XmlPullParser.attr(name: String): String? {
    getAttributeValue(null, name)?.let { return it }
    val colon = name.lastIndexOf(':')
    if (colon >= 0) getAttributeValue(null, name.substring(colon + 1))?.let { return it }
    return null
}

/** 移动到第一个 START_TAG */
fun XmlPullParser.toStart(): XmlPullParser {
    var ev = eventType
    while (ev != XmlPullParser.START_TAG && ev != XmlPullParser.END_DOCUMENT) ev = next()
    return this
}

/** 跳过当前 START_TAG 元素的全部内容（结束时停在对应 END_TAG） */
fun XmlPullParser.skip() {
    if (eventType != XmlPullParser.START_TAG) return
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
        }
    }
}

/**
 * 遍历当前 START_TAG 的直接子元素。
 * 约定：block 执行时 parser 停在子元素 START_TAG，block 内必须用
 * forEachChild 递归（返回时停在子 END_TAG）或 [skip] 消费完该子元素。
 */
inline fun XmlPullParser.forEachChild(block: XmlPullParser.() -> Unit) {
    var depth = 0
    while (true) {
        when (val e = next()) {
            XmlPullParser.START_TAG -> if (depth == 0) block() else depth++
            XmlPullParser.END_TAG -> if (depth == 0) return else depth--
            XmlPullParser.END_DOCUMENT -> return
            else -> {}
        }
    }
}

/** 读取只含文本的元素（如 <w:t>abc</w:t>），结束时停在 END_TAG */
fun XmlPullParser.textBody(): String {
    val sb = StringBuilder()
    var depth = 0
    while (true) {
        when (val e = next()) {
            XmlPullParser.TEXT -> sb.append(text)
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> if (depth == 0) return sb.toString() else depth--
            XmlPullParser.END_DOCUMENT -> return sb.toString()
        }
    }
}

// ===================== 渲染页基类与排版引擎 =====================

/** 一页的物理尺寸（英寸）与绘制指令（坐标原点为页面左上角） */
class PageDef(val widthIn: Float, val heightIn: Float, val draw: (Canvas) -> Unit)

/**
 * 所有自研渲染器的基类：排版产物为 [PageDef] 列表，渲染时按 dpi 建位图。
 * 预览与打印只是 dpi 不同、排版代码完全一致，从根本保证"预览即所得"。
 */
abstract class BaseRenderedPages(override val dpi: Int) : DocumentPages {
    protected val pageDefs = mutableListOf<PageDef>()
    override val pageCount: Int get() = pageDefs.size

    override fun renderPage(index: Int): Bitmap {
        val def = pageDefs[index]
        val w = (def.widthIn * dpi).toInt().coerceAtLeast(1)
        val h = (def.heightIn * dpi).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        def.draw(Canvas(bmp))
        return bmp
    }
}

/**
 * 流式排版原语：多页的 [PageFlow] 与单页不分页的 [StaticLayer]（页眉/页脚）共用。
 * 内容区坐标原点为左上角。
 */
interface FlowContext {
    val dpi: Int
    val cw: Int
    val ch: Int
    val curY: Int
    val remaining: Int

    fun pageBreak()
    fun ensure(h: Int): Int
    fun draw(op: (Canvas) -> Unit)
    fun gap(h: Int)
}

/** 实心矩形（内容区坐标） */
fun FlowContext.rect(x: Int, y: Int, w: Int, h: Int, color: Int) = draw { c ->
    val p = Paint().apply { this.color = color; style = Paint.Style.FILL }
    c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
}

/**
 * 绘制一个位图块：按指定宽高（像素），超页宽/页高自动缩放；
 * 调用前可用 [ensure] 换页。
 */
fun FlowContext.image(bmp: Bitmap, wIn: Int, hIn: Int) {
    var w = wIn; var h = hIn
    if (w > cw) {
        val s = cw.toFloat() / w
        w = cw; h = (h * s).toInt()
    }
    if (h > ch) {
        val s = ch.toFloat() / h
        h = ch; w = (w * s).toInt()
    }
    ensure(h)
    val y = curY
    draw { c -> c.drawBitmap(bmp, null,
        android.graphics.Rect(0, y, w, y + h), null) }
    gap(h + dpi / 60)
}

/**
 * 文本块排版：自动跨页，按行切分，每页从可用高度继续。
 * @param xPad 返回某批次（首行号为 firstDrawnLine）的左偏移像素，用于段落缩进
 * @return 实际占用总高度
 */
fun FlowContext.text(
    layout: StaticLayout,
    xPad: (firstDrawnLine: Int) -> Int = { 0 }
): Int {
    val startY0 = curY
    var line = 0
    val n = layout.lineCount
    while (line < n) {
        if (remaining <= 0) pageBreak()
        val top = layout.getLineTop(line)
        var end = line
        while (end < n && layout.getLineBottom(end) - top <= remaining) end++
        if (end == line) end = line + 1
        val y = curY
        val l0 = line; val l1 = end
        val xx = xPad(l0)
        draw { c ->
            c.save()
            c.translate(xx.toFloat(), 0f)
            drawLayoutRange(c, layout, l0, l1, y, top)
            c.restore()
        }
        gap(layout.getLineBottom(end - 1) - top)
        line = end
    }
    return curY - startY0
}

/**
 * 流式页面排版：内容从左上到右下排列，自动换页；文本块可跨页。
 * 支持每页固定绘制的页眉/页脚（[headerOps]/[footerOps]），正文区随之收缩。
 */
class PageFlow(
    override val dpi: Int,
    val widthIn: Float,
    val heightIn: Float,
    marginIn: Float = 0.5f,
    headerHPx: Int = 0,
    footerHPx: Int = 0
) : FlowContext {
    val pageW = (widthIn * dpi).toInt()
    val pageH = (heightIn * dpi).toInt()
    val margin = (marginIn * dpi).toInt()
    val headerH = headerHPx
    val footerH = footerHPx
    private val contentTop = margin + headerH
    private val footerTop = pageH - margin - footerH

    override val cw = pageW - margin * 2
    override val ch = (footerTop - contentTop).coerceAtLeast(dpi / 4)

    override var curY = 0
        private set
    override val remaining: Int get() = ch - curY

    private var ops = mutableListOf<(Canvas) -> Unit>()

    /** 页眉/页脚固定层（坐标系：内容区左上角，原点在各自区域顶部）。排版前设置。 */
    var headerOps: List<(Canvas) -> Unit> = emptyList()
    var footerOps: List<(Canvas) -> Unit> = emptyList()

    val defs = mutableListOf<PageDef>()

    init { defs.add(buildDef()) }

    private fun compose(c: Canvas, body: List<(Canvas) -> Unit>) {
        c.save()
        c.translate(margin.toFloat(), margin.toFloat())
        headerOps.forEach { it(c) }
        c.restore()
        c.save()
        c.translate(margin.toFloat(), contentTop.toFloat())
        body.forEach { it(c) }
        c.restore()
        c.save()
        c.translate(margin.toFloat(), footerTop.toFloat())
        footerOps.forEach { it(c) }
        c.restore()
    }

    private fun buildDef(): PageDef =
        PageDef(widthIn, heightIn) { c -> compose(c, ops) }

    // 立即冻结当前 ops 列表（lambda 延迟执行，若直接写 ops.toList()，
    // 渲染时才求值会读到换页后的新列表，导致每页都画成最后一页）。
    private fun snapshot(): PageDef {
        val frozen = ops.toList()
        return PageDef(widthIn, heightIn) { c -> compose(c, frozen) }
    }

    /** 强制换页 */
    override fun pageBreak() {
        defs[defs.lastIndex] = snapshot()
        ops = mutableListOf()
        curY = 0
        defs.add(buildDef())
    }

    /** 若剩余高度不足 h 则换页；返回本块绘制的起点 y */
    override fun ensure(h: Int): Int {
        if (h in 1..ch && curY + h > ch) pageBreak()
        return curY
    }

    override fun draw(op: (Canvas) -> Unit) { ops.add(op) }

    /** 追加垂直间距 */
    override fun gap(h: Int) { curY += h.coerceAtLeast(0) }

    /** 结束排版，取页面定义 */
    fun finish(): List<PageDef> {
        defs[defs.lastIndex] = snapshot()
        return defs
    }
}

/**
 * 单页、不分页的固定层，用于把页眉/页脚块排版到一个有限区域并取出绘制指令与高度。
 * 区域高度 [maxHPx] 取足够大（页眉/页脚一般较短，不会触发换页）。
 */
class StaticLayer(
    override val dpi: Int,
    widthPx: Int,
    private val maxHPx: Int
) : FlowContext {
    override val cw = widthPx
    override val ch = maxHPx
    private var cur = 0
    override val curY: Int get() = cur
    override val remaining: Int get() = ch - cur
    private val ops = mutableListOf<(Canvas) -> Unit>()

    override fun pageBreak() { /* 固定层不跨页 */ }
    override fun ensure(h: Int): Int = cur
    override fun draw(op: (Canvas) -> Unit) { ops.add(op) }
    override fun gap(h: Int) { cur += h.coerceAtLeast(0) }

    /** 取绘制指令（原点在该层左上角）与实际内容高度 */
    fun result(): Pair<List<(Canvas) -> Unit>, Int> = ops.toList() to cur
}


/** 把 StaticLayout 的 [l0,l1) 行绘制到 canvas 的 (0, y) 起 */
fun drawLayoutRange(
    canvas: Canvas,
    layout: StaticLayout,
    l0: Int,
    l1: Int,
    y: Int,
    top: Int = layout.getLineTop(l0)
) {
    val bottom = layout.getLineBottom(l1 - 1)
    canvas.save()
    canvas.translate(0f, y - top.toFloat())
    canvas.clipRect(0f, top.toFloat() - 1f, layout.width.toFloat(), bottom.toFloat() + 1f)
    layout.draw(canvas)
    canvas.restore()
}
