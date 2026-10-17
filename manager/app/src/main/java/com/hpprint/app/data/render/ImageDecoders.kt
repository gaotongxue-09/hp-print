package com.hpprint.app.data.render
import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Android 原生 BitmapFactory 不支持的图片格式的解码器：
 *  - EMF/WMF：Office 里这类"图"绝大多数是"位图容器"，绘图记录（STRETCHDIBITS /
 *    SETDIBITSTODEVICE / ALPHABLEND 等）中直接内嵌了完整 DIB（设备无关位图）。
 *    这里把内嵌 DIB 提取出来还原成 Bitmap；纯矢量（图表/公式/形状）无 DIB，返回 null。
 *  - SVG/SVGZ：用 AndroidSVG 渲染到位图（OOXML 中 SVG 通常还带 PNG 兜底，这里再兜一层）。
 */
object ImageDecoders {

    /** 主入口：返回解码后的位图，无法还原返回 null（调用方再生成占位图）。 */
    fun decode(data: ByteArray, reqLongEdge: Int): Bitmap? {
        if (data.size < 12) return null
        // emz/wmz/svgz 为 gzip 压缩的 emf/wmf/svg，先解包再按格式解码
        val buf = if (isGzip(data)) gunzip(data) ?: return null else data
        val raw = when {
            isEmf(buf) -> decodeEmf(buf)
            isWmf(buf) -> decodeWmf(buf)
            looksLikeSvg(buf) -> decodeSvg(buf, reqLongEdge)
            else -> null
        } ?: return null
        // 降采样到目标长边，控制内存
        val longEdge = maxOf(raw.width, raw.height)
        if (longEdge > reqLongEdge * 2) {
            val s = reqLongEdge.toFloat() / longEdge
            val w = (raw.width * s).toInt().coerceAtLeast(1)
            val h = (raw.height * s).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
            if (scaled !== raw) raw.recycle()
            return scaled
        }
        return raw
    }

    // ===================== 格式探测 =====================

    private fun isGzip(d: ByteArray): Boolean =
        d.size >= 2 && d[0] == 0x1F.toByte() && d[1] == 0x8B.toByte()

    private fun gunzip(d: ByteArray): ByteArray? = runCatching {
        GZIPInputStream(ByteArrayInputStream(d)).use { it.readBytes() }
    }.getOrNull()

    fun isEmf(d: ByteArray): Boolean =
        d.size >= 44 && d.le32(40) == 0x464D4520L   // " EMF" 小端

    fun isWmf(d: ByteArray): Boolean {
        if (d.size < 6) return false
        if (d.le32(0) == 0x9AC6CDD7L) return true      // 可放置 WMF 头
        // 标准 WMF：mtType(WORD)=1(MEMORY)/2(DISK)，mtHeaderSize(WORD)=9/3
        val mt = d.le16(0).toInt()
        val hs = d.le16(2).toInt()
        return (mt == 1 || mt == 2) && (hs == 9 || hs == 3)
    }

    fun looksLikeSvg(d: ByteArray): Boolean {
        var raw = d
        if (d.size >= 2 && d[0] == 0x1F.toByte() && d[1] == 0x8B.toByte()) {
            runCatching {
                raw = GZIPInputStream(ByteArrayInputStream(d)).use { it.readBytes() }
            }.onFailure { return false }
        }
        val n = minOf(raw.size, 4096)
        val head = String(raw, 0, n)
        return head.contains("<svg")
    }

    // ===================== EMF =====================

    // EMR 记录类型 → 其结构中 (offBmi, cbBmi, offBits, cbBits) 相对记录起点的偏移
    private val EMF_DIB_FIELDS = mapOf(
        76 to intArrayOf(84, 88, 92, 96),    // EMR_BITBLT
        77 to intArrayOf(84, 88, 92, 96),    // EMR_STRETCHBLT
        80 to intArrayOf(48, 52, 56, 60),    // EMR_SETDIBITSTODEVICE
        81 to intArrayOf(48, 52, 56, 60),    // EMR_STRETCHDIBITS
        114 to intArrayOf(84, 88, 92, 96),   // EMR_ALPHABLEND
        116 to intArrayOf(84, 88, 92, 96)    // EMR_TRANSPARENTBLT
    )

    private fun decodeEmf(data: ByteArray): Bitmap? {
        val candidates = ArrayList<Dib>()
        var pos = 0
        while (pos + 8 <= data.size) {
            val type = data.le32(pos).toInt()
            val recSize = data.le32(pos + 4).toInt()
            if (recSize < 8 || pos + recSize > data.size) break
            val f = EMF_DIB_FIELDS[type]
            if (f != null && f[0] + 16 <= recSize) {
                val offBmi = data.le32(pos + f[0]).toInt()
                val cbBmi = data.le32(pos + f[1]).toInt()
                val offBits = data.le32(pos + f[2]).toInt()
                val cbBits = data.le32(pos + f[3]).toInt()
                if (cbBmi in 40..recSize && offBmi in 8..recSize && offBmi + cbBmi <= recSize) {
                    val bmi = data.copyOfRange(pos + offBmi, pos + offBmi + cbBmi)
                    var bits: ByteArray? = null
                    if (offBits in 8..recSize && cbBits > 0 && offBits + cbBits <= recSize) {
                        bits = data.copyOfRange(pos + offBits, pos + offBits + cbBits)
                    }
                    buildDib(bmi, bits)?.let { candidates.add(it) }
                }
            }
            if (type == 14) break    // EMR_EOF
            pos += recSize
        }
        // 兜底：全文件扫描 DIB（EMF+ 或结构偏移不符时）
        if (candidates.isEmpty()) scanDib(data) { candidates.add(it) }
        return pickLargest(candidates)
    }

