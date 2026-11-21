package com.hpprint.app.ui.util

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity

/**
 * 在滚动到达顶部 / 底部边界时给出一次真实的震动反馈。
 * 使用系统 Vibrator 直接驱动震动马达，绕过 performHapticFeedback，
 * 从而不受系统「触感 / 声音」设置影响，保证有震感。
 */
fun Modifier.scrollEndVibration(): Modifier = composed {
    val context = LocalContext.current
    val vibrate: () -> Unit = remember(context) { { performScrollEndVibration(context) } }
    val connection = remember(vibrate) { ScrollEndVibrationConnection(vibrate) }
    this.nestedScroll(connection)
}

/**
 * 播放一次「临界反馈」震动（与上下滑动到边界时完全一致）。
 * 返回是否成功驱动了马达，供需要回退到系统触感的调用方使用。
 */
internal fun performEdgeVibration(context: Context): Boolean {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return false
    // 清脆的「咔哒」质感：API 29+ 用系统预定义轻触，旧版本退化为短促低幅震动。
    return runCatching {
        vibrator.vibrate(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(10L, 50)
            }
        )
    }.isSuccess
}

private fun performScrollEndVibration(context: Context) {
    performEdgeVibration(context)
}

private class ScrollEndVibrationConnection(
    private val vibrate: () -> Unit,
) : NestedScrollConnection {

    // 到达过边界后置 true，避免在边界上持续拖动时反复触发；离开边界恢复为 false
    private var atBoundary = false
    // 上次震动时间戳：短时间内的 overscroll 回弹等抖动不再重复触发，保证「只震一次」
    private var lastVibrateAt = 0L

    private fun trigger() {
        val now = SystemClock.uptimeMillis()
        if (!atBoundary && now - lastVibrateAt >= COOLDOWN_MS) {
            atBoundary = true
            lastVibrateAt = now
            vibrate()
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y != 0f) trigger() else atBoundary = false
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y != 0f) trigger() else atBoundary = false
        return Velocity.Zero
    }

    private companion object {
        const val COOLDOWN_MS = 500L
    }
}