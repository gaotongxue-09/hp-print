package com.hpprint.app.data.ipp

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * eSCL 扫描客户端（OkHttp 实现），流程与参考项目 HpPrintScanner 的 ESCLScanner 一致：
 *  1. GET /eSCL/ScannerStatus → 检查 State=Idle + 稿台最大扫描区域；
 *  2. POST /eSCL/ScanJobs → 完整 ScanSettings XML（分辨率/色彩/区域/格式）；
 *  3. GET .../NextDocument → 180s 长读超时 + 断线重连重试（最多 6 次、退避 1200ms*attempt）；
 *  4. DELETE 结束任务。
 *
 * 相比旧版（HttpURLConnection + ScannerCapabilities + 单一 25s 超时），修复了
 * 「扫描到一半失败、打印机有动作但无结果」的问题：NextDocument 是一条挂起长连接，
 * 部分惠普固件会把它当空闲连接提前关闭，必须给足长超时并在中断后重连重试。
 * 返回扫描图片字节（JPEG/PNG），失败返回 null。
 */
object PrinterScan {

    private const val TAG = "HpScan"
    private const val NS_SCAN = "http://schemas.hp.com/imaging/escl/2011/05/03"
    private const val NS_PWG = "http://www.pwg.org/schemas/2010/12/sm"

    // 普通请求客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // NextDocument 长连接客户端（读超时给足）
    private val longClient = client.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(190, TimeUnit.SECONDS)
        .build()

    suspend fun scan(ip: String, timeoutMs: Int = 25000): ByteArray? = withContext(Dispatchers.IO) {
        // 不同打印机 eSCL 服务可能跑在不同端口；依次尝试 80/8080/443（明文 HTTP，避免自签证书问题）。
        val endpoints = listOf(80, 8080, 443)
        for (port in endpoints) {
            val base = "http://$ip:$port"
            runCatching { scanBase(base) }.getOrNull()?.let {
                if (it.isNotEmpty()) return@withContext it
            }
        }
        null
    }

    private fun scanBase(baseUrl: String): ByteArray {
        val (idle, maxW, maxH) = scannerStatus(baseUrl)
        if (!idle) throw IllegalStateException("扫描仪未就绪（被占用或预热中），请稍后重试")

        val jobUri = createScanJob(baseUrl, maxW, maxH)
        Log.d(TAG, "扫描任务已创建: $jobUri")

        // 立即拉取（内部含长连接 + 断线重连重试）
        val bytes = fetchWithRetry(jobUri)
        deleteJob(jobUri)
        return bytes
    }

    // ================= 协议 =================

    private fun scannerStatus(baseUrl: String): Triple<Boolean, Int, Int> {
        val url = "$baseUrl/eSCL/ScannerStatus"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("ScannerStatus 返回 ${resp.code}")
            val xml = resp.body?.string().orEmpty()
            return parseStatus(xml)
        }
    }

    private fun parseStatus(xml: String): Triple<Boolean, Int, Int> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var state = ""
        var inPlaten = false
        var w = 0
        var h = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "State" -> state = parser.nextText()
                    "Platen" -> inPlaten = true
                    "Adf" -> inPlaten = false
                    "MaxWidth", "MaxScanWidth" -> if (inPlaten) {
                        parser.nextText().toIntOrNull()?.let { if (it > w) w = it }
                    }
                    "MaxHeight", "MaxScanHeight" -> if (inPlaten) {
                        parser.nextText().toIntOrNull()?.let { if (it > h) h = it }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "Platen") inPlaten = false
            }
            event = parser.next()
        }
        return Triple(state == "Idle", w, h)
    }

    private fun createScanJob(baseUrl: String, maxW: Int, maxH: Int): String {
        val url = "$baseUrl/eSCL/ScanJobs"
        val width = if (maxW > 0) maxW else 2480
        val height = if (maxH > 0) maxH else 3508
        val body = buildScanJobXml(width, height)
            .toRequestBody("text/xml; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/xml")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("创建扫描任务失败 code=${resp.code} body=${resp.body?.string()}")
            }
            val location = resp.header("Location")
            val text = resp.body?.string().orEmpty()
            return extractJobUri(baseUrl, location, text)
        }
    }

    /** 长连接拉取 + 断线重连重试 */
    private fun fetchWithRetry(jobUri: String): ByteArray {
        val url = if (jobUri.endsWith("/NextDocument")) jobUri else "$jobUri/NextDocument"
        val maxAttempts = 6
        var lastErr: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                Log.d(TAG, "GET $url （第 $attempt 次，等待扫描完成...）")
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "image/jpeg, image/png, application/octet-stream")
                    .build()

                longClient.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        200 -> {
                            val bytes = resp.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                Log.d(TAG, "已收到图像 ${bytes.size / 1024} KB")
                                return bytes
                            }
                            lastErr = IOException("返回内容为空")
                        }
                        204 -> throw IOException("扫描仪未返回图像（请确认稿件已放好）")
                        // 未就绪 / 任务处理中 / 连接被抢先：稍后重试
                        404, 409, 500, 503 -> lastErr = IOException("设备返回 ${resp.code}（处理中）")
                        else -> lastErr = IOException("拉取图像 code=${resp.code}")
                    }
                }
            } catch (e: Exception) {
                lastErr = e
                Log.w(TAG, "本次连接中断: ${e.message}")
            }
            // 退避后重连
            Thread.sleep(1200L * attempt)
        }
        throw IOException("多次重连仍未取到图像: ${lastErr?.message}")
    }

    private fun deleteJob(jobUri: String) {
        try {
            val req = Request.Builder().url(jobUri).delete().build()
            client.newCall(req).execute().use { }
            Log.d(TAG, "扫描任务已结束")
        } catch (e: Exception) {
            Log.w(TAG, "结束任务失败（可忽略）: ${e.message}")
        }
    }

    // ================= 工具 =================

    private fun buildScanJobXml(width: Int, height: Int): String {
        val r = 300
        return """<?xml version="1.0" encoding="UTF-8"?>
<scan:ScanSettings xmlns:scan="$NS_SCAN" xmlns:pwg="$NS_PWG">
  <pwg:Version>2.0</pwg:Version>
  <pwg:ScanResolution>$r</pwg:ScanResolution>
  <scan:XResolution>$r</scan:XResolution>
  <scan:YResolution>$r</scan:YResolution>
  <scan:ColorMode>RGB24</scan:ColorMode>
  <pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>
  <scan:InputSource>Platen</scan:InputSource>
  <scan:ScanRegions>
    <scan:ScanRegion>
      <scan:Width>$width</scan:Width>
      <scan:Height>$height</scan:Height>
      <scan:XOffset>0</scan:XOffset>
      <scan:YOffset>0</scan:YOffset>
      <pwg:ContentRegionUnits>escl:ThreeHundredthsOfInches</pwg:ContentRegionUnits>
    </scan:ScanRegion>
  </scan:ScanRegions>
</scan:ScanSettings>"""
    }

    private fun extractJobUri(baseUrl: String, locationHeader: String?, xml: String): String {
        var jobUri = locationHeader?.trim().orEmpty()
        if (jobUri.isEmpty()) {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    (parser.name == "JobUri" || parser.name == "jobUri")
                ) {
                    jobUri = parser.nextText().trim()
                    break
                }
                event = parser.next()
            }
        }
        if (jobUri.isEmpty()) throw IllegalStateException("响应中未找到任务地址")
        if (!jobUri.startsWith("http")) {
            jobUri = baseUrl.trimEnd('/') + "/" + jobUri.trimStart('/')
        }
        return jobUri
    }
}