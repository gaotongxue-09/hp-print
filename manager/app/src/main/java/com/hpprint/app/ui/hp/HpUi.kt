package com.hpprint.app.ui.hp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.model.hp.SupplyColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun printerStatusLabel(status: PrinterStatus): String = stringResource(
    when (status) {
        PrinterStatus.ONLINE -> R.string.hp_status_online
        PrinterStatus.OFFLINE -> R.string.hp_status_offline
        PrinterStatus.IDLE -> R.string.hp_status_idle
        PrinterStatus.BUSY -> R.string.hp_status_busy
        PrinterStatus.ERROR -> R.string.hp_status_error
    }
)

@Composable
fun jobStatusLabel(status: PrintJobStatus): String = stringResource(
    when (status) {
        PrintJobStatus.QUEUED -> R.string.hp_status_queued
        PrintJobStatus.PRINTING -> R.string.hp_status_printing
        PrintJobStatus.COMPLETED -> R.string.hp_status_completed
        PrintJobStatus.FAILED -> R.string.hp_status_failed
        PrintJobStatus.CANCELLED -> R.string.hp_status_cancelled
    }
)

@Composable
fun supplyColorLabel(color: SupplyColor): String = stringResource(
    when (color) {
        SupplyColor.BLACK -> R.string.hp_ink_black
        SupplyColor.CYAN -> R.string.hp_ink_cyan
        SupplyColor.MAGENTA -> R.string.hp_ink_magenta
        SupplyColor.YELLOW -> R.string.hp_ink_yellow
        SupplyColor.PHOTO_BLACK -> R.string.hp_ink_photo_black
    }
)

/** Returns (backgroundColor, contentColor) for a printer status badge. */
fun printerStatusColors(status: PrinterStatus): Pair<Color, Color> = when (status) {
    PrinterStatus.ONLINE -> Color(0xFFE6F4EA) to Color(0xFF1B5E20)
    PrinterStatus.OFFLINE -> Color(0xFFECECEC) to Color(0xFF5F6368)
    PrinterStatus.IDLE -> Color(0xFFE8F0FE) to Color(0xFF1A73E8)
    PrinterStatus.BUSY -> Color(0xFFFEF3E0) to Color(0xFFB06000)
    PrinterStatus.ERROR -> Color(0xFFFCE8E6) to Color(0xFFB3261E)
}

fun jobStatusColors(status: PrintJobStatus): Pair<Color, Color> = when (status) {
    PrintJobStatus.QUEUED -> Color(0xFFE8F0FE) to Color(0xFF1A73E8)
    PrintJobStatus.PRINTING -> Color(0xFFE8F0FE) to Color(0xFF1A73E8)
    PrintJobStatus.COMPLETED -> Color(0xFFE6F4EA) to Color(0xFF1B5E20)
    PrintJobStatus.FAILED -> Color(0xFFFCE8E6) to Color(0xFFB3261E)
    PrintJobStatus.CANCELLED -> Color(0xFFECECEC) to Color(0xFF5F6368)
}

fun supplyColorHex(color: SupplyColor): Color = when (color) {
    SupplyColor.BLACK -> Color(0xFF1F1F1F)
    SupplyColor.CYAN -> Color(0xFF00A0B0)
    SupplyColor.MAGENTA -> Color(0xFFD32F6B)
    SupplyColor.YELLOW -> Color(0xFFF2C744)
    SupplyColor.PHOTO_BLACK -> Color(0xFF6B6B6B)
}

private val timestampFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatTimestamp(ms: Long): String = if (ms <= 0) "" else timestampFormat.format(Date(ms))

/** 打印文档选择器允许的 MIME 类型：PDF/文本/HTML/图片 + Office + 通配兜底（保证任意文档都能选到）。 */
val HP_PRINT_DOC_MIME_TYPES: Array<String> = arrayOf(
    "application/pdf",
    "text/*",
    "text/html",
    "image/*",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "*/*",
)

/** Derive a printable kind + display title from a picked content URI. */
fun describeFile(context: Context, uri: Uri): Pair<PrintJobKind, String> {
    val mime = context.contentResolver.getType(uri) ?: ""
    val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "Document"
    val lower = name.lowercase(Locale.ROOT)
    val kind = when {
        mime.startsWith("image/") -> PrintJobKind.IMAGE
        mime.contains("text/html") || mime.contains("html") -> PrintJobKind.HTML
        mime.contains("pdf") -> PrintJobKind.PDF
        // Office 文档：优先按 MIME，getType 返回 null 时按扩展名兜底
        mime.contains("wordprocessingml") || mime.contains("msword") ||
            lower.endsWith(".docx") || lower.endsWith(".doc") -> PrintJobKind.DOCX
        mime.contains("spreadsheetml") || mime.contains("ms-excel") ||
            lower.endsWith(".xlsx") || lower.endsWith(".xls") -> PrintJobKind.XLSX
        mime.contains("presentationml") || mime.contains("ms-powerpoint") ||
            lower.endsWith(".pptx") || lower.endsWith(".ppt") -> PrintJobKind.PPTX
        mime.startsWith("text/") -> PrintJobKind.TEXT
        else -> PrintJobKind.PDF
    }
    return kind to name
}

fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()