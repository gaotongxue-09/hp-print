package com.hpprint.app.data.render
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * 旧版 Office（doc/xls/ppt，OLE2 复合二进制）与 RTF 的文本提取。
 *
 * 这些格式没有 Office Open XML 的 zip+xml 结构，这里实现：
 *  - CFBF（Compound File Binary）容器解析；
 *  - doc：FIB + piece table（fcClx/PlcPcd），权威提取正文；
 *  - xls：BIFF8（BOUNDSHEET / SST+CONTINUE / LABELSST / LABEL / NUMBER / RK）；
 *  - ppt：递归容器，收集 TextCharsAtom / TextBytesAtom；
 *  - RTF：控制字 / 编码 / unicode 解析。
 *
 * 旧格式中的图片与精细样式无法在不引入大型库的前提下还原，文本与表格结构完整保留；
 * 需要与原文件完全一致的版面时，仍可在 WPS 另存 PDF 打印（PDF 管线为高保真）。
 */
object LegacyOffice {

    // ===================== CFBF 容器 =====================

    private class Cfbf(val data: ByteArray) {
        val sectorSize: Int
        private val fat: IntArray
        private val entries: List<Entry>
        private val miniStream: ByteArray
        private val miniFat: IntArray
        private val cutoff: Long

        private class Entry(
            val name: String, val type: Int, val start: Int, val size: Long
        )

        init {
            require(data[0] == 0xD0.toByte() && data[1] == 0xCF.toByte()) {
                "不是 OLE2 复合文档"
            }
            val sectorShift = u16(data, 30)
            sectorSize = 1 shl sectorShift
            val firstDir = u32(data, 48).toInt()
            cutoff = u32(data, 56).let { if (it == 0L) 4096 else it }
            val firstMiniFat = u32(data, 60).toInt()
            val numMiniFat = u32(data, 64).toInt()
            val firstDifat = u32(data, 68).toInt()
            val numDifat = u32(data, 72).toInt()

            // DIFAT → FAT sectors
            val fatSectors = ArrayList<Int>()
            for (i in 0 until 109) {
                val v = u32(data, 76 + i * 4).toInt()
                if (v in 0..0xFFFFFFF0) fatSectors.add(v)
            }
            var difSector = firstDifat
            val perDif = sectorSize / 4 - 1
            repeat(numDifat) {
                if (difSector < 0 || difSector >= 0xFFFFFFF0) return@repeat
                for (i in 0 until perDif) {
                    val v = sectorU32(data, sectorSize, difSector, i * 4).toInt()
                    if (v in 0..0xFFFFFFF0) fatSectors.add(v)
                }
                difSector = sectorU32(data, sectorSize, difSector, sectorSize - 4).toInt()
            }

            // FAT
            val entriesPer = sectorSize / 4
            fat = IntArray(fatSectors.size * entriesPer)
            fatSectors.forEachIndexed { idx, s ->
                for (i in 0 until entriesPer)
                    fat[idx * entriesPer + i] =
                        sectorU32(data, sectorSize, s, i * 4).toInt()
            }

            // 目录
            val dirData = readChain(firstDir)
            entries = (0 until dirData.size / 128).mapNotNull { off ->
                val nameLen = u16(dirData, off + 64)
                if (nameLen <= 0) return@mapNotNull null
                val name = String(
                    dirData, off, (nameLen - 2).coerceIn(0, 62), Charsets.UTF_16LE
                )
                val type = dirData[off + 66].toInt() and 0xFF
                val start = u32(dirData, off + 116).toInt()
                val size = u32(dirData, off + 120)
                Entry(name, type, start, size)
            }

            // 根 → mini stream
            val root = entries.firstOrNull { it.type == 5 }
            miniStream = if (root != null && root.size > 0)
                readChain(root.start) else ByteArray(0)

            // mini FAT
            miniFat = if (numMiniFat > 0 && firstMiniFat >= 0) {
                val raw = readChain(firstMiniFat)
                IntArray(raw.size / 4) { i ->
                    ((raw[i * 4].toInt() and 0xFF) or
                        ((raw[i * 4 + 1].toInt() and 0xFF) shl 8) or
                        ((raw[i * 4 + 2].toInt() and 0xFF) shl 16) or
                        ((raw[i * 4 + 3].toInt() and 0xFF) shl 24))
                }
            } else IntArray(0)
        }

