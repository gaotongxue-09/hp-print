package com.hpprint.app.ui.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hpprint.app.data.ipp.PrintConfig
import com.hpprint.app.data.render.DocumentLoader
import com.hpprint.app.data.render.DocumentPages
import com.hpprint.app.data.ipp.PrinterIpp
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.hpApp
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.min

data class PrintSpec(
    val title: String,
    val kind: PrintJobKind,
    val sourceUri: String = "",
    val webUrl: String = "",
    val copies: Int = 1,
    val colorMode: String = "color",
    val paperSize: String = "A4",
    val duplex: String = "off",
    val quality: Int = 4,
    val orientation: Int = 3,
    val pageRange: String = "all",
    val pageStart: Int = 1,
    val pageEnd: Int = 1,
    val printerIp: String = "",
    val printerPort: Int = 631,
    val printerScheme: String = "http",
)

/**
 * 统一打印分发：优先走直连 IPP（直接把 PDF 发给打印机），
 * IPP 不可用时回退到系统 PrintManager。
 * 返回是否通过 IPP 成功送达打印机。
 */
object PrintDispatcher {

    private const val TAG = "HpPrintDispatcher"

    private const val TEXT_PAGE_WIDTH = 540
    private const val LINES_PER_PAGE = 40
    private const val LINE_HEIGHT = 16f
    private const val RENDER_DPI = 300

    suspend fun dispatch(
        context: Context,
        spec: PrintSpec,
        onManualFlip: suspend () -> Boolean = { true },
        allowSystemFallback: Boolean = true,
    ): Boolean {
        Log.d(TAG, "dispatch 进入: kind=${spec.kind} title=${spec.title} ip=${spec.printerIp}:${spec.printerPort} duplex=${spec.duplex} uri=${spec.sourceUri}")
        // 无 IPP 主机：无法直连，只能回退系统打印
        if (spec.printerIp.isBlank()) {
            Log.e(TAG, "dispatch: 打印机 IP 为空，无法直连 IPP (title=${spec.title})")
            if (allowSystemFallback) dispatchSystem(context, spec)
            return false
        }
        // 手动双面：拆奇偶页分两段单面打印，中间等待翻面确认
        if (spec.duplex == "manual") {
            return dispatchManualDuplex(context, spec, onManualFlip)
        }
        // 图片：解码位图后走 JPEG 优先、PDF 兜底的直连 IPP（参考项目验证过的可靠路径）
        if (spec.kind == PrintJobKind.IMAGE) {
            val bitmap = withContext(Dispatchers.IO) { decodeImage(context, spec.sourceUri) }
            if (bitmap != null) {
                val sent = try {
                    PrinterIpp.printImage(
                        ip = spec.printerIp,
                        port = spec.printerPort,
                        title = spec.title,
                        bitmap = bitmap,
                        config = spec.toPrintConfig(),
                        scheme = spec.printerScheme,
                    )
                } finally {
                    runCatching { bitmap.recycle() }
                }
                if (sent) return true
                Log.e(TAG, "dispatch: IPP 直连图片失败 (ip=${spec.printerIp})")
            } else {
                Log.e(TAG, "dispatch: 解码图片失败 (source=${spec.sourceUri})")
            }
            if (allowSystemFallback) dispatchSystem(context, spec)
            return false
        }
        // 其余类型（文档）：渲染成逐页 JPEG 直连 IPP。Office/HTML 走 DocumentLoader 原生渲染，
        // 其它格式仍走 PDF 渲染。HP 打印机普遍拒绝 application/pdf（0x040A），转 JPEG 才能真实打印。
        val jpegs = if (spec.isOffice()) {
            Log.d(TAG, "renderOfficeJpegs 开始: kind=${spec.kind} uri=${spec.sourceUri}")
            renderOfficeJpegs(context, spec).also { Log.d(TAG, "renderOfficeJpegs 完成: pages=${it.size}") }
        } else {
            val pdf = withContext(Dispatchers.IO) {
                Log.d(TAG, "renderPdfBytes 开始: kind=${spec.kind} uri=${spec.sourceUri}")
                renderPdfBytes(context, spec).also { Log.d(TAG, "renderPdfBytes 完成: size=${it?.size}") }
            }
            if (pdf != null) withContext(Dispatchers.IO) { renderPdfPagesToJpegs(context, pdf, spec) }
            else emptyList()
        }
        if (jpegs.isNotEmpty()) {
            if (sendJpegs(spec, jpegs, spec.title)) return true
            Log.e(TAG, "dispatch: IPP 直连文档(JPEG)失败 (ip=${spec.printerIp}:${spec.printerPort})")
        } else {
            Log.e(TAG, "dispatch: 文档渲染为 JPEG 失败 (title=${spec.title}, kind=${spec.kind})")
        }

        // HTML/网页/IPP 失败：回退系统打印服务
        if (allowSystemFallback) dispatchSystem(context, spec)
        return false
    }

