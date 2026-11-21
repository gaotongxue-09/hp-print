package com.hpprint.app.ui.screen.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 取景页统一的强调色与底部控制台底色。 */
internal val ScanCaptureYellow = Color(0xFFFABB06)
private val ConsoleBg = Color(0xFF0A0A0A)

/**
 * 屏幕底部的控制台：圆角面板略微上叠取景区，包含设置行与主操作行。
 */
@Composable
internal fun CaptureConsole(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = (-22).dp)
            .background(ConsoleBg, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        content = content,
    )
}

/** 设置行胶囊：等宽、34dp 高，激活时用黄色描边配色。 */
@Composable
internal fun CaptureSettingChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (active) ScanCaptureYellow.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.09f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) ScanCaptureYellow else Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** 取景区底部的状态提示胶囊，[active] 表示当前已识别到文档边缘。 */
@Composable
internal fun CaptureHintRow(
    text: String,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (active) ScanCaptureYellow else Color.White.copy(alpha = 0.4f),
                    CircleShape,
                )
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CaptureAlbumButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.width(60.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFDFCF6)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoLibrary,
                contentDescription = label,
                tint = Color(0xFF2A2A24),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun CaptureShutter(
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .border(4.dp, ScanCaptureYellow, CircleShape)
            .padding(6.dp)
            .background(if (busy) Color(0xFF8A8A8A) else Color.White, CircleShape)
            .clickable(enabled = !busy, onClick = onClick),
    )
}

@Composable
internal fun CaptureDoneBadge(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.width(60.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (count > 0) ScanCaptureYellow else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$count",
                color = if (count > 0) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
    }
}