        fun stream(name: String): ByteArray? {
            val e = entries.firstOrNull { it.name == name && it.type == 2 }
                ?: return null
            if (e.size < cutoff) {
                val len = e.size.toInt()
                val out = ByteArray(len)
                var s = e.start; var off = 0
                while (s >= 0 && s < 0xFFFFFFF0 && off < len) {
                    val n = minOf(64, len - off)
                    if (s * 64 + n <= miniStream.size)
                        System.arraycopy(miniStream, s * 64, out, off, n)
                    off += n
                    s = if (s < miniFat.size) miniFat[s] else ENDOF
                }
                return out
            }
            return readChain(e.start, e.size)
        }

        fun streamsWithPrefix(prefix: String): List<String> =
            entries.filter { it.type == 2 && it.name.startsWith(prefix) }
                .map { it.name }

        private fun readChain(start: Int, limit: Long = -1): ByteArray {
            val out = ByteArrayOutputStream()
            var s = start
            var written = 0L
            while (s >= 0 && s < 0xFFFFFFF0) {
                val off = headerSize + s.toLong() * sectorSize
                if (off + sectorSize > data.size) break
                var n = sectorSize
                if (limit >= 0 && written + n > limit) n = (limit - written).toInt()
                out.write(data, off.toInt(), n)
                written += n
                s = if (s < fat.size) fat[s] else ENDOF
            }
            return out.toByteArray()
        }
    }

    private const val ENDOF = -2
    private const val headerSize = 512

