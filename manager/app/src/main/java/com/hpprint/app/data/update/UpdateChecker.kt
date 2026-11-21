package com.hpprint.app.data.update

import com.hpprint.app.BuildConfig
import com.hpprint.app.ui.util.checkNewVersion
import com.hpprint.app.ui.util.isVersionNewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReleaseInfo(
    val versionName: String,
    val changelog: String,
    val apkUrl: String,
)

/** 应用内更新适配层：版本检测统一走 [checkNewVersion]，避免重复实现。 */
object UpdateChecker {

    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val info = checkNewVersion()
        if (info.versionCode <= 0 || info.downloadUrl.isEmpty()) return@withContext null
        ReleaseInfo(
            versionName = info.versionName.ifBlank { info.versionCode.toString() },
            changelog = info.changelog,
            apkUrl = info.downloadUrl,
        )
    }

    fun isNewer(latestVersion: String, currentVersion: String = BuildConfig.VERSION_NAME): Boolean =
        isVersionNewer(latestVersion, currentVersion)
}
