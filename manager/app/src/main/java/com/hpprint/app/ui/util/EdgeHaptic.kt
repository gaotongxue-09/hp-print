package com.hpprint.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 统一的「临界反馈」震动：与列表上下滑动到边界时完全一致的清脆短震，
 * 无法驱动马达时回退到系统长按触感。
 */
@Composable
fun rememberEdgeHaptic(): () -> Unit {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    return remember(context, haptic) {
        {
            if (!performEdgeVibration(context)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
}
