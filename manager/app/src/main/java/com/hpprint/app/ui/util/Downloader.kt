package com.hpprint.app.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import com.hpprint.app.BuildConfig
import com.hpprint.app.hpApp
import com.hpprint.app.ui.util.module.LatestVersionInfo
import okhttp3.Request
import org.json.JSONObject

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {}
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = hpApp,
        url = url,
        fileName = fileName,
        onCompleted = onDownloaded,
    )

    DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        hpApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/gaotongxue-09/hp-print/releases/latest"

/** APK 资产名中的版本信息：如 HP_Print_v1.0.0_30000-release.apk */
private val APK_VERSION_REGEX = Regex("v(.+?)_(\\d+)-", RegexOption.IGNORE_CASE)

/**
 * 联网检查最新版本（同步阻塞，需在 IO 线程调用）。
 * 优先从 APK 资产名解析版本号；解析失败时用 tag 兜底。
 */
fun checkNewVersion(): LatestVersionInfo {
    return runCatching {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        hpApp.okhttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return LatestVersionInfo()
            val body = response.body?.string().orEmpty()
            if (body.isEmpty()) return LatestVersionInfo()

            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            val versionName = tag.removePrefix("v").removePrefix("V")
            val changelog = json.optString("body")

            var versionCode = 0
            var downloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name").orEmpty()
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    downloadUrl = asset.optString("browser_download_url").orEmpty()
                    versionCode = APK_VERSION_REGEX.find(name)
                        ?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                    if (versionCode > 0) break
                }
            }
            if (downloadUrl.isEmpty()) return LatestVersionInfo()

            if (versionCode <= 0 && versionName.isNotEmpty()) {
                versionCode = if (isVersionNewer(versionName)) {
                    BuildConfig.VERSION_CODE + 1
                } else {
                    BuildConfig.VERSION_CODE
                }
            }

            LatestVersionInfo(
                versionCode = versionCode,
                downloadUrl = downloadUrl,
                changelog = changelog,
                versionName = versionName,
            )
        }
    }.getOrDefault(LatestVersionInfo())
}

/** 语义化版本比较：latest 是否比 current 新。 */
fun isVersionNewer(latest: String, current: String = BuildConfig.VERSION_NAME): Boolean {
    val latestParts = latest.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
    if (latestParts.isEmpty() || currentParts.isEmpty()) return false
    val size = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until size) {
        val a = latestParts.getOrElse(i) { 0 }
        val b = currentParts.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