    private fun u16(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8))

    private fun u32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 0xFF) or ((b[p + 1].toLong() and 0xFF) shl 8) or
            ((b[p + 2].toLong() and 0xFF) shl 16 or ((b[p + 3].toLong() and 0xFF) shl 24)))

    private fun sectorU32(
        data: ByteArray, sectorSize: Int, sector: Int, inOff: Int
    ): Long = u32(data, headerSize + sector * sectorSize + inOff)

    // ===================== doc =====================

    fun extractDoc(bytes: ByteArray): String {
        val cf = Cfbf(bytes)
        val word = cf.stream("WordDocument")
            ?: throw IllegalArgumentException("无法读取该 doc（缺少 WordDocument 流）")

        val fWhich = word.getOrElse(0x000B) { 0 }.toInt() and 0x02 != 0
        val tableNames = if (fWhich) listOf("1Table", "0Table") else listOf("0Table", "1Table")

        var text: String? = null
        for (tn in tableNames) {
            val table = cf.stream(tn) ?: continue
            text = try {
                parsePieceTable(word, table)
            } catch (_: Exception) { null }
            if (!text.isNullOrBlank()) break
        }
        val out = text?.let { cleanWordText(it) }
        return out?.takeIf { it.isNotBlank() }
            ?: heuristicUtf16(word)
            ?: throw IllegalArgumentException("无法提取该 doc 的文字，建议另存为 PDF")
    }

    private fun parsePieceTable(word: ByteArray, table: ByteArray): String {
        if (0x01A6 + 4 > word.size) throw IllegalStateException("FIB 过短")
        val fcClx = u32(word, 0x01A2)
        val lcbClx = u32(word, 0x01A6)
        if (fcClx <= 0 || lcbClx <= 0 || fcClx + lcbClx > table.size)
            throw IllegalStateException("Clx 越界")

        var pos = fcClx.toInt()
        val end = pos + lcbClx.toInt()
        var plcStart = -1; var plcLen = -1
        while (pos < end) {
            when (table[pos].toInt() and 0xFF) {
                0x01 -> {
                    val cb = u16(table, pos + 1)
                    pos += 3 + cb
                }
                0x02 -> {
                    val lcb = u32(table, pos + 1).toInt()
                    plcStart = pos + 5
                    plcLen = lcb
                    pos = end
                }
                else -> throw IllegalStateException("Clx opcode 异常")
            }
        }
        if (plcStart < 0) throw IllegalStateException("无 Pcdt")

        // PlcPcd：n = (lcb-4)/12
        val n = (plcLen - 4) / 12
        val cps = IntArray(n + 1) { u32(table, plcStart + it * 4).toInt() }
        val pcdBase = plcStart + (n + 1) * 4

        val sb = StringBuilder()
        for (i in 0 until n) {
            val pcdOff = pcdBase + i * 8
            val fc = u32(table, pcdOff + 2)
            val compressed = (fc and 0x40000000L) != 0L
            val charCount = cps[i + 1] - cps[i]
            if (compressed) {
                val start = (fc and 0x3FFFFFFFL).toInt() / 2
                val seg = ByteArray(charCount)
                if (start >= 0 && start + charCount <= word.size)
                    System.arraycopy(word, start, seg, 0, charCount)
                sb.append(String(seg, cp1252))
            } else {
                val start = (fc and 0x3FFFFFFFL).toInt()
                val seg = ByteArray(charCount * 2)
                if (start >= 0 && start + charCount * 2 <= word.size)
                    System.arraycopy(word, start, seg, 0, charCount * 2)
                sb.append(String(seg, Charsets.UTF_16LE))
            }
        }
        return sb.toString()
    }

    private fun cleanWordText(raw: String): String {
        val sb = StringBuilder()
        var prev = 0.toChar()
        raw.forEach { ch ->
            when {
                ch == '\r' -> sb.append('\n')
                ch == 0x07.toChar() ->
                    // 单元格分隔后紧跟行分隔：07 07 → tab+换行
                    if (prev == 0x07.toChar()) sb.append('\n') else sb.append('\t')
                ch == 0x0B.toChar() || ch == 0x0C.toChar() -> sb.append('\n')
                ch == '\t' || ch == '\n' -> sb.append(ch)
                ch.code >= 32 -> sb.append(ch)
                else -> {}
            }
            prev = ch
        }
        return sb.toString().trim()
    }

    // ===================== xls =====================

    fun extractXls(bytes: ByteArray): String {
        val cf = Cfbf(bytes)
        val wb = cf.stream("Workbook") ?: cf.stream("Book")
            ?: throw IllegalArgumentException("无法读取该 xls（缺少 Workbook 流）")

        // 收集 records
        val records = mutableListOf<BiffRec>()
        var p = 0
        while (p + 4 <= wb.size) {
            val type = u16(wb, p)
            val len = u16(wb, p + 2)
            if (p + 4 + len > wb.size) break
            records.add(BiffRec(type, wb.copyOfRange(p + 4, p + 4 + len)))
            p += 4 + len
        }

        // SST（含 CONTINUE）
        val sst = mutableListOf<String>()
        val sstIdx = records.indexOfFirst { it.type == 0x00FC }
        if (sstIdx >= 0) {
            val continues = records.subList(sstIdx + 1, records.size)
                .takeWhile { it.type == 0x003C }
            parseSst(records[sstIdx].data, continues.map { it.data }, sst)
        }

        // sheet 名（BOUNDSHEET）
        val sheetNames = mutableListOf<String>()
        records.forEach { r ->
            if (r.type == 0x0085) {
                val nameLen = r.data.getOrElse(6) { 0 }.toInt() and 0xFF
                val flags = r.data.getOrElse(7) { 0 }.toInt() and 0xFF
                val hi = flags and 0x01 != 0
                val name = if (hi)
                    String(r.data, 8, nameLen * 2, Charsets.UTF_16LE)
                else String(r.data, 8, nameLen, cp1252)
                sheetNames.add(name)
            }
        }

        // 单元格：row → col → text
        val grid = sortedMapOf<Int, MutableMap<Int, String>>()
        fun put(row: Int, col: Int, v: String) {
            grid.getOrPut(row) { sortedMapOf() }[col] = v
        }
        records.forEach { r ->
            when (r.type) {
                0x00FD -> { // LABELSST
                    val row = u16(r.data, 0); val col = u16(r.data, 2)
                    val idx = u32(r.data, 6).toInt()
                    sst.getOrNull(idx)?.let { put(row, col, it) }
                }
                0x0204 -> { // LABEL
                    val row = u16(r.data, 0); val col = u16(r.data, 2)
                    val cch = u16(r.data, 6)
                    val grbit = u16(r.data, 8)
                    val hi = grbit and 0x01 != 0
                    val v = if (hi)
                        String(r.data, 10, cch * 2, Charsets.UTF_16LE)
                    else String(r.data, 10, cch, cp1252)
                    put(row, col, v)
                }
                0x0203 -> { // NUMBER
                    val row = u16(r.data, 0); val col = u16(r.data, 2)
                    val d = Double.fromBits(u64(r.data, 6))
                    put(row, col, numToText(d))
                }
                0x027E -> { // RK
                    val row = u16(r.data, 0); val col = u16(r.data, 2)
                    decodeRk(u32(r.data, 6))?.let { put(row, col, numToText(it)) }
                }
                0x00BD -> { // MULRK
                    val row = u16(r.data, 0)
                    val colFirst = u16(r.data, 2)
                    val colLast = u16(r.data, r.data.size - 2)
                    var off = 4
                    for (c in colFirst..colLast) {
                        if (off + 6 > r.data.size) break
                        decodeRk(u32(r.data, off + 2))?.let { put(row, c, numToText(it)) }
                        off += 6
                    }
                }
            }
        }

        if (grid.isEmpty()) {
            return heuristicUtf16(wb)
                ?: throw IllegalArgumentException("无法提取该 xls 的内容，建议另存为 PDF")
        }

        val sb = StringBuilder()
        val rMin = grid.keys.min(); val rMax = grid.keys.max()
        var cMax = 0
        grid.values.forEach { cMax = maxOf(cMax, it.keys.maxOrNull() ?: 0) }
        for (row in rMin..rMax) {
            val cells = grid[row]
            if (cells == null) { sb.append('\n'); continue }
            val parts = (0..cMax).map { cells[it] ?: "" }
            sb.append(parts.joinToString("\t")).append('\n')
        }
        return sb.toString().trim()
    }

    private data class BiffRec(val type: Int, val data: ByteArray)

    private fun parseSst(
        first: ByteArray,
        continues: List<ByteArray>,
        out: MutableList<String>
    ) {
        if (first.size < 8) return
        val numUnique = u32(first, 4).toInt()
        // 逻辑流：first[8..] + 每个 continue（continue 首字节是 options，保留）
        val log = ByteArrayOutputStream()
        log.write(first, 8, first.size - 8)
        continues.forEach { log.write(it) }
        val b = log.toByteArray()

        var pos = 0
        repeat(numUnique.coerceAtMost(65535)) {
            if (pos + 3 > b.size) return
            val cch = u16(b, pos)
            val flags = b[pos + 2].toInt() and 0xFF
            pos += 3
            val fExt = flags and 0x04 != 0
            val fRich = flags and 0x08 != 0
            var cRun = 0
            if (fRich) {
                if (pos + 2 > b.size) return
                cRun = u16(b, pos); pos += 4
            }
            if (fExt) {
                if (pos + 4 > b.size) return
                val cb = u32(b, pos).toInt(); pos += 4 + cb
            }
            val high = flags and 0x01 != 0
            val text = if (high) {
                val n = cch * 2
                if (pos + n > b.size) return
                String(b, pos, n, Charsets.UTF_16LE)
            } else {
                if (pos + cch > b.size) return
                String(b, pos, cch, cp1252)
            }
            pos += if (high) cch * 2 else cch
            if (fRich) pos += cRun * 4
            out.add(text)
        }
    }

    private fun decodeRk(code: Long): Double? {
        val mult100 = code and 0x01L != 0L
        val isInt = code and 0x02L != 0L
        var v: Double
        if (isInt) {
            val raw = (code shr 2).toInt()
            v = raw.toDouble()
        } else {
            val bits = (code and 0xFFFFFFFCL) shl 32
            v = Double.fromBits(bits)
        }
        if (mult100) v /= 100.0
        return v
    }

    private fun numToText(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString()
        else XNumber.roundStr(d, 6).trimEnd('0').trimEnd('.')

    private fun u64(b: ByteArray, p: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[p + i].toLong() and 0xFF)
        return v
    }

    // ===================== ppt =====================

    fun extractPpt(bytes: ByteArray): String {
        val cf = Cfbf(bytes)
        val stream = cf.stream("PowerPoint Document")
            ?: throw IllegalArgumentException("无法读取该 ppt（缺少 PowerPoint Document 流）")

        val texts = mutableListOf<String>()
        fun walk(off: Int, len: Int) {
            var p = off
            val end = off + len
            while (p + 8 <= end) {
                val verInstance = u16(stream, p)
                val type = u16(stream, p + 2)
                val recLen = u32(stream, p + 4).toInt()
                val body = p + 8
                if (body + recLen > end) break
                val isContainer = (verInstance and 0x000F) == 0x000F
                when (type) {
                    0x0FA0 -> // TextCharsAtom
                        if (recLen >= 2)
                            texts.add(String(stream, body, recLen, Charsets.UTF_16LE))
                    0x0FA8 -> // TextBytesAtom
                        texts.add(String(stream, body, recLen, cp1252))
                }
                if (isContainer) walk(body, recLen)
                p = body + recLen
            }
        }
        walk(0, stream.size)

        val out = texts.map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n") { it }
        return out.ifBlank {
            heuristicUtf16(stream)
                ?: throw IllegalArgumentException("无法提取该 ppt 的文字，建议另存为 PDF")
        }
    }

    // ===================== RTF =====================

    fun extractRtf(bytes: ByteArray): String {
        val raw = String(bytes, cp1252)
        var cpg = 936   // 中文环境默认 GBK
        val sb = StringBuilder()
        val hex = ByteArrayOutputStream()
        fun flushHex() {
            if (hex.size() > 0) {
                val cs: Charset = when (cpg) {
                    65001 -> Charsets.UTF_8
                    1252 -> cp1252
                    else -> Charset.forName("GBK")
                }
                sb.append(String(hex.toByteArray(), cs))
                hex.reset()
            }
        }

        var i = 0
        var skipGroupDepth = -1   // pict/fonttbl 等跳过组
        var depth = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '{' -> {
                    depth++
                    i++
                }
                c == '}' -> {
                    flushHex()
                    if (skipGroupDepth == depth) skipGroupDepth = -1
                    depth--; i++
                }
                skipGroupDepth >= 0 -> i++
                c == '\\' -> {
                    // 控制字
                    if (i + 1 >= raw.length) break
                    val c1 = raw[i + 1]
                    if (!c1.isLetter()) {
                        // 转义符号或 \'xx
                        when (c1) {
                            '\'' -> {
                                val hh = raw.substring(i + 2, i + 4)
                                hh.toIntOrNull(16)?.let { hex.write(it) }
                                i += 4
                            }
                            '\\' -> { flushHex(); sb.append('\\'); i += 2 }
                            '{' -> { flushHex(); sb.append('{'); i += 2 }
                            '}' -> { flushHex(); sb.append('}'); i += 2 }
                            else -> { i += 2 }
                        }
                    } else {
                        var j = i + 1
                        val w0 = j
                        while (j < raw.length && raw[j].isLetter()) j++
                        val word = raw.substring(w0, j)
                        var num: String? = null
                        if (j < raw.length && (raw[j] == '-' || raw[j].isDigit())) {
                            var k = j
                            if (raw[k] == '-') k++
                            while (k < raw.length && raw[k].isDigit()) k++
                            num = raw.substring(j, k)
                            j = k
                        }
                        if (j < raw.length && raw[j] == ' ') j++   // 分隔空格
                        flushHex()
                        when (word) {
                            "par", "line" -> sb.append('\n')
                            "tab" -> sb.append('\t')
                            "u" -> {
                                var n = num?.toIntOrNull() ?: 0
                                if (n < 0) n += 65536
                                sb.append(n.toChar())
                                // 跳过 ANSI 替身（通常一个字符）
                                if (j < raw.length && raw[j] != '\\') j++
                            }
                            "ansicpg" -> cpg = num?.toIntOrNull() ?: cpg
                            "pict" -> skipGroupDepth = depth
                            "fonttbl", "colortbl", "stylesheet", "themedata",
                            "datastore", "object" -> skipGroupDepth = depth
                            "emdash" -> sb.append('—')
                            "endash" -> sb.append('–')
                            "lquote", "rquote" -> sb.append('\'')
                            "ldblquote", "rdblquote" -> sb.append('"')
                            "bullet" -> sb.append('•')
                            else -> {}
                        }
                        i = j
                    }
                }
                c == '\r' || c == '\n' -> i++   // RTF 软换行忽略
                else -> { flushHex(); sb.append(c); i++ }
            }
        }
        flushHex()
        val out = sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        if (out.isBlank())
            throw IllegalArgumentException("无法解析该 RTF，建议另存为 PDF/TXT")
        return out
    }

    // ===================== 启发式兜底 =====================

    /** UTF-16LE 扫描：提取连续可读片段（中英文/标点/换行） */
    private fun heuristicUtf16(b: ByteArray): String? {
        val sb = StringBuilder()
        var run = StringBuilder()
        fun flush() {
            val t = run.toString()
            if (t.length >= 2 && t.any { it.code >= 0x4E00 && it.code <= 0x9FFF ||
                it.isLetterOrDigit() })
                sb.append(t)
            run.setLength(0)
        }
        var i = 0
        while (i + 1 < b.size) {
            val code = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
            val ch = code.toChar()
            val ok = ch == '\n' || ch == '\t' || ch in ' '..'~' ||
                (code in 0x4E00..0x9FFF) || (code in 0x3000..0x303F) ||
                (code in 0xFF00..0xFFEF) || ch in '，'..'？'
            if (ok) run.append(ch) else flush()
            i += 2
        }
        flush()
        val out = sb.toString().trim()
        return out.ifBlank { null }
    }

    private val cp1252: Charset = Charset.forName("windows-1252")
}
