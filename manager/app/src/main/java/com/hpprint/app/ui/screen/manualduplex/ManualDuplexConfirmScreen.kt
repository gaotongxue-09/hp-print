package com.hpprint.app.ui.screen.manualduplex

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.util.ManualDuplexController
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 手动双面「翻面确认页」。打印控制器进入 AwaitingFlip 后由导航器强制跳转到本页，
 * 用户点「继续」才打偶数页；点「取消」或系统返回则中止背面打印。
 */
@Composable
fun ManualDuplexConfirmScreen(title: String) {
    val haptic = LocalHapticFeedback.current
    val onConfirm = {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        ManualDuplexController.confirmFlip()
    }
    val onCancel = {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        ManualDuplexController.cancelFlip()
    }

    // 拦截系统返回：等价于“取消翻面”，避免退到上一页后又被导航器拉回确认页
    BackHandler { onCancel() }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfirmMiuix(title, onConfirm, onCancel)
        UiMode.Material -> ConfirmMaterial(title, onConfirm, onCancel)
    }
}

@Composable
private fun ConfirmMiuix(title: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_duplex_manual),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixText(
                        text = stringResource(R.string.hp_manual_duplex_notify_title),
                        fontSize = 16.sp,
                    )
                    if (title.isNotBlank()) {
                        MiuixText(text = title, fontSize = 13.sp)
                    }
                    MiuixText(
                        text = stringResource(R.string.hp_manual_duplex_prompt),
                        fontSize = 14.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiuixButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    MiuixText(stringResource(R.string.hp_manual_duplex_cancel), fontSize = 16.sp)
                }
                MiuixButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(stringResource(R.string.hp_manual_duplex_continue), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ConfirmMaterial(title: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { MaterialText(stringResource(R.string.hp_duplex_manual)) },
                navigationIcon = { TopBarBackButton(onClick = onCancel) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MaterialText(
                text = stringResource(R.string.hp_manual_duplex_notify_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (title.isNotBlank()) {
                MaterialText(text = title, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            MaterialText(
                text = stringResource(R.string.hp_manual_duplex_prompt),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                MaterialText(stringResource(R.string.hp_manual_duplex_continue))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                MaterialText(stringResource(R.string.hp_manual_duplex_cancel))
            }
            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}