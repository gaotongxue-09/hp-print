package com.hpprint.app.ui.component.choosekmidialog

import androidx.compose.runtime.Composable
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode

@Composable
fun ChooseKmiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ChooseKmiDialogMiuix(show, onDismissRequest, onSelected)
        UiMode.Material -> ChooseKmiDialogMaterial(show, onDismissRequest, onSelected)
    }
}
