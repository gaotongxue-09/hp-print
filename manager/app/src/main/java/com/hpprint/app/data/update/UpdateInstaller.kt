package com.hpprint.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hpprint.app.BuildConfig
import com.hpprint.app.hpApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

object UpdateInstaller {

    suspend fun downloadApk(url: String, versionName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            hpApp.okhttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body
                val file = File(hpApp.cacheDir, "HP_Print_update_$versionName.apk")
                file.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
                return@runCatching file
            }
        }.getOrNull()
    }

    fun installApk(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}