package com.hpprint.app.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 无底色、无水波纹的图标按钮：只有图标本身在按压时轻微缩小，
 * 并伴随一次「临界反馈」震动，适合列表行内的重命名 / 删除等次级操作。
 */
@Composable
fun PressableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberEdgeHaptic()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        label = "pressableIconScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
