package com.hpprint.app.print

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintDocument
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import com.hpprint.app.data.ipp.PrinterIpp
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream

/**
 * 系统级打印服务：把 App 里已保存/发现的惠普打印机注册为 Android 系统打印服务，
 * 让其它应用通过系统「打印」入口直连 IPP 打印机（复用 [PrinterIpp] 的公开协议实现）。
 */
class HpPrintService : PrintService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = object : PrinterDiscoverySession() {
        override fun onStartPrinterDiscovery(priorityList: List<PrinterId>) {
            val printers = PrinterRepositoryImpl.getInstance().printers.value
            addPrinters(printers.filter { it.ip.isNotBlank() }.map { buildPrinterInfo(it) })
        }

        override fun onStopPrinterDiscovery() {}

        override fun onValidatePrinters(printerIds: List<PrinterId>) {}

        override fun onStartPrinterStateTracking(printerId: PrinterId) {}

        override fun onStopPrinterStateTracking(printerId: PrinterId) {}

        override fun onDestroy() {}
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        val printer = PrinterRepositoryImpl.getInstance().printers.value
            .firstOrNull { it.id == printJob.info.printerId?.localId }
        if (printer == null || printer.ip.isBlank()) {
            printJob.fail("未找到打印机")
            return
        }
        printJob.start()
        scope.launch {
            try {
                val bytes = readDocumentBytes(printJob.document)
                val title = printJob.document.info.name.take(255).ifBlank { printer.name }
                val ok = PrinterIpp.printPdf(
                    ip = printer.ip,
                    port = printer.port,
                    title = title,
                    pdfBytes = bytes,
                    copies = 1,
                    colorMode = "color",
                    scheme = printer.scheme,
                )
                if (ok) printJob.complete() else printJob.fail("打印失败")
            } catch (e: Exception) {
                printJob.fail(e.message ?: "打印失败")
            }
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildPrinterInfo(printer: Printer): PrinterInfo {
        val id = generatePrinterId(printer.id)
        val caps = PrinterCapabilitiesInfo.Builder(id)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
            .addResolution(PrintAttributes.Resolution("600x600", "600 x 600 dpi", 600, 600), true)
            .setColorModes(
                PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_COLOR,
            )
            .setDuplexModes(
                if (printer.isDuplex) {
                    PrintAttributes.DUPLEX_MODE_NONE or PrintAttributes.DUPLEX_MODE_LONG_EDGE
                } else {
                    PrintAttributes.DUPLEX_MODE_NONE
                },
                PrintAttributes.DUPLEX_MODE_NONE,
            )
            .build()
        val status = when (printer.status) {
            PrinterStatus.OFFLINE, PrinterStatus.ERROR -> PrinterInfo.STATUS_UNAVAILABLE
            PrinterStatus.BUSY -> PrinterInfo.STATUS_BUSY
            else -> PrinterInfo.STATUS_IDLE
        }
        val builder = PrinterInfo.Builder(id, printer.name.ifBlank { printer.ip }, status)
            .setCapabilities(caps)
        if (printer.model.isNotBlank()) builder.setDescription(printer.model)
        return builder.build()
    }

    private fun readDocumentBytes(document: PrintDocument): ByteArray {
        val pfd = document.data ?: throw IllegalArgumentException("打印数据不可用")
        return FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
    }
}