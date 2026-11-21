package com.hpprint.app.data.papertemplate.model

import android.graphics.Bitmap

/** 纸张尺寸（毫米） */
enum class PageSize(val label: String, val widthMm: Float, val heightMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    B5("B5", 176f, 250f),
    LETTER("Letter", 215.9f, 279.4f),
}

/** 纸张方向 */
enum class Orientation(val label: String) {
    PORTRAIT("纵向"),
    LANDSCAPE("横向"),
}

/** 背景图案（对应原站 Background Pattern） */
enum class BackgroundPattern(val label: String) {
    NONE("无图案"),
    GRID("网格纹理"),
    DOTS("点阵纹理"),
    LINES("线条纹理"),
    CROSSHATCH("交叉阴影"),
    GRAPH("图表纹理"),
    VERTICAL("垂直条纹"),
    DIAGONAL("对角条纹 /"),
    DIAGONAL_REVERSE("对角条纹 \\"),
    DENSE_DOTS("密集点"),
    CHECKER("棋盘格"),
    SOLID("实色遮罩"),
}

/** 预设配色 */
object Palette {
    // 纸张背景色（7 种，对应原站 Paper Color）
    val paperColors = listOf(
        "白色" to 0xFFFFFFFF.toInt(),
        "米黄" to 0xFFFFF8E1.toInt(),
        "浅灰" to 0xFFF8F9FA.toInt(),
        "浅蓝" to 0xFFF0F9FF.toInt(),
        "浅绿" to 0xFFF0FDF4.toInt(),
        "浅粉" to 0xFFFDF2F8.toInt(),
        "浅黄" to 0xFFFFFBEB.toInt(),
    )

    // 线条颜色（6 种）
    val lineColors = listOf(
        "深绿" to 0xFF344F1F.toInt(),
        "深蓝" to 0xFF1E3A8A.toInt(),
        "墨黑" to 0xFF111827.toInt(),
        "灰色" to 0xFF4B5563.toInt(),
        "砖红" to 0xFFB91C1C.toInt(),
        "紫色" to 0xFF6D28D9.toInt(),
    )

    const val DEFAULT_PAPER = 0xFFFFFFFF.toInt()
    const val DEFAULT_LINE = 0xFF344F1F.toInt()
    const val DEFAULT_ACCENT = 0xFFF4991A.toInt()
    const val DEFAULT_TEXT = 0xFF1F2937.toInt()
}

/**
 * 纸张绘制参数（与原站参数面板一一对应）。
 * - [spacingMm] 是「主间距」，语义随纸张类型变化（横线=行距、方格=格边长、五线谱=谱距…）
 * - [secondaryMm] 是「次间距」，仅部分纸张使用
 */
data class PaperConfig(
    val pageSize: PageSize = PageSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val paperColor: Int = Palette.DEFAULT_PAPER,
    val lineColor: Int = Palette.DEFAULT_LINE,
    val accentColor: Int = Palette.DEFAULT_ACCENT,
    val textColor: Int = Palette.DEFAULT_TEXT,
    val pattern: BackgroundPattern = BackgroundPattern.NONE,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "草稿",
    val logoEnabled: Boolean = false,
    val logoText: String = "LOGO",
    val logoBitmap: Bitmap? = null,
    val lineWidthMm: Float = 0.25f,
    val spacingMm: Float = 8f,
    val secondaryMm: Float = 8f,
    val marginMm: Float = 15f,
    val pageCount: Int = 1,
    val showMargin: Boolean = false,
) {
    /** 有效纸张宽高（考虑方向） */
    val contentWidthMm: Float
        get() = if (orientation == Orientation.LANDSCAPE) pageSize.heightMm else pageSize.widthMm
    val contentHeightMm: Float
        get() = if (orientation == Orientation.LANDSCAPE) pageSize.widthMm else pageSize.heightMm

    val pageCountCoerced: Int get() = pageCount.coerceIn(1, 100)
}
