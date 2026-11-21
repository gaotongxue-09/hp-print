package com.hpprint.app.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveDialog
import com.hpprint.app.ui.util.rememberEdgeHaptic
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

/** 重命名弹窗：扫描记录与打印历史共用，双风格自适应。 */
@Composable
fun RenameDialog(
    show: Boolean,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    var text by remember(initial) { mutableStateOf(initial) }
    val title = stringResource(R.string.hp_rename)
    val trimmed = text.trim()
    val strongHaptic = rememberEdgeHaptic()
    fun tap(action: () -> Unit): () -> Unit = {
        strongHaptic()
        action()
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        MiuixTextButton(
                            text = stringResource(R.string.hp_cancel),
                            onClick = tap(onDismiss),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = stringResource(R.string.hp_ok),
                            enabled = trimmed.isNotEmpty(),
                            onClick = tap { onConfirm(trimmed) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            },
        )
        UiMode.Material -> ExpressiveDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(title) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = trimmed.isNotEmpty(), onClick = tap { onConfirm(trimmed) }) {
                    Text(stringResource(R.string.hp_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = tap(onDismiss)) {
                    Text(stringResource(R.string.hp_cancel))
                }
            },
        )
    }
}
