package com.hpprint.app.ui.util

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hpprint.app.R
import com.hpprint.app.data.update.ReleaseInfo
import com.hpprint.app.data.update.UpdateChecker
import com.hpprint.app.data.update.UpdateInstaller
import com.hpprint.app.ui.component.dialog.rememberConfirmDialog
import com.hpprint.app.ui.component.dialog.rememberLoadingDialog
import kotlinx.coroutines.launch

@Composable
fun rememberUpdateChecker(): (manual: Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()

    var pending by remember { mutableStateOf<ReleaseInfo?>(null) }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            pending?.let { release ->
                scope.launch {
                    val file = loadingDialog.withLoading {
                        UpdateInstaller.downloadApk(release.apkUrl, release.versionName)
                    }
                    if (file != null) {
                        UpdateInstaller.installApk(context, file)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_download_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    )

    return { manual ->
        scope.launch {
            val release = if (manual) {
                loadingDialog.withLoading { UpdateChecker.fetchLatestRelease() }
            } else {
                UpdateChecker.fetchLatestRelease()
            }
            if (release != null && UpdateChecker.isNewer(release.versionName)) {
                pending = release
                confirmDialog.showConfirm(
                    title = context.getString(R.string.update_available_title, release.versionName),
                    content = release.changelog.ifBlank { context.getString(R.string.update_no_changelog) },
                    markdown = true,
                    confirm = context.getString(R.string.update_download),
                    dismiss = context.getString(R.string.update_later),
                )
            } else if (manual) {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_latest),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}