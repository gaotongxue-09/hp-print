package com.hpprint.app.data.ipp

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.data.model.hp.PrinterProbe
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.model.hp.SupplyColor
import com.hpprint.app.data.model.hp.SupplyInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * IPP（Internet Printing Protocol）客户端，连接与可靠性策略对齐参考项目 HpPrintScanner：
 *
 *  - 多端点自动探测：惠普新机型打印常走 https/443（IPPS），明文 631 可能关闭。
 *    给定主机后依次尝试首选端点与候选端点（443/631/80/8080/8443），连接失败自动切换。
 *  - HTTPS 信任打印机自签名证书（局域网直连通行做法）。
 *  - 提交 Print-Job 前先以 Get-Printer-Attributes（无副作用）探测出唯一可用端点，
 *    避免重复创建打印任务；并缓存上次成功端点，忙时跳过全端点探测。
 *  - 自动降级：文档格式不支持(0x040A)换格式；属性不支持(0x040B)去掉 Job Template 重试；
 *    打印机忙(0x0507)递增退避重试。
 *  - 完成判定：优先按 job-id → Get-Job-Attributes 权威等待（awaitJobs）；拿不到 id 时
 *    退回 printer-state 轮询。两者对 printer-state/job-state 的枚举值标签同时接受
 *    integer(0x21) 与 enum(0x23)，避免部分打印机用 0x21 编码枚举导致永远读不出状态、
 *    最终误报「等待作业完成超时」。
 *
 * 功能：
 * 1. [probe]     Get-Printer-Attributes —— 采集实时状态与耗材。
 * 2. [printPdf] / [printImage] / [printJpeg] / [printDocument] —— Print-Job 直连打印。
 * 3. [waitUntilPrinterIdle] —— 打印后等待打印机回到空闲。
 */
object PrinterIpp {

    // IPP 值标签
    private const val TAG_OPERATION = 0x01
    private const val TAG_JOB = 0x02
    private const val TAG_END = 0x03
    private const val TAG_INTEGER = 0x21
    private const val TAG_BOOLEAN = 0x22
    private const val TAG_ENUM = 0x23
    private const val TAG_OCTET_STRING = 0x30
    private const val TAG_NAME = 0x42        // nameWithoutLanguage
    private const val TAG_KEYWORD = 0x44
    private const val TAG_URI = 0x45
    private const val TAG_CHARSET = 0x47
    private const val TAG_NATURAL_LANGUAGE = 0x48
    private const val TAG_MIME = 0x49        // mimeMediaType

    // IPP 操作码
    private const val OP_GET_PRINTER_ATTRIBUTES = 0x000B
    private const val OP_GET_JOB_ATTRIBUTES = 0x0009
    private const val OP_PRINT_JOB = 0x0002
    private const val OP_CANCEL_JOB = 0x0008

    private const val SUCCESS_MAX = 0x00FF      // 0x0000~0x00FF 成功类
    private const val ERR_DOC_FORMAT_NOT_SUPPORTED = 0x040A
    private const val ERR_ATTRIBUTES_NOT_SUPPORTED = 0x040B
    private const val ERR_BUSY = 0x0507
    private const val TAG = "HpIpp"

    // 每次请求递增 request-id（RFC 2911 要求唯一），避免部分打印机对此做去重判断。
    private val requestIdCounter = java.util.concurrent.atomic.AtomicInteger(1)
    private fun nextRequestId(): Int = requestIdCounter.incrementAndGet()

    // 端点候选（除首选外）：明文 http 排在 https 之前。
    // 实测部分 HP 打印机对 https/443(IPPS) 的 Get-Printer-Attributes 正常响应，
    // 但 Print-Job 大报文会断开连接（ippStatus=null），因此打印优先尝试 http 端点。
    private val CANDIDATES = listOf(
        "http" to 631,
        "http" to 80,
        "http" to 8080,
        "https" to 443,
        "https" to 8443,
    )