    // 进程级协程作用域：后台打印不随打印设置页关闭而取消
    private val printScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 后台依次直连打印多份文档；完成后按索引回调结果（回调运行在 IO 线程）。
     * 用于把打印与「返回上一页」解耦，避免网络卡顿把用户困在打印页。
     */
    fun printListAsync(
        context: Context,
        specs: List<PrintSpec>,
        onResult: suspend (index: Int, sent: Boolean) -> Unit = { _, _ -> },
    ): Job = printScope.launch {
        specs.forEachIndexed { i, spec ->
            Log.d(TAG, "printListAsync[$i/${specs.size}]: kind=${spec.kind} title=${spec.title} ip=${spec.printerIp}")
            // 后台直连打印禁用系统打印回退（PrintManager 只能在用户主动触发且有前台 Activity 时调用，
            // 从脱离 Activity 生命周期的协程里弹系统打印对话框会闪退），并捕获任何异常避免崩溃进程。
            val sent = runCatching {
                dispatch(context, spec, allowSystemFallback = false)
            }.getOrDefault(false)
            // 对齐参考项目 PrintQueueManager：串行下发，每个任务完成后等打印机真正空闲再放行下一个，
            // 避免多文件连续提交触发 0x0507 打印机忙或页序错乱。
            if (sent && spec.printerIp.isNotBlank()) {
                runCatching {
                    PrinterIpp.waitUntilPrinterIdle(spec.printerIp, spec.printerPort, spec.printerScheme)
                }
            }
            onResult(i, sent)
        }
    }

    private suspend fun sendJpegs(spec: PrintSpec, jpegs: List<ByteArray>, baseTitle: String): Boolean {
        val config = spec.toPrintConfig()
        jpegs.forEachIndexed { i, jpg ->
            val title = if (jpegs.size > 1) "$baseTitle (${i + 1}/${jpegs.size})" else baseTitle
            val ok = PrinterIpp.printJpeg(
                ip = spec.printerIp,
                port = spec.printerPort,
                title = title,
                jpegBytes = jpg,
                config = config,
                scheme = spec.printerScheme,
            )
            if (!ok) {
                Log.e(TAG, "sendJpegs: 第 ${i + 1}/${jpegs.size} 页发送失败 (ip=${spec.printerIp}:${spec.printerPort})")
                return false
            }
            // 非最后一页：等打印机真正打完这一页再发下一页，避免 HP 打印机单任务队列时
            // 仍在上张纸处理中就收到下一份 Print-Job 而返回 0x0507（忙）导致“只能打印一张”。
            if (i < jpegs.size - 1 && spec.printerIp.isNotBlank()) {
                PrinterIpp.waitUntilPrinterIdle(spec.printerIp, spec.printerPort, spec.printerScheme)
            }
        }
        return true
    }

    private fun PrintSpec.toPrintConfig(): PrintConfig = PrintConfig(
        color = colorMode == "color",
        quality = quality,
        copies = copies,
        media = ippMedia(paperSize),
        orientation = orientation,
        sides = ippSides(duplex),
    )

