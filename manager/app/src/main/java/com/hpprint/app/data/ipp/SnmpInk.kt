package com.hpprint.app.data.ipp

import com.hpprint.app.data.model.hp.SupplyColor
import com.hpprint.app.data.model.hp.SupplyInfo
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * 纯 Kotlin SNMPv1 客户端：用标准 Printer MIB（RFC 1759）读取真实墨量/耗材。
 *
 * HP Smart 用 `libhpmpbsnmp.so` 走 SNMP 做发现/状态，墨量主路径是 IPP marker-levels + EWS REST；
 * 本类作为「IPP 未返回耗材」时的兜底，直接对打印机 161 端口发 GETNEXT 遍历 prtMarkerSuppliesEntry：
 *
 *  - 1.3.6.1.2.1.43.11.1.1.9  prtMarkerSuppliesLevel     → 剩余百分比（0~100）
 *  - 1.3.6.1.2.1.43.11.1.1.8  prtMarkerSuppliesMaxLevel   → 最大量
 *  - 1.3.6.1.2.1.43.11.1.1.5  prtMarkerSuppliesDescription → 颜色描述（如 "Black Cartridge"）
 *
 * 社区名默认 "public"（局域网打印机通行做法）；无 SNMP 的设备在短超时内无响应，优雅返回空列表。
 */
object SnmpInk {

    private const val COMMUNITY = "public"
    private const val MARKER_SUPPLIES_ENTRY = "1.3.6.1.2.1.43.11.1.1"

    // 列下标（相对 prtMarkerSuppliesEntry）——RFC 1759 布局
    private const val COL_DESCRIPTION = 5
    private const val COL_COLORANT = 6
    private const val COL_MAX_LEVEL = 8
    private const val COL_LEVEL = 9

    private const val MAX_WALK_STEPS = 64

