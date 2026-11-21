package com.hpprint.app.data.ipp

import com.hpprint.app.data.model.hp.SupplyColor
import com.hpprint.app.data.model.hp.SupplyInfo
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过打印机内嵌 Web 服务器（EWS）读取真实墨量。
 *
 * HP Smart 的墨量主路径之一即 EWS REST（见 `libhpmp.so` 中 `_get_ink_status(): REST enter`）：
 *   GET /DevMgmt/ProductStatusDyn.xml   —— 每支墨盒的剩余量（动态）
 *   GET /DevMgmt/ConsumableConfigDyn.xml —— 耗材颜色/型号（静态，作兜底）
 *
 * 这里用宽松正则解析 LEDM XML：每个 <...Consumable> 块里取
 *   <ConsumablePercentageLevelRemaining>0~100</...> 和 <ConsumableColorEnum>K/C/M/Y/PK</...>。
 * 拉不到（EWS 关闭/非 HP 机型）时返回空列表，绝不抛异常。
 */
object EwsInk {

    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 2500

    fun query(ip: String, port: Int = 80, timeoutMs: Int = 2500): List<SupplyInfo> {
        val dyn = fetch(ip, port, "/DevMgmt/ProductStatusDyn.xml", timeoutMs)
        if (!dyn.isNullOrBlank()) {
            val parsed = parseSupplies(dyn)
            if (parsed.isNotEmpty()) return parsed
        }
        val cfg = fetch(ip, port, "/DevMgmt/ConsumableConfigDyn.xml", timeoutMs)
        return if (cfg.isNullOrBlank()) emptyList() else parseSupplies(cfg)
    }

    private fun fetch(ip: String, port: Int, path: String, timeoutMs: Int): String? {
        return try {
            val url = URL("http://$ip:$port$path")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/xml,text/xml,*/*")
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            // 连接在 use/流关闭后自动释放
        }
    }

    private data class Consumable(val colorRaw: String?, val level: Int?)

    private fun parseSupplies(xml: String): List<SupplyInfo> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<SupplyInfo>()

        // 每个 Consumable 块（含命名空间前缀，如 <psdyn:Consumable>）
        val blockRegex = Regex("""<[^>]*Consumable[^>]*>.*?</[^>]*Consumable[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        for (block in blockRegex.findAll(xml)) {
            val text = block.value
            val level = valueIn(text, "ConsumablePercentageLevelRemaining")?.toIntOrNull()
                ?: valueIn(text, "PercentageLevelRemaining")?.toIntOrNull()
            val levelPercent = level?.takeIf { it in 0..100 } ?: continue
            val colorRaw = valueIn(text, "ConsumableColorEnum")
                ?: valueIn(text, "MarkerColor")
                ?: valueIn(text, "ColorEnum")
            val color = mapColor(colorRaw, text)
            if (color == null) {
                // 无颜色信息时按顺序推断：单墨盒默认黑，多墨盒最后一个是黑。
                if (result.isEmpty()) result.add(SupplyInfo(SupplyColor.BLACK, "Black", levelPercent))
                continue
            }
            result.add(SupplyInfo(color, defaultName(color), levelPercent))
        }

        // 去重（ConsumableConfigDyn 可能返回静态 + 动态两份）
        return result.distinctBy { it.color }
    }

    /** 从块内提取形如 <...name...>value</...name...> 的文本值。 */
    private fun valueIn(block: String, name: String): String? {
        // 匹配 <任意前缀:name ...> value </任意前缀:name>，或 <name> value </name>
        val re = Regex("""<$name(?:\s[^>]*)?>([^<]+)</$name>|<\w*[:.]$name(?:\s[^>]*)?>([^<]+)</\w*[:.]$name>""")
        val m = re.find(block) ?: return null
        return (m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: m.groupValues.getOrNull(2))?.trim()
    }

    private fun mapColor(raw: String?, block: String): SupplyColor? {
        val fallback = block
        return when {
            raw == null -> null
            raw.equals("K", true) || "Black" in raw || "Black" in fallback -> SupplyColor.BLACK
            raw.equals("C", true) || "Cyan" in raw || "Cyan" in fallback -> SupplyColor.CYAN
            raw.equals("M", true) || "Magenta" in raw || "Magenta" in fallback -> SupplyColor.MAGENTA
            raw.equals("Y", true) || "Yellow" in raw || "Yellow" in fallback -> SupplyColor.YELLOW
            raw.equals("PK", true) || "Photo" in raw || "Photo" in fallback -> SupplyColor.PHOTO_BLACK
            raw.equals("LM", true) -> SupplyColor.MAGENTA
            raw.equals("LC", true) -> SupplyColor.CYAN
            else -> null
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