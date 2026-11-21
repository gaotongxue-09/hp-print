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
    val photoSize: String = "",
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

    /** 打印历史二级页：按日期 / 按文档格式分类查看。 */
    @Parcelize
    @Serializable
    data object PrintHistory : Route

    /** 扫描历史二级页：全部 / 文档扫描 / 图片扫描 / 身份证。 */
    @Parcelize
    @Serializable
    data object ScanHistory : Route

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
    data class ScanPreview(
        val imageUri: String,
        /** 是否做自动裁边/透视校正；打印机扫描件与历史记录已是平整页面，无需校正。 */
        val autoRectify: Boolean = true,
    ) : Route

    @Parcelize
    @Serializable
    data class ManualDuplexConfirm(val title: String) : Route

    @Parcelize
    @Serializable
    data object ScanSettings : Route

    @Parcelize
    @Serializable
    data object WebArticle : Route

    @Parcelize
    @Serializable
    data object IdPhoto : Route

    @Parcelize
    @Serializable
    data object IdCardCopy : Route

    /** 证件打印：支持打印机扫描仪、手机摄像头与相册三种来源，图片统一做透视矫正。 */
    @Parcelize
    @Serializable
    data object IdPrint : Route

    /** 相机文档扫描（多页）；[docId] 为空表示新建文档，否则继续追加到已有文档。 */
    @Parcelize
    @Serializable
    data class ScanCamera(val docId: String = "") : Route

    /** 多页扫描文档查看/编辑页。 */
    @Parcelize
    @Serializable
    data class ScanDoc(val docId: String) : Route

    /** 扫描文档文字识别页。 */
    @Parcelize
    @Serializable
    data class ScanOcr(val docId: String) : Route

    /** 纸张模板库（二级页，双列缩略图 + 搜索）。 */
    @Parcelize
    @Serializable
    data object PaperTemplateGallery : Route

    /** 纸张模板参数调整页；[typeKey] 为 PaperType.key，空串表示默认模板。 */
    @Parcelize
    @Serializable
    data class PaperTemplate(val typeKey: String = "") : Route
}