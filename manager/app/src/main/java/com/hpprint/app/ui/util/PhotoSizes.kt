package com.hpprint.app.ui.util

import kotlin.math.roundToInt

/**
 * 照片/证件尺寸表。key 用于跨 Parcelable 传递，widthMm/heightMm 为物理尺寸（毫米）。
 * 打印时按 300 DPI 生成精确物理尺寸的 PDF 页，实现照片/证件照「按尺寸打印」。
 */
data class PhotoSize(val key: String, val widthMm: Float, val heightMm: Float)

object PhotoSizes {

    const val ORIGINAL = "original"
    const val PPI = 300f

    /** 身份证（第二代）标准尺寸：85.6 × 54 mm。 */
    val ID_CARD = PhotoSize("idcard", 85.6f, 54f)

    val ALL: List<PhotoSize> = listOf(
        PhotoSize("3x5", 76f, 127f),
        PhotoSize("4x6", 102f, 152f),
        PhotoSize("5x7", 127f, 178f),
        PhotoSize("1inch", 25f, 35f),
        PhotoSize("small1", 22f, 32f),
        PhotoSize("big1", 33f, 48f),
        PhotoSize("2inch", 35f, 49f),
        PhotoSize("small2", 35f, 45f),
    )

    fun byKey(key: String): PhotoSize? = when (key) {
        ID_CARD.key -> ID_CARD
        else -> ALL.firstOrNull { it.key == key }
    }

    fun widthPx(s: PhotoSize): Int = ((s.widthMm / 25.4f) * PPI).roundToInt().coerceAtLeast(1)

    fun heightPx(s: PhotoSize): Int = ((s.heightMm / 25.4f) * PPI).roundToInt().coerceAtLeast(1)

    /** PDF 页尺寸（点，1 英寸 = 72 点）。 */
    fun pageWidthPt(s: PhotoSize): Int = (s.widthMm / 25.4f * 72f).roundToInt().coerceAtLeast(1)

    fun pageHeightPt(s: PhotoSize): Int = (s.heightMm / 25.4f * 72f).roundToInt().coerceAtLeast(1)
}