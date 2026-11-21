package com.hpprint.app.data.ipp

import android.content.Context
import com.hpprint.app.HpPrintApplication
import com.hpprint.app.hpApp

/** eSCL 扫描参数（分辨率 / 色彩 / 双面 / 输出格式）。 */
data class ScanSettings(
    val dpi: Int = 300,
    val colorMode: String = "RGB24",
    val duplex: Boolean = false,
    val format: String = "image/jpeg",
)

object ScanSettingsStore {
    private const val PREFS = "hp_scan_settings"

    private fun app(): HpPrintApplication? = runCatching { hpApp }.getOrNull()

    fun load(): ScanSettings {
        val app = app() ?: return ScanSettings()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ScanSettings(
            dpi = p.getInt("dpi", 300),
            colorMode = p.getString("colorMode", "RGB24") ?: "RGB24",
            duplex = p.getBoolean("duplex", false),
            format = p.getString("format", "image/jpeg") ?: "image/jpeg",
        )
    }

    fun save(s: ScanSettings) {
        val app = app() ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("dpi", s.dpi)
            .putString("colorMode", s.colorMode)
            .putBoolean("duplex", s.duplex)
            .putString("format", s.format)
            .apply()
    }
}