package com.hpprint.app.ui.util

import android.content.Context
import android.os.Build
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

private fun performScrollEndVibration(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.vibrate(
        VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

private class ScrollEndVibrationConnection(
    private val vibrate: () -> Unit,
) : NestedScrollConnection {

    // 到达过边界后置 true，避免在边界上持续拖动时反复触发；离开边界恢复为 false
    private var atBoundary = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y != 0f) {
            if (!atBoundary) {
                atBoundary = true
                vibrate()
            }
        } else {
            atBoundary = false
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(available: Velocity, consumed: Velocity): Velocity {
        if (available.y != 0f) {
            if (!atBoundary) {
                atBoundary = true
                vibrate()
            }
        } else {
            atBoundary = false
        }
        return Velocity.Zero
    }
}