    private fun ippMedia(paper: String): String = when (paper) {
        "A3" -> "iso_a3"
        "A5" -> "iso_a5"
        "B5" -> "jis_b5"
        "Letter" -> "na_letter"
        "Legal" -> "na_legal"
        "Tabloid" -> "na_ledger"
        else -> "iso_a4"
    }

    private fun ippSides(duplex: String): String = when (duplex) {
        "long" -> "two-sided-long-edge"
        "short" -> "two-sided-short-edge"
        else -> "one-sided"
    }

    private suspend fun dispatchSystem(context: Context, spec: PrintSpec) {
        // PrintManager.print 只允许 Activity 上下文（Application 上下文会抛 "Can print only from an activity"），
        // 且需在主线程调用。这里取当前前台 Activity，并在主线程执行系统打印回退。
        val activity = hpApp.currentActivity ?: (context as? Activity) ?: return
        withContext(Dispatchers.Main.immediate) {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return@withContext
            val attrs = buildAttributes(spec)

            when (spec.kind) {
                PrintJobKind.IMAGE -> printImage(activity, printManager, spec, attrs)
                PrintJobKind.PDF -> printPdf(activity, printManager, spec, attrs)
                PrintJobKind.TEXT -> printText(activity, printManager, spec, attrs)
                PrintJobKind.HTML -> printHtml(activity, printManager, spec, attrs)
                PrintJobKind.WEB -> printWeb(activity, printManager, spec, attrs)
                // Office 系统打印暂不支持（后续阶段实现本地解析前不弹系统打印对话框）
                PrintJobKind.DOCX, PrintJobKind.XLSX, PrintJobKind.PPTX ->
                    Log.w(TAG, "dispatchSystem: 暂不支持该格式的系统打印 (kind=${spec.kind})")
            }
        }
    }

    // ------------------------------------------------------------ 手动双面

    /** 用户在翻面提示中取消手动双面打印时抛出的标记异常，用于与“打印失败”区分。 */
    class ManualDuplexCancelledException : Exception("用户取消手动双面打印")

    /**
     * 手动双面打印（IPP 专属流程）：先把整份文档渲染成逐页 JPEG，再按奇偶页拆分——
     * 正面（第1、3、5…页，正序）先打，等打印机真正空闲后提示翻面，确认后打背面
     * （第2、4、6…页，倒序），使长边翻转后的页序正确。
     * 用户在翻面提示中取消时抛 [ManualDuplexCancelledException]；打印失败返回 false。
     */
    private suspend fun dispatchManualDuplex(
        context: Context,
        spec: PrintSpec,
        onManualFlip: suspend () -> Boolean,
    ): Boolean {
        // ① 渲染待打印范围内的逐页 JPEG（renderDocJpegs 内部已按 pageRange 筛选）
        val pages = renderDocJpegs(context, spec)
        if (pages.isEmpty()) {
            Log.e(TAG, "手动双面: 无法渲染文档页，中止 (title=${spec.title})")
            return false
        }
        // 范围内仅一页时退化为普通单面直连打印
        if (pages.size <= 1) {
            val ok = sendJpegs(spec, pages, spec.title)
            if (!ok) Log.e(TAG, "手动双面(单页): IPP 直连失败 (ip=${spec.printerIp}:${spec.printerPort})")
            return ok
        }

        // ② 拆奇偶页：正面正序、背面倒序
        val frontIndices = pages.indices.filter { it % 2 == 0 }
        val backIndices = pages.indices.filter { it % 2 == 1 }.reversed()

        // ③ 正面
        val front = frontIndices.map { pages[it] }
        if (!sendJpegs(spec, front, "${spec.title} (1/2 正面)")) {
            Log.e(TAG, "手动双面: 正面打印失败 (ip=${spec.printerIp}:${spec.printerPort})")
            return false
        }
        // 关键：等正面真正打完（打印机回到空闲）再提示翻面，避免边打印边翻面
        PrinterIpp.waitUntilPrinterIdle(spec.printerIp, spec.printerPort, spec.printerScheme)

        // ④ 提示翻面，用户取消则中止，不再打印背面
        if (!onManualFlip()) throw ManualDuplexCancelledException()

        // ⑤ 背面
        val back = backIndices.map { pages[it] }
        val backOk = sendJpegs(spec, back, "${spec.title} (2/2 背面)")
        if (backOk) {
            PrinterIpp.waitUntilPrinterIdle(spec.printerIp, spec.printerPort, spec.printerScheme)
        }
        return backOk
    }

