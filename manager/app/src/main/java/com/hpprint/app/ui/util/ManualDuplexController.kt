package com.hpprint.app.ui.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.hpprint.app.R
import com.hpprint.app.hpApp
import com.hpprint.app.ui.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 手动双面打印的最终结果。 */
enum class ManualDuplexResult { SUCCESS, CANCELLED, FAILED }

/** 首页据此弹「翻面提醒」对话框/通知的状态。 */
sealed interface ManualDuplexState {
    data object Idle : ManualDuplexState
    data class AwaitingFlip(val title: String) : ManualDuplexState
}

/**
 * 手动双面打印的后台协调器（进程级，脱离打印设置页生命周期）。
 * 点击「开始打印」后立即返回首页，奇数页在后台打印；打完奇数页后把状态置为
 * [ManualDuplexState.AwaitingFlip] 并发送系统通知，首页弹窗/通知「继续」后才打偶数页。
 */
object ManualDuplexController {

    private const val TAG = "ManualDuplexCtrl"
    private const val CHANNEL_ID = "manual_duplex"
    private const val NOTIFY_ID = 1001
    private const val PERM_REQ = 2101

    const val ACTION_CONFIRM = "com.hpprint.app.action.MANUAL_DUPLEX_CONFIRM"
    const val ACTION_CANCEL = "com.hpprint.app.action.MANUAL_DUPLEX_CANCEL"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ManualDuplexState>(ManualDuplexState.Idle)
    val state: StateFlow<ManualDuplexState> = _state.asStateFlow()

    private var flipGate: CompletableDeferred<Boolean>? = null

    val isBusy: Boolean get() = _state.value != ManualDuplexState.Idle

    /**
     * 后台依次执行多份手动双面打印，每份独立结算。[onResult] 运行在 IO 线程，
     * 参数是 (文档索引, 结果)，用于回写任务状态。
     */
    fun start(
        context: Context,
        specs: List<PrintSpec>,
        onResult: suspend (index: Int, result: ManualDuplexResult) -> Unit,
    ) {
        if (_state.value != ManualDuplexState.Idle) {
            Log.w(TAG, "已有手动双面任务在进行，忽略新的启动请求")
            return
        }
        scope.launch {
            specs.forEachIndexed { i, spec ->
                val result = runOne(context, spec)
                onResult(i, result)
            }
            _state.value = ManualDuplexState.Idle
        }
    }

    /** 执行单份文档的手动双面（正面→等待翻面→背面），映射为结果。 */
    private suspend fun runOne(context: Context, spec: PrintSpec): ManualDuplexResult {
        val outcome = runCatching {
            PrintDispatcher.dispatch(
                context, spec,
                allowSystemFallback = false,
                onManualFlip = { awaitFlip(context, spec.title) },
            )
        }
        return when {
            outcome.exceptionOrNull() is PrintDispatcher.ManualDuplexCancelledException ->
                ManualDuplexResult.CANCELLED
            outcome.getOrDefault(false) -> ManualDuplexResult.SUCCESS
            else -> ManualDuplexResult.FAILED
        }
    }

    /** 在后台打印协程里被调用：进入翻面等待，直到用户「继续」或「取消」。 */
    private suspend fun awaitFlip(context: Context, title: String): Boolean {
        val gate = CompletableDeferred<Boolean>()
        flipGate = gate
        _state.value = ManualDuplexState.AwaitingFlip(title)
        showNotification(context, title)
        return try {
            gate.await()
        } finally {
            flipGate = null
            clearNotification(context)
            _state.value = ManualDuplexState.Idle
        }
    }

    /** 首页弹窗/通知「继续」——打印背面。 */
    fun confirmFlip() {
        flipGate?.complete(true)
    }

    /** 首页弹窗/通知「取消」——不再打印背面。 */
    fun cancelFlip() {
        flipGate?.complete(false)
    }

    /** 在开始手动双面打印前请求通知权限（Android 13+），拒绝则静默降级为仅首页弹窗。 */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            hpApp.currentActivity?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_REQ,
            )
        }
    }

    // ------------------------------------------------ 系统通知（辅助提醒）

    private fun showNotification(context: Context, title: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            val confirmPi = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, ManualDuplexNotificationReceiver::class.java).setAction(ACTION_CONFIRM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val cancelPi = PendingIntent.getBroadcast(
                context, 2,
                Intent(context, ManualDuplexNotificationReceiver::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val contentPi = PendingIntent.getActivity(
                context, 3,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.hp_manual_duplex_notify_title))
                .setContentText(context.getString(R.string.hp_manual_duplex_notify_text, title))
                .setContentIntent(contentPi)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.hp_manual_duplex_continue), confirmPi).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.hp_manual_duplex_cancel), cancelPi).build())
                .setOngoing(true)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFY_ID, notification)
        }.onFailure { Log.w(TAG, "发送翻面通知失败：${it.message}") }
    }

    private fun clearNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFY_ID)
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "手动双面打印", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}

/** 通知栏「继续 / 取消」按钮的广播接收器，直接完成翻面等待。 */
class ManualDuplexNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ManualDuplexController.ACTION_CONFIRM -> ManualDuplexController.confirmFlip()
            ManualDuplexController.ACTION_CANCEL -> ManualDuplexController.cancelFlip()
        }
    }
}