package com.hpprint.app.data.render
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintCallbackBridge
import android.print.PrintDocumentAdapter
import android.print.PageRange
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTML / HTM 渲染：离屏 [WebView] 加载后，经系统 PrintDocumentAdapter 生成 PDF，
 * 再走 PDF 统一管线（PdfRenderer 逐页渲染）。
 * Chromium 负责排版与分页，表格/样式/内联图片高保真。
 */
object HtmlRenderer {

    /**
     * 渲染 HTML 为临时 PDF，返回该文件（App cacheDir，App 内可读）。
     * WebView 全部操作在主线程。
     */
    suspend fun toPdfFile(context: Context, uri: Uri): File {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取该 HTML 文件")
        val cs = sniffCharset(bytes)
        val html = String(bytes, cs)

        val pdfFile = File(
            context.cacheDir, "html_print_${System.currentTimeMillis()}.pdf"
        )

        withContext(Dispatchers.Main.immediate) {
            val webView = WebView(context)
            try {
                webView.settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                }
                webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        1024, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(
                        8000, android.view.View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, 1024, webView.measuredHeight)

                awaitPage(webView, uri, html)

                val adapter = webView.createPrintDocumentAdapter("html-document")
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(PrintAttributes.Margins(36, 36, 36, 36))
                    .build()

                awaitLayout(adapter, attrs)

                val pfd = ParcelFileDescriptor.open(
                    pdfFile,
                    ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_READ_WRITE
                )
                try {
                    awaitWrite(adapter, pfd)
                } finally {
                    try { pfd.close() } catch (_: Exception) {}
                }
            } finally {
                webView.destroy()
            }
        }

        if (!pdfFile.exists() || pdfFile.length() < 128)
            throw IllegalStateException("HTML 转 PDF 失败，可将网页另存为 PDF 后打印")
        return pdfFile
    }

    private suspend fun awaitPage(webView: WebView, base: Uri, html: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            webView.loadDataWithBaseURL(
                base.toString(), html, "text/html", "utf-8", null
            )
        }
        delay(300) // 给 Chromium 排版留一拍
    }

    private suspend fun awaitLayout(adapter: PrintDocumentAdapter, attrs: PrintAttributes) =
        suspendCancellableCoroutine<Unit> { cont ->
            adapter.onLayout(
                null, attrs, CancellationSignal(),
                PrintCallbackBridge.newLayoutCallback(object : PrintCallbackBridge.LayoutSink {
                    override fun onFinished(
                        info: android.print.PrintDocumentInfo?, changed: Boolean
                    ) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onFailed(error: CharSequence?) {
                        cont.resumeWithException(
                            IllegalStateException("HTML 排版失败：$error")
                        )
                    }
                }),
                null
            )
        }

    private suspend fun awaitWrite(
        adapter: PrintDocumentAdapter, pfd: ParcelFileDescriptor
    ) = suspendCancellableCoroutine<Unit> { cont ->
        // onWrite 无 Executor 参数（回调固定在主线程）
        adapter.onWrite(
            arrayOf(PageRange.ALL_PAGES),
            pfd,
            CancellationSignal(),
            PrintCallbackBridge.newWriteCallback(object : PrintCallbackBridge.WriteSink {
                override fun onFinished(pages: Array<out PageRange>?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailed(error: CharSequence?) {
                    cont.resumeWithException(
                        IllegalStateException("HTML 生成 PDF 失败：$error")
                    )
                }
            })
        )
    }

    /** 嗅探网页编码（BOM / meta charset），默认 UTF-8 */
    internal fun sniffCharset(b: ByteArray): Charset {
        if (b.size >= 3 && (b[0].toInt() and 0xFF) == 0xEF &&
            (b[1].toInt() and 0xFF) == 0xBB
        ) return Charsets.UTF_8

        val n = minOf(b.size, 4096)
        val head = String(b, 0, n, Charsets.ISO_8859_1).lowercase()
        Regex("charset\\s*=?\\s*[\"']?([a-z0-9_\\-]+)").find(head)?.let { m ->
            val name = m.groupValues[1]
            return when {
                name.startsWith("gb") -> Charset.forName("GBK")
                name == "utf-8" || name == "utf8" -> Charsets.UTF_8
                name.startsWith("big5") -> Charset.forName("Big5")
                name.contains("shift") || name == "sjis" -> Charset.forName("Shift_JIS")
                else -> try { Charset.forName(name) }
                catch (_: Exception) { Charsets.UTF_8 }
            }
        }
        return Charsets.UTF_8
    }
}