    /** 把整份文档渲染成逐页 JPEG（PDF 走 PdfRenderer，Office/HTML 走 DocumentLoader）。 */
    private suspend fun renderDocJpegs(context: Context, spec: PrintSpec): List<ByteArray> {
        if (spec.isOffice()) return renderOfficeJpegs(context, spec)
        val pdf = withContext(Dispatchers.IO) { renderPdfBytes(context, spec) } ?: return emptyList()
        return withContext(Dispatchers.IO) { renderPdfPagesToJpegs(context, pdf, spec) }
    }

    /** 计算本次打印实际要渲染的页（0 基索引）。打印范围为「全部」时返回全部页，否则按起始/结束页截取。 */
    private fun PrintSpec.pageIndices(total: Int): List<Int> {
        if (total <= 0) return emptyList()
        if (pageRange != "custom") return (0 until total).toList()
        val s = pageStart.coerceIn(1, total)
        val e = pageEnd.coerceIn(s, total)
        return (s - 1 until e).toList()
    }

    /** 把文档 PDF 逐页渲染成带纸张白底页框的 JPEG（等比居中、不拉伸），规避 HP 打印机拒收 application/pdf。 */
    private fun renderPdfPagesToJpegs(
        context: Context,
        pdfBytes: ByteArray,
        spec: PrintSpec,
        indices: List<Int>? = null,
    ): List<ByteArray> {
        val temp = java.io.File.createTempFile("hp_doc", ".pdf", context.cacheDir)
        temp.writeBytes(pdfBytes)
        val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        return try {
            val media = mediaSize(spec.paperSize)
            val frameW = (media.widthMils / 1000f * RENDER_DPI).toInt().coerceAtLeast(1)
            val frameH = (media.heightMils / 1000f * RENDER_DPI).toInt().coerceAtLeast(1)
            val selected = indices ?: spec.pageIndices(renderer.pageCount)
            val out = mutableListOf<ByteArray>()
            for (idx in selected) {
                if (idx < 0 || idx >= renderer.pageCount) continue
                val page = renderer.openPage(idx)
                val pw = page.width.toFloat()
                val ph = page.height.toFloat()
                val scale = min(frameW / pw, frameH / ph)
                val cw = (pw * scale).toInt().coerceAtLeast(1)
                val ch = (ph * scale).toInt().coerceAtLeast(1)
                val content = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
                page.render(content, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val frame = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(frame)
                canvas.drawColor(Color.WHITE)
                val left = (frameW - cw) / 2f
                val top = (frameH - ch) / 2f
                canvas.drawBitmap(content, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
                content.recycle()

                val bos = ByteArrayOutputStream()
                frame.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                frame.recycle()
                out.add(bos.toByteArray())
            }
            out
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            runCatching { temp.delete() }
        }
    }

    // ------------------------------------------------------------ IPP 渲染

    /** 把源文件渲染成 PDF 字节，用于直连 IPP 打印；Office/HTML 现走 DocumentLoader 直出 JPEG，WEB 需联网返回 null。 */
    private suspend fun renderPdfBytes(context: Context, spec: PrintSpec): ByteArray? = when (spec.kind) {
        PrintJobKind.PDF -> readUriBytes(context, spec.sourceUri)
        PrintJobKind.IMAGE -> imageToPdf(context, spec)
        PrintJobKind.TEXT -> textToPdf(context, spec)
        // DOCX/XLSX/PPTX/HTML：走 DocumentLoader 原生渲染直出 JPEG，不再生成 PDF；WEB 需联网走系统打印
        PrintJobKind.DOCX, PrintJobKind.XLSX, PrintJobKind.PPTX, PrintJobKind.HTML, PrintJobKind.WEB -> null
    }

    /** Office/HTML 类型集合：这些格式走 DocumentLoader 原生渲染逐页位图 → JPEG。 */
    private val OFFICE_KINDS = setOf(PrintJobKind.DOCX, PrintJobKind.XLSX, PrintJobKind.PPTX, PrintJobKind.HTML)

    private fun PrintSpec.isOffice(): Boolean = kind in OFFICE_KINDS

    private fun officeMime(kind: PrintJobKind): String? =
        if (kind == PrintJobKind.HTML) "text/html" else null

    /** Office/HTML 文档逐页渲染成整页 JPEG（套纸张白底框、等比居中），供 IPP 直连打印。 */
    private suspend fun renderOfficeJpegs(
        context: Context,
        spec: PrintSpec,
        indices: List<Int>? = null,
    ): List<ByteArray> {
        val pages = withContext(Dispatchers.IO) {
            DocumentLoader.load(context, Uri.parse(spec.sourceUri), officeMime(spec.kind), RENDER_DPI)
        }
        return withContext(Dispatchers.IO) {
            try {
                renderOfficePagesToJpegs(pages, spec, indices)
            } finally {
                runCatching { pages.close() }
            }
        }
    }

    /** 已加载的 Office/HTML 页面按索引渲染为 JPEG（纸面白底 + 等比居中，不拉伸）。 */
    private fun renderOfficePagesToJpegs(
        pages: DocumentPages,
        spec: PrintSpec,
        indices: List<Int>? = null,
    ): List<ByteArray> {
        val selected = indices ?: spec.pageIndices(pages.pageCount)
        val media = mediaSize(spec.paperSize)
        val frameW = (media.widthMils / 1000f * RENDER_DPI).toInt().coerceAtLeast(1)
        val frameH = (media.heightMils / 1000f * RENDER_DPI).toInt().coerceAtLeast(1)
        val out = mutableListOf<ByteArray>()
        for (idx in selected) {
            if (idx < 0 || idx >= pages.pageCount) continue
            val content = pages.renderPage(idx)
            val frame = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            canvas.drawColor(Color.WHITE)
            val scale = min(frameW / content.width.toFloat(), frameH / content.height.toFloat())
            val cw = (content.width * scale).toInt().coerceAtLeast(1)
            val ch = (content.height * scale).toInt().coerceAtLeast(1)
            val left = (frameW - cw) / 2f
            val top = (frameH - ch) / 2f
            canvas.drawBitmap(content, null, RectF(left, top, left + cw, top + ch), Paint(Paint.FILTER_BITMAP_FLAG))
            content.recycle()
            val bos = ByteArrayOutputStream()
            frame.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            frame.recycle()
            out.add(bos.toByteArray())
        }
        return out
    }

    private fun readUriBytes(context: Context, sourceUri: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { it.readBytes() }
    }.getOrElse { e ->
        Log.e(TAG, "readUriBytes 失败: uri=$sourceUri err=${e.javaClass.simpleName}:${e.message}")
        null
    }

    /** 解码图片流（带 2 的幂采样，避免大图 OOM）；扫描结果以绝对路径保存，需同时支持文件路径。 */
    private fun decodeImage(context: Context, sourceUri: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput(context, sourceUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w > 0 && h > 0 && w / 2 >= 2048 && h / 2 >= 2048) {
            w /= 2; h /= 2; sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        openInput(context, sourceUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private fun openInput(context: Context, sourceUri: String): java.io.InputStream? =
        if (sourceUri.startsWith("content://") || sourceUri.startsWith("android.resource://")) {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))
        } else {
            runCatching { java.io.FileInputStream(java.io.File(sourceUri)) }.getOrNull()
        }

    private fun imageToPdf(context: Context, spec: PrintSpec): ByteArray? {
        val bitmap = decodeImage(context, spec.sourceUri) ?: return null
        return runCatching {
            val out = ByteArrayOutputStream()
            val doc = PrintedPdfDocument(context, buildAttributes(spec))
            val page = doc.startPage(0)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val cw = canvas.width.toFloat()
            val ch = canvas.height.toFloat()
            val scale = min(cw / bitmap.width, ch / bitmap.height).coerceAtMost(1f)
            val left = (cw - bitmap.width * scale) / 2f
            val top = (ch - bitmap.height * scale) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page)
            doc.writeTo(out)
            doc.close()
            bitmap.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    private fun textToPdf(context: Context, spec: PrintSpec): ByteArray? {
        val text = readText(context, spec.sourceUri) ?: return null
        return runCatching {
            val measurePaint = TextPaint().apply { textSize = 12f }
            val static = StaticLayout(text, measurePaint, TEXT_PAGE_WIDTH, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            val lines = (0 until static.lineCount).map {
                text.substring(static.getLineStart(it), static.getLineEnd(it))
            }
            val pageCount = maxOf(1, ceil(lines.size.toDouble() / LINES_PER_PAGE).toInt())

            val out = ByteArrayOutputStream()
            val doc = PrintedPdfDocument(context, buildAttributes(spec))
            val paint = TextPaint().apply { textSize = 12f; isAntiAlias = true; color = Color.BLACK }
            for (pageIndex in 0 until pageCount) {
                val page = doc.startPage(pageIndex)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val startLine = pageIndex * LINES_PER_PAGE
                var y = 72f
                for (j in 0 until LINES_PER_PAGE) {
                    val idx = startLine + j
                    if (idx >= lines.size) break
                    canvas.drawText(lines[idx], 72f, y, paint)
                    y += LINE_HEIGHT
                }
                doc.finishPage(page)
            }
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }.getOrNull()
    }

    private fun buildAttributes(spec: PrintSpec): PrintAttributes {
        return PrintAttributes.Builder()
            .setColorMode(
                if (spec.colorMode == "grayscale") PrintAttributes.COLOR_MODE_MONOCHROME
                else PrintAttributes.COLOR_MODE_COLOR
            )
            .setMediaSize(mediaSize(spec.paperSize))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setDuplexMode(
                when (spec.duplex) {
                    "long" -> PrintAttributes.DUPLEX_MODE_LONG_EDGE
                    "short" -> PrintAttributes.DUPLEX_MODE_SHORT_EDGE
                    else -> PrintAttributes.DUPLEX_MODE_NONE
                }
            )
            .build()
    }

    private fun mediaSize(paper: String): PrintAttributes.MediaSize = when (paper) {
        "A3" -> PrintAttributes.MediaSize.ISO_A3
        "A5" -> PrintAttributes.MediaSize.ISO_A5
        "B5" -> PrintAttributes.MediaSize.JIS_B5
        "Letter" -> PrintAttributes.MediaSize.NA_LETTER
        "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
        "Tabloid" -> PrintAttributes.MediaSize.NA_LEDGER
        else -> PrintAttributes.MediaSize.ISO_A4
    }

    private fun printImage(context: Context, pm: PrintManager, spec: PrintSpec, attrs: PrintAttributes) {
        val bitmap = decodeImage(context, spec.sourceUri) ?: return

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(spec.title)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                val doc = PrintedPdfDocument(context, attrs)
                try {
                    val page = doc.startPage(0)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val cw = canvas.width.toFloat()
                    val ch = canvas.height.toFloat()
                    val scale = min(cw / bitmap.width, ch / bitmap.height).coerceAtMost(1f)
                    val left = (cw - bitmap.width * scale) / 2f
                    val top = (ch - bitmap.height * scale) / 2f
                    val dst = RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
                    canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page)
                    doc.writeTo(FileOutputStream(destination.fileDescriptor))
                    callback.onWriteFinished(pages)
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }

        pm.print(spec.title, adapter, attrs)
    }

    private fun printPdf(context: Context, pm: PrintManager, spec: PrintSpec, attrs: PrintAttributes) {
        val uri = Uri.parse(spec.sourceUri)
        val pfd: ParcelFileDescriptor = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: return
        val renderer = PdfRenderer(pfd)

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(spec.title)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(renderer.pageCount)
                    .build()
                callback.onLayoutFinished(info, oldAttributes != newAttributes)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                val doc = PrintedPdfDocument(context, attrs)
                try {
                    for (range in pages) {
                        val start = range.start.coerceAtLeast(0)
                        val end = range.end.coerceAtMost(renderer.pageCount - 1)
                        for (pageIndex in start..end) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                doc.close()
                                return
                            }
                            val page = doc.startPage(pageIndex)
                            val rendered = renderer.openPage(pageIndex)
                            val bitmap = Bitmap.createBitmap(
                                rendered.width, rendered.height, Bitmap.Config.ARGB_8888
                            )
                            rendered.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            rendered.close()

                            val canvas = page.canvas
                            val cw = canvas.width.toFloat()
                            val ch = canvas.height.toFloat()
                            val scale = min(cw / bitmap.width, ch / bitmap.height).coerceAtMost(1f)
                            val left = (cw - bitmap.width * scale) / 2f
                            val top = (ch - bitmap.height * scale) / 2f
                            val dst = RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                            bitmap.recycle()
                            doc.finishPage(page)
                        }
                    }
                    doc.writeTo(FileOutputStream(destination.fileDescriptor))
                    callback.onWriteFinished(pages)
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }

            override fun onFinish() {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }

        pm.print(spec.title, adapter, attrs)
    }

    private fun printText(context: Context, pm: PrintManager, spec: PrintSpec, attrs: PrintAttributes) {
        val text = readText(context, spec.sourceUri) ?: return
        val adapter = object : PrintDocumentAdapter() {
            private var lines: List<String> = emptyList()
            private var pageCount = 1

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val paint = TextPaint().apply { textSize = 12f }
                val static = StaticLayout(text, paint, TEXT_PAGE_WIDTH, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
                lines = (0 until static.lineCount).map {
                    text.substring(static.getLineStart(it), static.getLineEnd(it))
                }
                pageCount = maxOf(1, ceil(lines.size.toDouble() / LINES_PER_PAGE).toInt())
                val info = PrintDocumentInfo.Builder(spec.title)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build()
                callback.onLayoutFinished(info, oldAttributes != newAttributes)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                val doc = PrintedPdfDocument(context, attrs)
                try {
                    val paint = TextPaint().apply { textSize = 12f; isAntiAlias = true; color = Color.BLACK }
                    for (range in pages) {
                        for (pageIndex in range.start..range.end) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback.onWriteCancelled()
                                doc.close()
                                return
                            }
                            val page = doc.startPage(pageIndex)
                            val canvas: Canvas = page.canvas
                            canvas.drawColor(Color.WHITE)
                            val startLine = pageIndex * LINES_PER_PAGE
                            var y = 72f
                            for (j in 0 until LINES_PER_PAGE) {
                                val idx = startLine + j
                                if (idx >= lines.size) break
                                canvas.drawText(lines[idx], 72f, y, paint)
                                y += LINE_HEIGHT
                            }
                            doc.finishPage(page)
                        }
                    }
                    doc.writeTo(FileOutputStream(destination.fileDescriptor))
                    callback.onWriteFinished(pages)
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    doc.close()
                }
            }
        }
        pm.print(spec.title, adapter, attrs)
    }

    private fun printHtml(context: Context, pm: PrintManager, spec: PrintSpec, attrs: PrintAttributes) {
        val html = readText(context, spec.sourceUri) ?: return
        printViaWebView(context, pm, spec, attrs) { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private fun printWeb(context: Context, pm: PrintManager, spec: PrintSpec, attrs: PrintAttributes) {
        val url = spec.webUrl.ifBlank { spec.sourceUri.ifBlank { return } }
        printViaWebView(context, pm, spec, attrs) { webView ->
            webView.loadUrl(url)
        }
    }

    private fun printViaWebView(
        context: Context,
        pm: PrintManager,
        spec: PrintSpec,
        attrs: PrintAttributes,
        load: (WebView) -> Unit,
    ) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val adapter = view?.createPrintDocumentAdapter(spec.title) ?: return
                pm.print(spec.title, adapter, attrs)
            }
        }
        load(webView)
    }

    private fun readText(context: Context, uriString: String): String? {
        val uri = Uri.parse(uriString)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
    }
}