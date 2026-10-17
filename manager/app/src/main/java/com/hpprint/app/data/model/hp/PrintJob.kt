package com.hpprint.app.data.model.hp

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class PrintJobKind { IMAGE, PDF, TEXT, HTML, WEB, DOCX, XLSX, PPTX }

@Serializable
enum class PrintJobStatus { QUEUED, PRINTING, COMPLETED, FAILED, CANCELLED }

@Immutable
@Serializable
data class PrintJob(
    val id: String,
    val title: String,
    val printerId: String,
    val kind: PrintJobKind,
    val sourceUri: String = "",
    val pageCount: Int = 1,
    val copies: Int = 1,
    val colorMode: String = "color",
    val paperSize: String = "A4",
    val duplex: String = "off",
    val status: PrintJobStatus = PrintJobStatus.QUEUED,
    val createdAt: Long = 0L,
    val finishedAt: Long = 0L,
)

/** 系统打印服务报告的任务（PrintManager.getPrintJobs() 抽取的瞬时快照）。 */
@Immutable
data class SystemPrintJob(
    val label: String,
    val status: PrintJobStatus,
)