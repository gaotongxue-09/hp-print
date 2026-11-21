package com.hpprint.app.ui.screen.idcard

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hpprint.app.R
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.Filters
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.util.rememberEdgeHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max

/**
 * 证件拍摄后的参数调整页：旋转 90°、滤镜（原图/增强/灰度/黑白/去阴影）、重置，
 * 预览同步刷新；确认后把处理好的位图交给调用方（返回的位图归调用方所有）。
 */
@Composable
fun CardAdjustDialog(
    title: String,
    source: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val haptic = rememberEdgeHaptic()
    val working = remember(source) { normalizeForAdjust(source) }
    var rotation by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(Filters.ORIGINAL) }
    var displayed by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var handedOff by remember { mutableStateOf(false) }

    LaunchedEffect(rotation, filter) {
        busy = true
        val out = withContext(Dispatchers.Default) {
            val rotated = if (rotation == 0) working else BitmapUtil.rotate(working, rotation.toFloat())
            val filtered = Filters.apply(rotated, filter)
            if (filtered !== rotated && rotated !== working) rotated.recycle()
            filtered
        }
        val previous = displayed
        displayed = out
        if (previous != null && previous !== working && previous !== out) previous.recycle()
        busy = false
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!handedOff) displayed?.takeIf { it !== working }?.recycle()
            if (!working.isRecycled) working.recycle()
        }
    }

    fun confirm() {
        val current = displayed ?: return
        val output = if (current === working) {
            working.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            current
        }
        handedOff = true
        haptic()
        onConfirm(output)
    }

    Dialog(
        onDismissRequest = {
            if (!busy) {
                haptic()
                onCancel()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val miuix = LocalUiMode.current == UiMode.Miuix
        val background = if (miuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.background
        val foreground = if (miuix) MiuixTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface

        Column(modifier = Modifier.fillMaxSize().background(background)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            if (!busy) {
                                haptic()
                                onCancel()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = foreground)
                }
                Text(
                    text = title,
                    color = foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                displayed?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (busy) {
                    Text(
                        text = stringResource(R.string.hp_scan_processing),
                        color = foreground.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Filters.all.forEach { mode ->
                    val label = stringResource(filterLabel(mode))
                    val selected = filter == mode
                    AdjustFilterPill(text = label, selected = selected, miuix = miuix) {
                        haptic()
                        filter = mode
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (miuix) {
                    MiuixTextButton(
                        text = stringResource(R.string.hp_card_adjust_rotate),
                        onClick = {
                            haptic()
                            rotation = (rotation + 90) % 360
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.hp_card_adjust_reset),
                        onClick = {
                            haptic()
                            rotation = 0
                            filter = Filters.ORIGINAL
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TextButton(
                        onClick = {
                            haptic()
                            rotation = (rotation + 90) % 360
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hp_card_adjust_rotate))
                    }
                    TextButton(
                        onClick = {
                            haptic()
                            rotation = 0
                            filter = Filters.ORIGINAL
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hp_card_adjust_reset))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (miuix) {
                    MiuixTextButton(
                        text = stringResource(R.string.hp_cancel),
                        onClick = {
                            if (!busy) {
                                haptic()
                                onCancel()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.hp_card_adjust_use),
                        onClick = { if (!busy) confirm() },
                        enabled = displayed != null && !busy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                } else {
                    TextButton(
                        onClick = {
                            if (!busy) {
                                haptic()
                                onCancel()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hp_cancel))
                    }
                    TextButton(
                        onClick = { if (!busy) confirm() },
                        enabled = displayed != null && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hp_card_adjust_use))
                    }
                }
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@Composable
private fun AdjustFilterPill(text: String, selected: Boolean, miuix: Boolean, onClick: () -> Unit) {
    val primary = if (miuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val onPrimary = if (miuix) MiuixTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
    val soft = if (miuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onSoft = if (miuix) MiuixTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary else soft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = if (selected) onPrimary else onSoft, fontSize = 12.sp)
    }
}

@StringRes
private fun filterLabel(mode: Int): Int = when (mode) {
    Filters.ENHANCE -> R.string.hp_scan_filter_enhance
    Filters.GRAY -> R.string.hp_scan_filter_gray
    Filters.BW -> R.string.hp_scan_filter_bw
    Filters.SHADOW -> R.string.hp_scan_filter_shadow
    else -> R.string.hp_scan_filter_original
}

/** 调整预览统一压到 1600px 内，滤镜处理更快；排版到 1:1 证件尺寸仍然足够清晰。 */
private fun normalizeForAdjust(source: Bitmap): Bitmap {
    val maxLong = 1600
    val long = max(source.width, source.height)
    return if (long <= maxLong) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        val scale = maxLong.toFloat() / long
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
