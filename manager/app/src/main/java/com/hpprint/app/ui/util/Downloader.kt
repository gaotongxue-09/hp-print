package com.hpprint.app.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import com.hpprint.app.hpApp
import com.hpprint.app.ui.util.module.LatestVersionInfo
import okhttp3.Request

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

fun checkNewVersion(): LatestVersionInfo {
    return LatestVersionInfo()
}
