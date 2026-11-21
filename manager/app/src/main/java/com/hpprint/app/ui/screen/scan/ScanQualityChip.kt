package com.hpprint.app.ui.screen.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hpprint.app.R
import com.hpprint.app.data.scan.camera.ScanQuality
import com.hpprint.app.data.scan.camera.ScanTier

/**
 * 取景页的扫描清晰度胶囊：显示当前档位，点击弹出深色选择面板。
 * 外观与底部设置行其他胶囊一致。
 */
@Composable
fun ScanQualityChip(
    tier: ScanTier,
    modifier: Modifier = Modifier,
    onChange: (ScanTier) -> Unit,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    CaptureSettingChip(
        text = stringResource(tier.labelRes),
        active = false,
        modifier = modifier,
    ) { showDialog = true }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.hp_scan_quality_title),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                ScanTier.entries.forEach { item ->
                    val selected = item == tier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onChange(item)
                                showDialog = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(item.labelRes),
                            color = if (selected) Color(0xFFFABB06) else Color.White,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = stringResource(
                                R.string.hp_scan_quality_px,
                                ScanQuality.megaPixels(context, item),
                            ),
                            color = if (selected) Color(0xFFFABB06) else Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showDialog = false }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.hp_cancel),
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