    /** 读取一台打印机的所有墨量；失败/超时返回空列表，绝不抛异常。 */
    fun query(ip: String, port: Int = 161, timeoutMs: Int = 1800): List<SupplyInfo> {
        return try {
            val rows = walk(InetAddress.getByName(ip), port, timeoutMs)
            mapSupplies(rows)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ SNMP walk

    private data class SnmpValue(val tag: Int, val bytes: ByteArray) {
        val asInt: Int? get() = if (tag == 0x02) decodeInteger(bytes) else null
        val asString: String? get() = if (tag == 0x04) bytes.toString(Charsets.UTF_8) else null
    }

    /** row -> (col -> value) */
    private fun walk(addr: InetAddress, port: Int, timeoutMs: Int): Map<Int, Map<Int, SnmpValue>> {
        val rows = mutableMapOf<Int, MutableMap<Int, SnmpValue>>()
        val entry = oidToInts(MARKER_SUPPLIES_ENTRY)
        var requestId = 1
        var current = entry

        repeat(MAX_WALK_STEPS) {
            val resp = getNext(addr, port, current, requestId, timeoutMs) ?: return rows
            requestId++
            val (oid, value) = resp
            if (!startsWith(oid, entry) || oid.size < entry.size + 2) return rows
            val col = oid[entry.size]
            val row = oid[entry.size + 1]
            rows.getOrPut(row) { mutableMapOf() }[col] = value
            current = oid
        }
        return rows
    }

    private fun getNext(
        addr: InetAddress,
        port: Int,
        oid: IntArray,
        requestId: Int,
        timeoutMs: Int,
    ): Pair<IntArray, SnmpValue>? {
        val request = buildGetNext(requestId, oid)
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val send = DatagramPacket(request, request.size, InetSocketAddress(addr, port))
            socket.send(send)
            val buf = ByteArray(8192)
            val recv = DatagramPacket(buf, buf.size)
            socket.receive(recv)
            return parseGetResponse(recv.data, recv.length)
        }
    }

    // ------------------------------------------------------------ BER 编码

    private fun buildGetNext(requestId: Int, oid: IntArray): ByteArray {
        val body = ByteArrayOutputStream()

        writeTlv(body, 0x02, encodeInteger(0))                // SNMPv1 version = 0
        writeTlv(body, 0x04, COMMUNITY.toByteArray(Charsets.UTF_8))

        val pdu = ByteArrayOutputStream()
        writeTlv(pdu, 0x02, encodeInteger(requestId))         // request-id
        writeTlv(pdu, 0x02, encodeInteger(0))                 // error-status
        writeTlv(pdu, 0x02, encodeInteger(0))                 // error-index

        val vb = ByteArrayOutputStream()
        writeTlv(vb, 0x06, encodeOidValue(oid))
        writeTlv(vb, 0x05, ByteArray(0))                       // NULL
        val vbl = ByteArrayOutputStream()
        writeTlv(vbl, 0x30, vb.toByteArray())                  // varbind
        writeTlv(pdu, 0x30, vbl.toByteArray())                 // varbind-list

        writeTlv(body, 0xA1, pdu.toByteArray())                 // GetNextRequest-PDU

        val msg = ByteArrayOutputStream()
        writeTlv(msg, 0x30, body.toByteArray())
        return msg.toByteArray()
    }

    private fun writeTlv(out: ByteArrayOutputStream, tag: Int, content: ByteArray) {
        out.write(tag)
        writeLength(out, content.size)
        out.write(content)
    }

    private fun writeLength(out: ByteArrayOutputStream, len: Int) {
        if (len < 0x80) {
            out.write(len)
            return
        }
        val bytes = mutableListOf<Int>()
        var v = len
        while (v > 0) {
            bytes.add(0, v and 0xff)
            v = v ushr 8
        }
        out.write(0x80 or bytes.size)
        bytes.forEach { out.write(it) }
    }

    private fun encodeInteger(value: Int): ByteArray =
        java.math.BigInteger.valueOf(value.toLong()).toByteArray()

    private fun encodeOidValue(oid: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        val first = oid[0] * 40 + (if (oid.size > 1) oid[1] else 0)
        writeVarInt(out, first)
        for (i in 2 until oid.size) writeVarInt(out, oid[i])
        return out.toByteArray()
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        val buf = mutableListOf<Int>()
        var v = value
        do {
            buf.add(0, v and 0x7f)
            v = v ushr 7
        } while (v > 0)
        for (i in buf.indices) {
            out.write(if (i < buf.size - 1) buf[i] or 0x80 else buf[i])
        }
    }

    // ------------------------------------------------------------ BER 解码

    private fun parseGetResponse(data: ByteArray, len: Int): Pair<IntArray, SnmpValue>? {
        if (len < 2) return null
        val r = Reader(data, len)
        if (r.readTag() != 0x30) return null
        r.readLength()              // 外层 SEQUENCE 长度（按子元素顺序继续解析）
        r.skipElement()             // version
        r.skipElement()             // community
        if (r.readTag() != 0xA2) return null  // 非 GetResponse
        r.readLength()              // PDU 长度
        r.skipElement()             // request-id
        r.skipElement()             // error-status
        r.skipElement()             // error-index
        r.skipElement()             // varbind-list
        r.skipElement()             // varbind SEQUENCE
        if (r.readTag() != 0x06) return null
        val oidLen = r.readLength()
        val oid = decodeOidValue(r.data.copyOfRange(r.pos, r.pos + oidLen))
        r.pos += oidLen
        val valTag = r.readTag()
        val valLen = r.readLength()
        val bytes = r.data.copyOfRange(r.pos, r.pos + valLen)
        r.pos += valLen
        return oid to SnmpValue(valTag, bytes)
    }

    private class Reader(val data: ByteArray, val max: Int) {
        var pos = 0
        fun readTag(): Int {
            if (pos >= max) return -1
            return data[pos++].toInt() and 0xff
        }
        fun readLength(): Int {
            if (pos >= max) return 0
            val b = data[pos++].toInt() and 0xff
            if (b < 0x80) return b
            var len = 0
            for (i in 0 until (b and 0x7f)) {
                len = (len shl 8) or (data[pos++].toInt() and 0xff)
            }
            return len
        }
        fun skipElement() {
            readTag()
            pos += readLength()
        }
    }

    private fun decodeInteger(bytes: ByteArray): Int {
        var v = 0
        for (b in bytes) v = (v shl 8) or (b.toInt() and 0xff)
        return v
    }

    private fun decodeOidValue(bytes: ByteArray): IntArray {
        val arcs = mutableListOf<Int>()
        var v = 0
        var completed = false
        for (b in bytes) {
            v = (v shl 7) or (b.toInt() and 0x7f)
            completed = (b.toInt() and 0x80) == 0
            if (completed) {
                arcs.add(v)
                v = 0
            }
        }
        if (arcs.isEmpty()) return IntArray(0)
        val first = arcs[0]
        val x = when {
            first < 40 -> 0
            first < 80 -> 1
            else -> 2
        }
        val y = when (x) {
            0 -> first
            1 -> first - 40
            else -> first - 80
        }
        val rest = arcs.drop(1).toIntArray()
        return intArrayOf(x, y) + rest
    }

    private fun oidToInts(oid: String): IntArray = oid.split('.').map { it.toInt() }.toIntArray()

    private fun startsWith(oid: IntArray, prefix: IntArray): Boolean {
        if (oid.size < prefix.size) return false
        for (i in prefix.indices) if (oid[i] != prefix[i]) return false
        return true
    }

    // ------------------------------------------------------------ 墨量映射

    private fun mapSupplies(rows: Map<Int, Map<Int, SnmpValue>>): List<SupplyInfo> {
        if (rows.isEmpty()) return emptyList()
        val result = mutableListOf<SupplyInfo>()
        val sortedRows = rows.keys.sorted()
        sortedRows.forEach { row ->
            val cells = rows[row] ?: return@forEach
            // 优先读 level 列（9）；部分机型把它放在 max 列（8）附近，都兼容 0~100 校验。
            val level = validPercent(cells[COL_LEVEL]?.asInt) ?: validPercent(cells[COL_MAX_LEVEL]?.asInt)
                ?: return@forEach
            val desc = cells[COL_DESCRIPTION]?.asString.orEmpty()
            val colorant = cells[COL_COLORANT]?.asInt
            val color = inferColor(colorant, desc, row, sortedRows.size)
            result.add(
                SupplyInfo(
                    color = color,
                    name = desc.ifBlank { defaultName(color) },
                    levelPercent = level.coerceIn(0, 100),
                ),
            )
        }
        return result
    }

    private fun validPercent(v: Int?): Int? = v?.takeIf { it in 0..100 }

    private fun inferColor(colorant: Int?, desc: String, row: Int, total: Int): SupplyColor {
        val s = desc.lowercase()
        return when {
            "black" in s || "黑" in desc -> SupplyColor.BLACK
            "photo" in s || "照片" in desc -> SupplyColor.PHOTO_BLACK
            "cyan" in s || "青" in desc || "蓝" in desc -> SupplyColor.CYAN
            "magenta" in s || "品红" in desc || "洋红" in desc -> SupplyColor.MAGENTA
            "yellow" in s || "黄" in desc -> SupplyColor.YELLOW
            total == 1 -> SupplyColor.BLACK
            row == total - 1 -> SupplyColor.BLACK
            else -> when (colorant) {
                3, 9 -> SupplyColor.BLACK
                4, 6 -> SupplyColor.CYAN
                5, 7 -> SupplyColor.MAGENTA
                8 -> SupplyColor.YELLOW
                else -> when (row % 3) {
                    0 -> SupplyColor.CYAN
                    1 -> SupplyColor.MAGENTA
                    else -> SupplyColor.YELLOW
                }
            }
        }
    }

    private fun defaultName(color: SupplyColor): String = when (color) {
        SupplyColor.BLACK -> "Black"
        SupplyColor.CYAN -> "Cyan"
        SupplyColor.MAGENTA -> "Magenta"
        SupplyColor.YELLOW -> "Yellow"
        SupplyColor.PHOTO_BLACK -> "Photo Black"
    }
}