    private fun decodeWmf(data: ByteArray): Bitmap? {
        // WMF 记录布局随类型变化，直接全文件扫描内嵌 DIB 最稳
        val candidates = ArrayList<Dib>()
        scanDib(data) { candidates.add(it) }
        return pickLargest(candidates)
    }

    // ===================== DIB 提取 =====================

    private class Dib(val bmp: Bitmap, val area: Long)

    private fun pickLargest(list: List<Dib>): Bitmap? {
        if (list.isEmpty()) return null
        val best = list.maxByOrNull { it.area } ?: return null
        list.forEach { if (it !== best) it.bmp.recycle() }
        return best.bmp
    }

    /** 在整个字节流中按 BITMAPINFOHEADER 特征扫描 DIB（EMF/WMF 通用兜底）。 */
    private inline fun scanDib(data: ByteArray, emit: (Dib) -> Unit) {
        var i = 0
        val limit = data.size - 40
        while (i < limit) {
            val headerSize = data.le32(i).toInt()
            if (headerSize in setOf(40, 52, 56, 108, 124) && i + headerSize <= data.size) {
                val w = data.le32(i + 4).toInt()
                val h = data.le32(i + 8).toInt()
                val planes = data.le16(i + 12).toInt()
                val bpp = data.le16(i + 14).toInt()
                val comp = data.le32(i + 16).toInt()
                if (w in 1..50000 && (h in -50000..-1 || h in 1..50000) && planes == 1 &&
                    bpp in intArrayOf(1, 4, 8, 16, 24, 32) && comp in intArrayOf(0, 1, 2, 3, 6)
                ) {
                    val paletteBytes = paletteSize(headerSize, bpp, comp, data, i)
                    val bitsOff = i + headerSize + paletteBytes
                    if (bitsOff < data.size) {
                        val bmi = data.copyOfRange(i, bitsOff)
                        val bits = data.copyOfRange(bitsOff, data.size)
                        buildDib(bmi, bits)?.let { emit(it) }
                        // 跳过这块，避免重复命中
                        i = bitsOff + 1
                        continue
                    }
                }
            }
            i++
        }
    }

    /** 颜色表/位掩码占用的字节数。 */
    private fun paletteSize(headerSize: Int, bpp: Int, comp: Int, d: ByteArray, off: Int): Int {
        val clrUsed = d.le32(off + 32).toInt()
        when {
            bpp <= 8 -> {
                val n = if (clrUsed > 0) clrUsed else (1 shl bpp)
                return n * 4
            }
            bpp == 16 || bpp == 32 -> {
                if (comp == 3 && headerSize == 40) return 12   // BI_BITFIELDS：3 个 DWORD 掩码
                return 0
            }
            else -> return 0
        }
    }

    /**
     * 由 BITMAPINFOHEADER(+颜色表) 与像素数据还原 Bitmap。
     * 支持 32/24/8/16 位；4/1 位等极少见格式返回 null。
     */
    private fun buildDib(bmi: ByteArray, bitsIn: ByteArray?): Dib? {
        if (bmi.size < 40) return null
        val headerSize = bmi.le32(0).toInt()
        val w = bmi.le32(4).toInt()
        val hRaw = bmi.le32(8).toInt()
        val bpp = bmi.le16(14).toInt()
        val comp = bmi.le32(16).toInt()
        if (w <= 0 || hRaw == 0 || w > 50000) return null
        val topDown = hRaw < 0
        val h = abs(hRaw)
        if (h > 50000) return null

        val stride = ((w * bpp + 31) / 32) * 4
        val need = stride * h
        // bits 缺失时用"紧跟 header 之后"的布局
        val bits = bitsIn ?: ByteArray(0)
        if (bits.size < need) return null

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)

        // 位掩码
        var maskR = 0; var maskG = 0; var maskB = 0; var maskA = 0
        when (bpp) {
            32 -> when (comp) {
                3 -> {
                    maskR = bmi.le32(headerSize + 0).toInt()
                    maskG = bmi.le32(headerSize + 4).toInt()
                    maskB = bmi.le32(headerSize + 8).toInt()
                    if (maskR == 0 && maskG == 0 && maskB == 0) { maskR = 0xFF0000; maskG = 0xFF00; maskB = 0xFF }
                }
                else -> { maskR = 0xFF0000; maskG = 0xFF00; maskB = 0xFF; maskA = 0 } // BI_RGB：保留字节当不透明
            }
            16 -> when (comp) {
                3 -> {
                    maskR = bmi.le32(headerSize + 0).toInt()
                    maskG = bmi.le32(headerSize + 4).toInt()
                    maskB = bmi.le32(headerSize + 8).toInt()
                }
                else -> { maskR = 0x7C00; maskG = 0x03E0; maskB = 0x001F } // RGB555
            }
        }