    /** 信任所有证书（仅用于局域网打印机自签证书）。 */
    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val http: OkHttpClient = run {
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAllManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // 探测专用：短超时，仅用于无副作用的 Get-Printer-Attributes / Get-Job-Attributes
    private val probeClient: OkHttpClient = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // IPP printer-state 枚举值
    private const val STATE_IDLE = 3
    private const val STATE_PROCESSING = 4
    private const val STATE_STOPPED = 5

    // IPP job-state 枚举值（RFC 2911）
    private const val JOB_PENDING = 3
    private const val JOB_PENDING_HELD = 4
    private const val JOB_PROCESSING = 5
    private const val JOB_PROCESSING_STOPPED = 6
    private const val JOB_CANCELED = 7
    private const val JOB_ABORTED = 8
    private const val JOB_COMPLETED = 9

    // -------------------------------------------------------------- 端点

    private data class Endpoint(val scheme: String, val ip: String, val port: Int) {
        val httpUrl get() = "$scheme://$ip:$port/ipp/print"
        val ippUri get() = "${if (scheme == "https") "ipps" else "ipp"}://$ip:$port/ipp/print"
    }

    /** 已下发任务句柄，用于取消 / 按 job-id 权威等待完成。 */
    private data class JobHandle(
        val scheme: String,
        val ip: String,
        val port: Int,
        val jobId: Int,
        val jobUri: String,
    )

    /** Get-Job-Attributes 解析出的单个作业状态。 */
    private data class JobStateInfo(val jobId: Int, val state: Int, val reasons: List<String>)

    /** 按 host 记住上次成功的打印端点：下次直连，跳过全端点探测（忙时探测会被挂起超时）。 */
    private val endpointCache = HashMap<String, Endpoint>()

    /** 最近一次成功下发/超时下发的任务句柄，供 [waitUntilPrinterIdle] 按 job-id 权威等待。 */
    @Volatile
    private var lastHandle: JobHandle? = null

    private sealed class SendResult {
        data class Ok(val handle: JobHandle) : SendResult()
        data class ConnectFail(val msg: String, val timeout: Boolean = false) : SendResult()
        data class Reject(val status: Int) : SendResult()
    }

    /** 首选端点（用调用方显式传入的 scheme+port）在前，候选端点去重后跟在后面。 */
    private fun endpointList(ip: String, port: Int, scheme: String): List<Endpoint> {
        val preferred = Endpoint(if (scheme == "https") "https" else "http", ip, port)
        val candidates = CANDIDATES.map { Endpoint(it.first, ip, it.second) }
        return (listOf(preferred) + candidates).distinctBy { it.scheme to it.port }
    }

    // -------------------------------------------------------------- 探测

    /**
     * Get-Printer-Attributes（无副作用）。只探测缓存端点（无缓存时探测首选端点），
     * 单次请求即判在线/离线，保证状态实时；失败返回 [PrinterProbe.reachable] = false。
     */
    suspend fun probe(ip: String, port: Int, scheme: String = "http", timeoutMs: Int = 8000): PrinterProbe =
        withContext(Dispatchers.IO) {
            runCatching {
                // 优先复用打印时缓存的已知端点，只发一次探测，快速反映在线/离线。
                // 缓存端点探测失败即判离线（不再遍历其余候选端点），避免打印机断网时
                // 每个候选端点都要等一次连接超时，导致状态长时间停留「在线」。
                // 端点漂移（换端口/IP）由打印流程重新全端点探测并刷新缓存来恢复。
                endpointCache[ip]?.let { cached ->
                    val body = probeGetAttributes(cached, timeoutMs)
                    return@runCatching if (body != null) parseResponse(body)
                    else PrinterProbe(reachable = false, status = PrinterStatus.OFFLINE)
                }
                // 无缓存（首次探测）：只测首选端点，失败即判离线。不再逐个试候选端口，
                // 避免打印机断网时多端口各等一次连接超时，状态迟迟不更新。
                val preferred = Endpoint(if (scheme == "https") "https" else "http", ip, port)
                val body = probeGetAttributes(preferred, timeoutMs)
                if (body != null) {
                    endpointCache[ip] = preferred
                    parseResponse(body)
                } else {
                    PrinterProbe(reachable = false, status = PrinterStatus.OFFLINE)
                }
            }.getOrNull() ?: PrinterProbe(reachable = false, status = PrinterStatus.OFFLINE)
        }

    private fun probeGetAttributes(ep: Endpoint, callTimeoutMs: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        out.write(0x01); out.write(0x01)
        writeShort(out, OP_GET_PRINTER_ATTRIBUTES)
        writeInt(out, nextRequestId())
        out.write(TAG_OPERATION)
        writeAttr(out, TAG_CHARSET, "attributes-charset", "utf-8")
        writeAttr(out, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en-us")
        writeAttr(out, TAG_URI, "printer-uri", ep.ippUri)
        out.write(TAG_END)

        val req = Request.Builder()
            .url(ep.httpUrl)
            .post(out.toByteArray().toRequestBody("application/ipp".toMediaType()))
            .header("Accept", "application/ipp")
            .build()
        return try {
            val call = probeClient.newCall(req)
            call.timeout().timeout(callTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            call.execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                if (body.size < 8) return null
                if (statusCode(body) > SUCCESS_MAX) return null
                body
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * 无副作用端点探测：发送 Get-Printer-Attributes(0x000B)。
     * @return IPP 状态码；-1=连接失败；-2=非 IPP 响应；-3=连接成功但响应超时（打印机忙）
     */
    private fun probeEndpoint(ep: Endpoint): Int {
        val out = ByteArrayOutputStream()
        out.write(0x01); out.write(0x01)
        writeShort(out, OP_GET_PRINTER_ATTRIBUTES)
        writeInt(out, nextRequestId())
        out.write(TAG_OPERATION)
        writeAttr(out, TAG_CHARSET, "attributes-charset", "utf-8")
        writeAttr(out, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en-us")
        writeAttr(out, TAG_URI, "printer-uri", ep.ippUri)
        out.write(TAG_END)

        val req = Request.Builder()
            .url(ep.httpUrl)
            .post(out.toByteArray().toRequestBody("application/ipp".toMediaType()))
            .header("Accept", "application/ipp")
            .build()
        return try {
            probeClient.newCall(req).execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                if (body.size < 4 || body[0] != 0x01.toByte() || body[1] != 0x01.toByte()) return -2
                statusCode(body)
            }
        } catch (e: SocketTimeoutException) {
            -3
        } catch (e: IOException) {
            -1
        }
    }

    /**
     * 确定唯一可用端点：
     *  ① 优先复用上次成功端点（忙/超时直接用，惠普忙时会把探测连接挂起）。
     *  ② 否则全端点探测，且用时间预算反复探测——打印机打完一页后网络栈会短暂拒绝新 TCP 连接，
     *     几秒到十几秒内反复探测可避免中途判死导致多页打印失败。
     */
    private suspend fun probeChosenEndpoint(ip: String, port: Int, scheme: String): Endpoint {
        endpointCache[ip]?.let { cached ->
            Log.d(TAG, "print: 使用上次成功端点 ${cached.httpUrl} ...")
            when (val s = probeEndpoint(cached)) {
                in 0..SUCCESS_MAX -> {
                    Log.d(TAG, "  端点可用 ✓ (status=0x${s.toString(16)})")
                    return cached
                }
                ERR_BUSY, -3 -> {
                    Log.d(TAG, "  打印机忙（探测未及时返回），直接用该端点提交打印")
                    return cached
                }
                else -> {
                    Log.d(TAG, "  缓存端点已失效，重新全端点探测")
                    endpointCache.remove(ip)
                }
            }
        }

        var busyFallback: Endpoint? = null
        val deadline = System.currentTimeMillis() + 45_000L
        var round = 0
        while (System.currentTimeMillis() < deadline) {
            if (round > 0) {
                Log.w(TAG, "print: 本轮未找到可用端点，5s 后重探测")
                delay(5000)
            }
            round++
            for (ep in endpointList(ip, port, scheme)) {
                Log.d(TAG, "探测端点 ${ep.httpUrl} ...")
                when (val s = probeEndpoint(ep)) {
                    in 0..SUCCESS_MAX -> {
                        Log.d(TAG, "  端点可用 ✓ (status=0x${s.toString(16)})")
                        endpointCache[ip] = ep
                        return ep
                    }
                    ERR_BUSY -> if (busyFallback == null) {
                        Log.d(TAG, "  是 IPP 端点但打印机忙，先记录")
                        busyFallback = ep
                    }
                    -3 -> Log.d(TAG, "  响应超时（打印机可能忙），继续探测")
                    -2 -> Log.d(TAG, "  非 IPP 服务，继续探测")
                    else -> Log.d(TAG, "  连接失败，继续探测")
                }
            }
            // 探测到至少一个“忙”端点：说明端点有效，交给 submitFormats 做忙重试即可，不必空等 45s。
            if (busyFallback != null) {
                endpointCache[ip] = busyFallback!!
                return busyFallback!!
            }
        }

        busyFallback?.let {
            endpointCache[ip] = it
            return it
        }
        throw IOException("未找到可用的 IPP 打印端点 $ip")
    }

    // -------------------------------------------------------------- 打印

    /** 把 PDF 字节直接作为 Print-Job 发给打印机（兼容入口，实际走 [printDocument]）。 */
    suspend fun printPdf(
        ip: String,
        port: Int,
        title: String,
        pdfBytes: ByteArray,
        copies: Int = 1,
        colorMode: String = "color",
        paperSize: String = "A4",
        duplex: String = "off",
        quality: Int = 4,
        orientation: Int = 3,
        scheme: String = "http",
        timeoutMs: Int = 20000,
    ): Boolean = printDocument(
        ip = ip,
        port = port,
        title = title,
        candidates = listOf("application/pdf" to pdfBytes),
        config = PrintConfig(
            color = colorMode == "color",
            quality = quality,
            copies = copies,
            media = mediaKeyword(paperSize),
            orientation = orientation,
            sides = sidesKeyword(duplex),
        ),
        scheme = scheme,
        timeoutMs = timeoutMs,
    )

    /**
     * 直连打印：把若干文档候选（图片场景 JPEG 优先、PDF 兜底）依次尝试，
     * 与参考项目 HpPrintScanner 的打印策略一致。
     */
    suspend fun printDocument(
        ip: String,
        port: Int,
        title: String,
        candidates: List<Pair<String, ByteArray>>,
        config: PrintConfig = PrintConfig(),
        scheme: String = "http",
        timeoutMs: Int = 20000,
    ): Boolean = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext false
        // 每次新打印任务前清理连接池：HP 打印机打印完成后会关闭 keep-alive 连接，
        // 复用旧连接会导致下次请求挂起或直接断开，这里强制新建连接。
        http.connectionPool.evictAll()
        runCatching {
            val target = probeChosenEndpoint(ip, port, scheme)
            val handle = submitFormats(target, title, candidates, config)
            lastHandle = handle
            true
        }.getOrElse { e ->
            Log.w(TAG, "print: 失败 ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 图片直连打印：JPEG 优先、PDF 兜底（参考项目验证过的可靠路径）。 */
    suspend fun printImage(
        ip: String,
        port: Int,
        title: String,
        bitmap: Bitmap,
        config: PrintConfig = PrintConfig(),
        scheme: String = "http",
        timeoutMs: Int = 20000,
    ): Boolean = withContext(Dispatchers.IO) {
        // 黑白打印时生成灰度副本；用毕即回收，避免连续多图打印时原生内存耗尽。
        val grayscale = if (config.color) null else PrintHelper.toGrayscale(bitmap)
        val effective = grayscale ?: bitmap
        try {
            // 优先下发 PDF（已按纸张等比居中排版，带 A4/Letter 页框），
            // 避免裸 JPEG 无页框时被打印机按默认“铺满”拉伸变形；JPEG 仅作降级兜底。
            val pdf = PrintHelper.bitmapToPdfBytes(effective, config)
            val jpg = PrintHelper.bitmapToJpegBytes(effective)
            printDocument(
                ip, port, title,
                listOf("application/pdf" to pdf, "image/jpeg" to jpg),
                config, scheme, timeoutMs,
            )
        } finally {
            grayscale?.recycle()
        }
    }

    /** 单张 JPEG 直连打印：HP 打印机普遍拒绝 application/pdf，文档逐页转 JPEG 后走此入口。 */
    suspend fun printJpeg(
        ip: String,
        port: Int,
        title: String,
        jpegBytes: ByteArray,
        config: PrintConfig = PrintConfig(),
        scheme: String = "http",
        timeoutMs: Int = 20000,
    ): Boolean = printDocument(
        ip = ip,
        port = port,
        title = title,
        candidates = listOf("image/jpeg" to jpegBytes),
        config = config,
        scheme = scheme,
        timeoutMs = timeoutMs,
    )

    private fun sidesKeyword(duplex: String): String = when (duplex) {
        "long" -> "two-sided-long-edge"
        "short" -> "two-sided-short-edge"
        else -> "one-sided"
    }

    /** 对唯一端点提交，含格式/属性降级与忙重试。 */
    private suspend fun submitFormats(
        target: Endpoint,
        title: String,
        candidates: List<Pair<String, ByteArray>>,
        config: PrintConfig,
    ): JobHandle {
        var formatIdx = 0
        var includeTemplate = true
        var busyRetries = 0
        var connectRetries = 0
        while (true) {
            val (fmt, data) = candidates[formatIdx]
            Log.d(TAG, "print: 提交 $fmt → ${target.httpUrl} (template=$includeTemplate, ${data.size / 1024} KB)")
            when (val r = send(target, data, fmt, title, config, includeTemplate)) {
                is SendResult.Ok -> {
                    Log.d(TAG, "print: 任务已成功下发 ✓ ($fmt, jobId=${r.handle.jobId})")
                    return r.handle
                }
                is SendResult.ConnectFail ->
                    if (r.timeout) {
                        // 文档已发出但等响应超时：打印机多半正在打印，转入完成状态轮询，避免重复提交
                        Log.w(TAG, "print: 文档已发送，等待响应超时，转入完成状态轮询")
                        return JobHandle(target.scheme, target.ip, target.port, -1, target.ippUri)
                    } else {
                        // 打印机打完一页后会短暂拒绝新 TCP 连接（连接中断/复位），立即判失败会让
                        // 多页打印 / 手动双面在后续页被误判为整份失败。这里做有界退避重试。
                        connectRetries++
                        if (connectRetries > 6) throw IOException("已选定端点但连接中断：${r.msg}")
                        val wait = minOf(1500L * connectRetries, 6000L)
                        Log.w(TAG, "print: 连接中断「${r.msg}」，${wait / 1000} 秒后重试（第 $connectRetries 次）...")
                        delay(wait)
                    }
                is SendResult.Reject -> when (r.status) {
                    ERR_DOC_FORMAT_NOT_SUPPORTED -> {
                        Log.w(TAG, "print: 不支持 $fmt，换下一种格式")
                        formatIdx++
                        includeTemplate = true
                        if (formatIdx >= candidates.size) throw IOException("文档格式均不被支持")
                    }
                    ERR_ATTRIBUTES_NOT_SUPPORTED -> {
                        if (includeTemplate) {
                            Log.w(TAG, "print: 不支持部分配置，去掉配置属性重试")
                            includeTemplate = false
                        } else {
                            formatIdx++
                            includeTemplate = true
                            if (formatIdx >= candidates.size) throw IOException("属性不支持")
                        }
                    }
                    ERR_BUSY -> {
                        busyRetries++
                        if (busyRetries > 30)
                            throw IOException("打印机持续忙：请重启打印机（关机 10 秒再开）以清空队列后重试")
                        val waitMs = minOf(2000L * busyRetries, 5000L)
                        Log.w(TAG, "print: 打印机忙，${waitMs / 1000} 秒后重试（第 $busyRetries 次）...")
                        delay(waitMs)
                    }
                    else -> throw IOException("打印被拒绝 status=0x${r.status.toString(16)}")
                }
            }
        }
    }

    /** 发送构建好的 IPP 打印请求；连接/读取超时与连接失败区分返回。 */
    private fun send(
        target: Endpoint,
        document: ByteArray,
        documentFormat: String,
        title: String,
        config: PrintConfig,
        includeTemplate: Boolean,
    ): SendResult {
        val ippBytes = encodePrintJob(target, document, title, config, includeTemplate, documentFormat)
        val req = Request.Builder()
            .url(target.httpUrl)
            .post(ippBytes.toRequestBody("application/ipp".toMediaType()))
            .header("Accept", "application/ipp")
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                if (body.size < 4) return SendResult.ConnectFail("空响应")
                val status = statusCode(body)
                Log.d(TAG, "print: HTTP ${resp.code}, IPP status=0x${status.toString(16)}")
                if (status in 0..SUCCESS_MAX) SendResult.Ok(parseJobHandle(body, target))
                else SendResult.Reject(status)
            }
        } catch (e: SocketTimeoutException) {
            SendResult.ConnectFail(e.message ?: "读取超时", timeout = true)
        } catch (e: IOException) {
            SendResult.ConnectFail(e.message ?: "连接失败", timeout = false)
        }
    }

    // -------------------------------------------------------------- 空闲 / 完成等待

    /** 打印机状态快照：printer-state + queued-job-count。 */
    private data class StateSnapshot(val printerState: Int, val queuedCount: Int)

    /**
     * 提交打印后阻塞轮询，直到打印机处理完当前任务并回到空闲（idle 且队列为空）。
     * 手动双面必须用它：正面打完、机器真正空闲后再提示用户翻面，避免边打印边翻面。
     * 优先用最近一次任务的 job-id 走 Get-Job-Attributes 权威等待；拿不到 id 时退回 printer-state 轮询。
     * @return true = 已回到空闲；false = 未检测到任务或等待超时
     */
    suspend fun waitUntilPrinterIdle(
        ip: String,
        port: Int,
        scheme: String = "http",
        timeoutMs: Int = 180000,
    ): Boolean = withContext(Dispatchers.IO) {
        // ① 有明确 job-id 时，权威等待该作业 finish（completed/aborted/canceled）
        val handle = lastHandle
        if (handle != null && handle.ip == ip && handle.jobId > 0) {
            val ep = endpointCache[ip] ?: Endpoint(handle.scheme, handle.ip, handle.port)
            if (awaitJobs(ep, listOf(handle.jobId), timeoutMs)) return@withContext true
            Log.w(TAG, "waitIdle: 按 job-id 等待未确认完成，退回 printer-state 轮询兜底")
        }
        // ② 无 job-id（或兜底）：按 printer-state 轮询
        waitByPrinterState(ip, port, scheme, timeoutMs)
    }

    /** 按 job-id 权威等待作业完成（completed=9）。aborted/canceled 判失败；其余继续等。 */
    private suspend fun awaitJobs(ep: Endpoint, jobIds: List<Int>, timeoutMs: Int): Boolean {
        val ids = jobIds.filter { it > 0 }
        if (ids.isEmpty()) return false
        val lastStates = HashMap<Int, JobStateInfo>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ids.forEach { id -> getJobState(ep, id)?.let { lastStates[id] = it } }
            val infos = ids.mapNotNull { lastStates[it] }
            if (infos.size == ids.size) {
                val aborted = infos.firstOrNull { it.state == JOB_ABORTED }
                val canceled = infos.firstOrNull { it.state == JOB_CANCELED }
                when {
                    aborted != null -> {
                        Log.w(TAG, "waitIdle: 作业 ${aborted.jobId} 被打印机中止，原因：${aborted.reasons.joinToString().ifEmpty { "未提供" }}")
                        return false
                    }
                    canceled != null -> {
                        Log.w(TAG, "waitIdle: 作业 ${canceled.jobId} 已取消")
                        return false
                    }
                    infos.all { it.state == JOB_COMPLETED } -> {
                        Log.d(TAG, "waitIdle: 作业全部完成 ✓ (${ids.size} 个)")
                        return true
                    }
                    else -> {
                        val doing = infos.count { it.state == JOB_PROCESSING || it.state == JOB_PROCESSING_STOPPED }
                        val queued = infos.count { it.state == JOB_PENDING || it.state == JOB_PENDING_HELD }
                        Log.d(TAG, "waitIdle: 打印中… 处理中 $doing / 排队 $queued / 共 ${ids.size}")
                    }
                }
            }
            delay(1500)
        }
        Log.w(TAG, "waitIdle: 等待作业完成超时")
        return false
    }

    /** 按 printer-state 轮询：先确认任务被接收，再等空闲。 */
    private suspend fun waitByPrinterState(ip: String, port: Int, scheme: String, timeoutMs: Int): Boolean {
        val endpoints = endpointList(ip, port, scheme)

        fun query(): StateSnapshot? {
            for (ep in endpoints) {
                probeState(ep)?.let { return it }
            }
            return null
        }

        // 阶段1：确认任务被接收（processing 或队列>0），最多 25s
        var saw = false
        val t1 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t1 < 25000) {
            val q = query()
            if (q != null && (q.printerState == STATE_PROCESSING || q.queuedCount > 0)) {
                saw = true
                break
            }
            delay(1000)
        }
        if (!saw) {
            Log.w(TAG, "waitIdle: 未检测到任务被接收")
            return false
        }
        Log.d(TAG, "waitIdle: 打印机已开始处理，等待物理打印完成 ...")

        // 阶段2：等待空闲（idle 且队列为空）
        val t2 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t2 < timeoutMs) {
            val q = query()
            if (q != null && q.printerState == STATE_IDLE && q.queuedCount == 0) {
                Log.d(TAG, "waitIdle: 打印完成，打印机已空闲 ✓")
                return true
            }
            delay(2000)
        }
        Log.w(TAG, "waitIdle: 等待空闲超时")
        return false
    }

    /** 查询 printer-state / queued-job-count（短超时、无副作用），失败返回 null。 */
    private fun probeState(ep: Endpoint): StateSnapshot? {
        val out = ByteArrayOutputStream()
        out.write(0x01); out.write(0x01)
        writeShort(out, OP_GET_PRINTER_ATTRIBUTES)
        writeInt(out, nextRequestId())
        out.write(TAG_OPERATION)
        writeAttr(out, TAG_CHARSET, "attributes-charset", "utf-8")
        writeAttr(out, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en-us")
        writeAttr(out, TAG_URI, "printer-uri", ep.ippUri)
        out.write(TAG_END)

        val req = Request.Builder()
            .url(ep.httpUrl)
            .post(out.toByteArray().toRequestBody("application/ipp".toMediaType()))
            .header("Accept", "application/ipp")
            .build()
        return try {
            probeClient.newCall(req).execute().use { resp ->
                val body = resp.body?.bytes() ?: ByteArray(0)
                if (body.size < 8) return null
                if (statusCode(body) > SUCCESS_MAX) return null
                var state = -1
                var queued = -1
                forEachAttr(body) { tag, name, value ->
                    if (name == "printer-state" && (tag == TAG_ENUM || tag == TAG_INTEGER) && value.size >= 4)
                        state = readInt(value, 0)
                    if (name == "queued-job-count" && tag == TAG_INTEGER && value.size >= 4)
                        queued = readInt(value, 0)
                }
                if (state < 0) null else StateSnapshot(state, if (queued < 0) 0 else queued)
            }
        } catch (e: IOException) {
            null
        }
    }

    /** Get-Job-Attributes(0x0009)：按 job-id 查询单个作业的 job-state / job-state-reasons。 */
    private fun getJobState(ep: Endpoint, jobId: Int): JobStateInfo? {
        val out = ByteArrayOutputStream()
        out.write(0x01); out.write(0x01)
        writeShort(out, OP_GET_JOB_ATTRIBUTES)
        writeInt(out, nextRequestId())
        out.write(TAG_OPERATION)
        writeAttr(out, TAG_CHARSET, "attributes-charset", "utf-8")
        writeAttr(out, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en-us")
        writeAttr(out, TAG_URI, "printer-uri", ep.ippUri)
        writeIntegerAttr(out, "job-id", jobId)
        out.write(TAG_END)

        val req = Request.Builder()
            .url(ep.httpUrl)
            .post(out.toByteArray().toRequestBody("application/ipp".toMediaType()))
            .header("Accept", "application/ipp")
            .build()
        return try {
            probeClient.newCall(req).execute().use { resp ->
                val body = resp.body?.bytes() ?: return null
                if (body.size < 4 || body[0] != 0x01.toByte() || body[1] != 0x01.toByte()) return null
                if (statusCode(body) > SUCCESS_MAX) return null
                var state = -1
                val reasons = mutableListOf<String>()
                forEachAttr(body) { tag, name, value ->
                    if (name == "job-state" && (tag == TAG_ENUM || tag == TAG_INTEGER) && value.size >= 4)
                        state = readInt(value, 0)
                    if (name == "job-state-reasons" && tag == TAG_KEYWORD && value.isNotEmpty())
                        reasons.add(value.toString(Charsets.UTF_8))
                }
                if (state < 0) null else JobStateInfo(jobId, state, reasons)
            }
        } catch (e: IOException) {
            null
        }
    }

    // -------------------------------------------------------------- 请求构建

    private fun encodePrintJob(
        ep: Endpoint,
        document: ByteArray,
        title: String,
        config: PrintConfig,
        includeTemplate: Boolean,
        documentFormat: String,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x01); out.write(0x01)
        writeShort(out, OP_PRINT_JOB)
        writeInt(out, nextRequestId())

        out.write(TAG_OPERATION)
        writeAttr(out, TAG_CHARSET, "attributes-charset", "utf-8")
        writeAttr(out, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en-us")
        writeAttr(out, TAG_URI, "printer-uri", ep.ippUri)
        writeAttr(out, TAG_NAME, "requesting-user-name", "android")
        writeAttr(out, TAG_NAME, "job-name", title.take(255))
        writeAttr(out, TAG_MIME, "document-format", documentFormat)

        // 作业属性组（打印配置）
        if (includeTemplate) {
            out.write(TAG_JOB)
            writeAttr(out, TAG_KEYWORD, "print-color-mode", if (config.color) "color" else "monochrome")
            writeEnumAttr(out, "print-quality", config.quality)
            writeIntegerAttr(out, "copies", config.copies.coerceIn(1, 99))
            writeAttr(out, TAG_KEYWORD, "media", config.media)
            writeEnumAttr(out, "orientation-requested", config.orientation)
            writeAttr(out, TAG_KEYWORD, "sides", config.sides)
            // 保持原比例：等比适应页面（auto-fit），禁止打印机默认“铺满”导致拉伸变形
            writeAttr(out, TAG_KEYWORD, "print-scaling", "auto-fit")
        }

        out.write(TAG_END)
        out.write(document)
        return out.toByteArray()
    }

    private fun mediaKeyword(paper: String): String = when (paper) {
        "A3" -> "iso_a3"
        "A5" -> "iso_a5"
        "B5" -> "jis_b5"
        "Letter" -> "na_letter"
        "Legal" -> "na_legal"
        "Tabloid" -> "na_ledger"
        else -> "iso_a4"
    }

    private fun writeAttr(out: ByteArrayOutputStream, tag: Int, name: String, value: String) {
        out.write(tag)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        writeShort(out, nameBytes.size); out.write(nameBytes)
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        writeShort(out, valueBytes.size); out.write(valueBytes)
    }

    private fun writeIntegerAttr(out: ByteArrayOutputStream, name: String, value: Int) {
        out.write(TAG_INTEGER)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        writeShort(out, nameBytes.size); out.write(nameBytes)
        writeShort(out, 4); writeInt(out, value)
    }

    private fun writeEnumAttr(out: ByteArrayOutputStream, name: String, value: Int) {
        out.write(TAG_ENUM)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        writeShort(out, nameBytes.size); out.write(nameBytes)
        writeShort(out, 4); writeInt(out, value)
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 24) and 0xFF); out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
    }

    // -------------------------------------------------------------- 响应解析

    private fun statusCode(data: ByteArray): Int =
        ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)

    /** 从 Print-Job 成功响应中解析 job-id / job-uri，供按 job-id 权威等待与取消。 */
    private fun parseJobHandle(body: ByteArray, ep: Endpoint): JobHandle {
        var jobId = -1
        var jobUri = ""
        forEachAttr(body) { tag, name, value ->
            if (name == "job-id" && (tag == TAG_INTEGER || tag == TAG_ENUM) && value.size >= 4)
                jobId = readInt(value, 0)
            if (name == "job-uri" && tag == TAG_URI)
                jobUri = value.toString(Charsets.UTF_8)
        }
        return JobHandle(ep.scheme, ep.ip, ep.port, jobId, jobUri)
    }

    private fun parseResponse(data: ByteArray): PrinterProbe {
        if (data.size < 8) return PrinterProbe(reachable = false, status = PrinterStatus.OFFLINE)
        if (statusCode(data) > SUCCESS_MAX) return PrinterProbe(reachable = true, status = PrinterStatus.ERROR)

        var printerState = 3 // idle
        var makeModel: String? = null
        val levels = mutableListOf<Int>()
        val markerNames = mutableListOf<String>()
        var color: Boolean? = null
        var duplex: Boolean? = null

        // 1setOf 多值属性（marker-levels/marker-names）：第一个值带属性名，
        // 后续值 name 为空，需沿用前一个已命名属性名归并，否则只读到第一个墨盒。
        var lastName: String? = null
        var pos = 8
        while (pos + 1 < data.size) {
            val tag = data[pos].toInt() and 0xff
            if (tag == TAG_END) break
            if (tag <= 0x0f) { pos++; continue }          // 属性组标签
            if (tag == 0x7f) { pos += 5; continue }       // 扩展值标签
            if (pos + 3 > data.size) break

            val nameLen = ((data[pos + 1].toInt() and 0xff) shl 8) or (data[pos + 2].toInt() and 0xff)
            val nameStart = pos + 3
            if (nameStart + nameLen + 2 > data.size) break
            val name = String(data, nameStart, nameLen, Charsets.UTF_8)
            val vp = nameStart + nameLen
            val valLen = ((data[vp].toInt() and 0xff) shl 8) or (data[vp + 1].toInt() and 0xff)
            val vs = vp + 2
            if (vs + valLen > data.size) break
            val value = data.copyOfRange(vs, vs + valLen)

            val attrName = if (name.isNotEmpty()) name.also { lastName = it } else (lastName ?: "")

            when (attrName) {
                "printer-state" -> if ((tag == TAG_ENUM || tag == TAG_INTEGER) && valLen >= 4) printerState = readInt(value, 0)
                "printer-make-and-model" -> makeModel = value.toString(Charsets.UTF_8)
                "marker-levels" -> levels.addAll(parseIntegerValues(value))
                "marker-names" -> markerNames.addAll(parseNames(value))
                "color-supported" -> if (tag == TAG_BOOLEAN && valLen >= 1) color = value[0].toInt() != 0
                "sides-supported" -> if (tag == TAG_KEYWORD) {
                    val s = value.toString(Charsets.UTF_8)
                    when {
                        s.contains("two-sided") -> duplex = true
                        s.contains("one-sided") && !s.contains("two-sided") -> duplex = false
                    }
                }
            }
            pos = vs + valLen
        }

        val status = when (printerState) {
            5 -> PrinterStatus.ERROR
            4 -> PrinterStatus.BUSY
            else -> PrinterStatus.ONLINE
        }
        Log.d(TAG, "probe supplies: levels=$levels names=$markerNames color=$color")
        return PrinterProbe(
            reachable = true,
            status = status,
            supplies = mapLevels(levels, markerNames),
            model = makeModel,
            isColor = color,
            isDuplex = duplex,
        )
    }

    /** 遍历 IPP 响应属性，回调 (value-tag, name, value)；多值属性沿用前一个已命名属性名归并。 */
    private inline fun forEachAttr(body: ByteArray, cb: (Int, String, ByteArray) -> Unit) {
        var lastName: String? = null
        var pos = 8
        while (pos + 1 < body.size) {
            val tag = body[pos].toInt() and 0xff
            if (tag == TAG_END) break
            if (tag <= 0x0f) { pos++; continue }      // 属性组标签
            if (tag == 0x7f) { pos += 5; continue }   // 扩展值标签
            if (pos + 3 > body.size) break
            val nameLen = ((body[pos + 1].toInt() and 0xff) shl 8) or (body[pos + 2].toInt() and 0xff)
            val nameStart = pos + 3
            if (nameStart + nameLen + 2 > body.size) break
            val name = String(body, nameStart, nameLen, Charsets.UTF_8)
            val vp = nameStart + nameLen
            val valLen = ((body[vp].toInt() and 0xff) shl 8) or (body[vp + 1].toInt() and 0xff)
            val vs = vp + 2
            if (vs + valLen > body.size) break
            val attrName = if (name.isNotEmpty()) name.also { lastName = it } else (lastName ?: "")
            cb(tag, attrName, body.copyOfRange(vs, vs + valLen))
            pos = vs + valLen
        }
    }

    private fun parseIntegerValues(value: ByteArray): List<Int> {
        if (value.size < 4 || value.size % 4 != 0) return emptyList()
        return (value.indices step 4).map { readInt(value, it) }.filter { it in 0..100 }
    }

    private fun parseNames(value: ByteArray): List<String> =
        value.toString(Charsets.UTF_8)
            .split(',', '|', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun mapLevels(levels: List<Int>, names: List<String>): List<SupplyInfo> {
        if (levels.isEmpty()) return emptyList()
        return levels.mapIndexed { i, lvl ->
            val rawName = names.getOrNull(i).orEmpty()
            val color = inferColor(rawName, i, levels.size)
            SupplyInfo(
                color = color,
                name = rawName.ifBlank { defaultName(color) },
                levelPercent = lvl.coerceIn(0, 100),
            )
        }
    }

    private fun inferColor(name: String, index: Int, total: Int): SupplyColor {
        val n = name.lowercase()
        return when {
            "black" in n -> SupplyColor.BLACK
            "cyan" in n || "青" in name || "蓝" in name -> SupplyColor.CYAN
            "magenta" in n || "品红" in name || "洋红" in name -> SupplyColor.MAGENTA
            "yellow" in n || "黄" in name -> SupplyColor.YELLOW
            "photo" in n || "照片" in name -> SupplyColor.PHOTO_BLACK
            total == 1 -> SupplyColor.BLACK
            index == total - 1 -> SupplyColor.BLACK
            else -> when (index % 3) {
                0 -> SupplyColor.CYAN
                1 -> SupplyColor.MAGENTA
                else -> SupplyColor.YELLOW
            }
        }
    }

    private fun defaultName(color: SupplyColor): String = when (color) {
        SupplyColor.BLACK -> "Black"
        SupplyColor.CYAN -> "Cyan"
        SupplyColor.MAGENTA -> "Magenta"
        SupplyColor.YELLOW -> "Yellow"
        SupplyColor.PHOTO_BLACK -> "Photo Black"
    }

    private fun readInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xff) shl 24) or
            ((b[off + 1].toInt() and 0xff) shl 16) or
            ((b[off + 2].toInt() and 0xff) shl 8) or
            (b[off + 3].toInt() and 0xff)
}