package com.hpprint.app.ui.navigation3

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/** 单个待打印文件（kind 用 String 避免枚举跨 Parcelable 传输的序列化问题）。 */
@Parcelize
@Serializable
data class PrintFile(
    val kind: String,
    val sourceUri: String = "",
    val title: String = "",
) : Parcelable

/**
 * 惠普打印机 APP 的类型安全导航键。
 * kind 用 String 而非枚举，避免枚举跨 Parcelable 传输的序列化问题。
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object Print : Route

    @Parcelize
    @Serializable
    data object Scan : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object AddPrinter : Route

    @Parcelize
    @Serializable
    data class PrinterDetail(val printerId: String) : Route

    @Parcelize
    @Serializable
    data class PrintSetup(
        val files: List<PrintFile> = emptyList(),
        val webUrl: String = "",
    ) : Route

    @Parcelize
    @Serializable
    data object WebPrint : Route

    @Parcelize
    @Serializable
    data class PrintPreview(
        val files: List<PrintFile> = emptyList(),
        val webUrl: String = "",
    ) : Route

    @Parcelize
    @Serializable
    data class ScanPreview(val imageUri: String) : Route

    @Parcelize
    @Serializable
    data class ManualDuplexConfirm(val title: String) : Route
}