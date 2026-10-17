package com.hpprint.app.data.model.hp

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class PrinterType { NSD_IPP, NSD_PDL, MANUAL }

@Serializable
enum class PrinterStatus { ONLINE, OFFLINE, IDLE, BUSY, ERROR }

@Serializable
enum class SupplyColor { BLACK, CYAN, MAGENTA, YELLOW, PHOTO_BLACK }

@Immutable
@Serializable
data class SupplyInfo(
    val color: SupplyColor,
    val name: String,
    val levelPercent: Int,
) {
    val isLow: Boolean get() = levelPercent < 15
}

@Immutable
@Serializable
data class Printer(
    val id: String,
    val name: String,
    val model: String,
    val ip: String,
    val hostname: String = "",
    val port: Int = 631,
    val scheme: String = "http",
    val type: PrinterType = PrinterType.MANUAL,
    val status: PrinterStatus = PrinterStatus.OFFLINE,
    val isDefault: Boolean = false,
    val isColor: Boolean = true,
    val isDuplex: Boolean = true,
    val supplies: List<SupplyInfo> = emptyList(),
)

/** 单次 IPP 探测结果（瞬时值，不持久化）。 */
@Immutable
data class PrinterProbe(
    val reachable: Boolean,
    val status: PrinterStatus,
    val supplies: List<SupplyInfo> = emptyList(),
    val model: String? = null,
    val isColor: Boolean? = null,
    val isDuplex: Boolean? = null,
)