        val shR = shiftFor(maskR); val shG = shiftFor(maskG)
        val shB = shiftFor(maskB); val shA = shiftFor(maskA)
        val palette = Array(1 shl bpp.coerceAtMost(8)) { 0 }
        if (bpp <= 8) {
            val clrUsed = bmi.le32(32).toInt()
            val n = if (clrUsed > 0) clrUsed.coerceAtMost(palette.size) else palette.size
            for (i in 0 until n) {
                val q = headerSize + i * 4
                if (q + 4 <= bmi.size) {
                    val bb = bmi[q].toInt() and 0xFF
                    val gg = bmi[q + 1].toInt() and 0xFF
                    val rr = bmi[q + 2].toInt() and 0xFF
                    palette[i] = 0xFF000000.toInt() or (rr shl 16) or (gg shl 8) or bb
                }
            }
        }

        for (y in 0 until h) {
            val srcRow = if (topDown) y else h - 1 - y
            val rowBase = srcRow * stride
            val outBase = y * w
            var x = 0
            while (x < w) {
                val color: Int = when (bpp) {
                    32 -> {
                        val o = rowBase + x * 4
                        val b = bits[o].toInt() and 0xFF
                        val g = bits[o + 1].toInt() and 0xFF
                        val r = bits[o + 2].toInt() and 0xFF
                        val a = bits[o + 3].toInt() and 0xFF
                        if (comp == 3) {
                            val v = bits.le32(o).toInt()
                            applyMasks(v, maskR, maskG, maskB, maskA, shR, shG, shB, shA)
                        } else {
                            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                        }
                    }
                    24 -> {
                        val o = rowBase + x * 3
                        val b = bits[o].toInt() and 0xFF
                        val g = bits[o + 1].toInt() and 0xFF
                        val r = bits[o + 2].toInt() and 0xFF
                        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    16 -> {
                        val o = rowBase + x * 2
                        val v = (bits[o].toInt() and 0xFF) or ((bits[o + 1].toInt() and 0xFF) shl 8)
                        applyMasks(v, maskR, maskG, maskB, 0, shR, shG, shB, 0)
                    }
                    8 -> {
                        val v = bits[rowBase + x].toInt() and 0xFF
                        palette[v.coerceIn(0, palette.size - 1)]
                    }
                    else -> return null
                }
                px[outBase + x] = color
                x++
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return Dib(out, w.toLong() * h)
    }

    private fun shiftFor(mask: Int): Int {
        if (mask == 0) return 0
        return Integer.numberOfTrailingZeros(mask)
    }

    private fun applyMasks(
        v: Int, mR: Int, mG: Int, mB: Int, mA: Int,
        shR: Int, shG: Int, shB: Int, shA: Int
    ): Int {
        fun expand(value: Int, mask: Int, shift: Int): Int {
            if (mask == 0) return 255
            var c = (value and mask) ushr shift
            val bits = Integer.bitCount(mask)
            if (bits < 8) c = c shl (8 - bits) or (c shr bits)
            return c.coerceIn(0, 255)
        }
        val r = expand(v, mR, shR); val g = expand(v, mG, shG)
        val b = expand(v, mB, shB)
        val a = if (mA != 0) expand(v, mA, shA) else 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ===================== SVG =====================

    fun decodeSvg(data: ByteArray, reqLongEdge: Int): Bitmap? {
        return runCatching {
            var raw = data
            if (data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
                raw = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
            }
            val svg = SVG.getFromInputStream(ByteArrayInputStream(raw))
            var baseW = svg.documentWidth
            var baseH = svg.documentHeight
            if (baseW <= 0f || baseH <= 0f) {
                val vb = svg.documentViewBox
                if (vb != null && vb.width() > 0f && vb.height() > 0f) {
                    baseW = vb.width(); baseH = vb.height()
                } else { baseW = 100f; baseH = 100f }
            }
            val target = reqLongEdge.coerceIn(16, 8000).toFloat()
            val scale = target / maxOf(baseW, baseH)
            val w = (baseW * scale).toInt().coerceIn(1, 8000)
            val h = (baseH * scale).toInt().coerceIn(1, 8000)
            val picture = svg.renderToPicture(w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawPicture(picture)
            bmp
        }.getOrNull()
    }
}

// ---- 小端读取 ----

private fun ByteArray.le16(off: Int): Int =
    if (off + 1 < size) (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)
    else 0

private fun ByteArray.le32(off: Int): Long {
    if (off + 3 >= size) return 0L
    var v = 0L
    for (i in 0..3) v = v or ((this[off + i].toLong() and 0xFF) shl (i * 8))
    return v
}
