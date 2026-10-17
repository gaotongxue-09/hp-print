package com.hpprint.app.ui.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.hpprint.app.BuildConfig
import com.hpprint.app.Natives
import com.hpprint.app.core.tasks.BootKernelVersion
import com.hpprint.app.core.tasks.ExtractImage
import com.hpprint.app.core.tasks.ProbeResult
import com.hpprint.app.core.utils.DataSourceChannel
import com.hpprint.app.hpApp
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

private fun getKsuDaemonPath(): String {
    return hpApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
}

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

object KsuCli {
    val SHELL: Shell = createRootShell()
    val GLOBAL_MNT_SHELL: Shell = createRootShell(true)
}

fun getRootShell(globalMnt: Boolean = false): Shell {
    return if (globalMnt) KsuCli.GLOBAL_MNT_SHELL else {
        KsuCli.SHELL
    }
}

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    var fileName: String? = null
    val contentResolver: ContentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(this, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
    }
    return fileName
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        if (globalMnt) {
            builder.build(getKsuDaemonPath(), "debug", "su", "-g")
        } else {
            builder.build(getKsuDaemonPath(), "debug", "su")
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ksu failed: ", e)
        try {
            if (globalMnt) {
                builder.build("su", "-mm")
            } else {
                builder.build("su")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "su failed: ", e)
            builder.build("sh")
        }
    }
}

fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean = true

suspend fun getFeatureStatus(feature: String): String = "1"

suspend fun getFeaturePersistValue(feature: String): Long? = 1L

fun install() {
    Log.i(TAG, "install (demo, no-op)")
}

fun listModules(): String = "[]"

fun getModuleCount(): Int = 0

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    Log.i(TAG, "toggleModule $id enable=$enable (demo) -> true")
    return true
}

fun undoUninstallModule(id: String): Boolean {
    Log.i(TAG, "undo uninstall module $id (demo) -> true")
    return true
}

fun uninstallModule(id: String): Boolean {
    Log.i(TAG, "uninstall module $id (demo) -> true")
    return true
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    return withNewRootShell {
        newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    }
}

fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Installing module... (demo)")
    onStdout("- Done")
    return FlashResult(0, "", false)
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    onStdout("- Running module action... (demo)")
    return true
}

fun restoreBoot(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Restoring boot image... (demo)")
    return FlashResult(0, "", false)
}

fun uninstallPermanently(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    onStdout("- Uninstalling... (demo)")
    return FlashResult(0, "", false)
}

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

private fun writeLkmFile(lkm: LkmSelection): File? {
    if (lkm !is LkmSelection.LkmUri) return null
    val file = File(hpApp.cacheDir, "kernelsu-tmp-lkm.ko")
    hpApp.contentResolver.openInputStream(lkm.uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}

private fun bootPatchFlags(
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
): String = buildString {
    if (allowShell) append(" --allow-shell")
    if (enableAdb) append(" --enable-adbd")
    if (forceBackup) append(" --backup")
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    onStdout("- Patching boot image... (demo)")
    onStdout("- Done")
    val showReboot = bootUri == null
    return FlashResult(0, "", showReboot)
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    onStdout("- Downloading and extracting boot image... (demo)")
    onStdout("- Done")
    return FlashResult(0, "", false)
}

suspend fun probeRemoteBootPartitions(url: String): ProbeResult =
    ProbeResult(partitions = listOf("boot", "init_boot", "vendor_boot"), kmi = null)

private fun newDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readMagic(channel: DataSourceChannel): String {
    val buffer = ByteBuffer.allocate(4)
    channel.read(buffer)
    channel.position(0)
    return String(buffer.array(), StandardCharsets.ISO_8859_1)
}

fun reboot(reason: String = "") {
    Log.i(TAG, "reboot reason=$reason (demo, no-op)")
}

fun rootAvailable(): Boolean = true

suspend fun getCurrentKmi(): String = "5.10.157-android12-9-00001"

suspend fun getSupportedKmis(): List<String> =
    listOf("5.10.157-android12-9-00001", "5.15.153-android13-8-00004")

suspend fun isAbDevice(): Boolean = true

suspend fun getDefaultPartition(): String = "init_boot"

suspend fun getSlotSuffix(ota: Boolean): String = "_a"

suspend fun getAvailablePartitions(): List<String> =
    listOf("boot", "init_boot", "vendor_boot")

fun hasMagisk(): Boolean = false

fun isSepolicyValid(rules: String?): Boolean = true

fun getSepolicy(pkg: String): String = ""

fun setSepolicy(pkg: String, rules: String): Boolean = true

fun listAppProfileTemplates(): List<String> = emptyList()

fun getAppProfileTemplate(id: String): String = ""

fun setAppProfileTemplate(id: String, template: String): Boolean = true

fun deleteAppProfileTemplate(id: String): Boolean = true

fun forceStopApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "forceStopApp $packageName (demo, no-op)")
}

fun launchApp(packageName: String, userId: Int? = null) {
    Log.i(TAG, "launchApp $packageName (demo, no-op)")
}

fun restartApp(packageName: String, userId: Int? = null) {
    forceStopApp(packageName, userId)
    launchApp(packageName, userId)
}
