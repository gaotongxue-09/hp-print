package com.hpprint.app.ui.screen.printer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.miuix.EditText
import com.hpprint.app.ui.component.statustag.StatusTag
import com.hpprint.app.ui.hp.printerStatusColors
import com.hpprint.app.ui.hp.printerStatusLabel
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.viewmodel.PrinterViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddPrinterScreen() {
    val viewModel = viewModel<PrinterViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    var name by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("631") }

    val onAdd = {
        if (ip.isNotBlank()) {
            viewModel.addManual(
                name.ifBlank { ip },
                model.ifBlank { "HP Printer" },
                ip,
                port.toIntOrNull() ?: 631,
            )
        }
    }
    val onBack = { navigator.pop() }
    val openDetail: (String) -> Unit = { id -> navigator.push(Route.PrinterDetail(id)) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> AddPrinterMiuix(state, name, { name = it }, model, { model = it }, ip, { ip = it }, port, { port = it }, viewModel::discover, onAdd, onBack, openDetail)
        UiMode.Material -> AddPrinterMaterial(state, name, { name = it }, model, { model = it }, ip, { ip = it }, port, { port = it }, viewModel::discover, onAdd, onBack, openDetail)
    }
}

@Composable
private fun AddPrinterMiuix(
    state: com.hpprint.app.ui.viewmodel.PrinterUiState,
    name: String, onName: (String) -> Unit,
    model: String, onModel: (String) -> Unit,
    ip: String, onIp: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    onDiscover: () -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_add_printer),
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_discover),
                    summary = if (state.isDiscovering) stringResource(R.string.hp_searching) else stringResource(R.string.hp_discover),
                    startAction = { Icon(Icons.Rounded.Radar, stringResource(R.string.hp_discover), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = !state.isDiscovering,
                    onClick = onDiscover,
                )
                if (state.discovered.isEmpty() && !state.isDiscovering) {
                    BasicComponent(
                        title = stringResource(R.string.hp_no_discovered),
                        summary = stringResource(R.string.hp_manual_add),
                        startAction = { Icon(Icons.Filled.Print, null, modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                EditText(title = stringResource(R.string.hp_printer_name), value = name, onValueChange = onName, textHint = stringResource(R.string.hp_printer_name))
                EditText(title = stringResource(R.string.hp_printer_model), value = model, onValueChange = onModel, textHint = stringResource(R.string.hp_printer_model))
                EditText(title = stringResource(R.string.hp_ip_address), value = ip, onValueChange = onIp, textHint = stringResource(R.string.hp_ip_address), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                EditText(title = stringResource(R.string.hp_port), value = port, onValueChange = onPort, textHint = stringResource(R.string.hp_port), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                ArrowPreference(
                    title = stringResource(R.string.hp_add),
                    summary = stringResource(R.string.hp_manual_add),
                    startAction = { Icon(Icons.Filled.Print, stringResource(R.string.hp_add), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = ip.isNotBlank(),
                    onClick = onAdd,
                )
            }

            if (state.printers.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    state.printers.forEach { printer ->
                        val (bg, fg) = printerStatusColors(printer.status)
                        BasicComponent(
                            title = printer.name,
                            summary = "${printer.model} · ${printer.ip}",
                            startAction = { Icon(Icons.Filled.Print, printer.name, modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            endActions = {
                                StatusTag(label = printerStatusLabel(printer.status), backgroundColor = bg, contentColor = fg)
                            },
                        )
                    }
                }
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

@Composable
private fun AddPrinterMaterial(
    state: com.hpprint.app.ui.viewmodel.PrinterUiState,
    name: String, onName: (String) -> Unit,
    model: String, onModel: (String) -> Unit,
    ip: String, onIp: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    onDiscover: () -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_add_printer)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onDiscover,
                        headlineContent = { Text(stringResource(R.string.hp_discover)) },
                        supportingContent = {
                            Text(if (state.isDiscovering) stringResource(R.string.hp_searching) else stringResource(R.string.hp_no_discovered))
                        },
                        leadingContent = { Icon(Icons.Rounded.Radar, stringResource(R.string.hp_discover)) },
                    )
                }
            }

            OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hp_printer_name)) }, singleLine = true)
            OutlinedTextField(value = model, onValueChange = onModel, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hp_printer_model)) }, singleLine = true)
            OutlinedTextField(value = ip, onValueChange = onIp, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hp_ip_address)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(value = port, onValueChange = onPort, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.hp_port)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = onAdd, enabled = ip.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Print, null)
                Text(stringResource(R.string.hp_add), modifier = Modifier.padding(start = 8.dp))
            }

            if (state.printers.isNotEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_printers)) {
                    state.printers.forEach { printer ->
                        item {
                            val (bg, fg) = printerStatusColors(printer.status)
                            SegmentedListItem(
                                onClick = { onOpenDetail(printer.id) },
                                headlineContent = { Text(printer.name) },
                                supportingContent = { Text("${printer.model} · ${printer.ip}") },
                                leadingContent = { Icon(Icons.Filled.Print, printer.name) },
                                trailingContent = { StatusTag(label = printerStatusLabel(printer.status), backgroundColor = bg, contentColor = fg) },
                            )
                        }
                    }
                }
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