package com.hpprint.app.ui.screen.printer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.SupplyInfo
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.statustag.StatusTag
import com.hpprint.app.ui.hp.printerStatusColors
import com.hpprint.app.ui.hp.printerStatusLabel
import com.hpprint.app.ui.hp.supplyColorHex
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.viewmodel.PrinterDetailViewModel
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
fun PrinterDetailScreen(printerId: String) {
    val viewModel = viewModel<PrinterDetailViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(printerId) { viewModel.load(printerId) }

    val printer = state.printer
    val onBack = { navigator.pop() }
    val onSetDefault = { viewModel.setDefault(printerId) }
    val onProbe = { viewModel.probe(printerId) }
    val onRemove = { viewModel.remove(printerId) { navigator.pop() } }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PrinterDetailMiuix(printer, state.isProbing, onSetDefault, onProbe, onRemove, onBack)
        UiMode.Material -> PrinterDetailMaterial(printer, state.isProbing, onSetDefault, onProbe, onRemove, onBack)
    }
}

@Composable
private fun PrinterDetailMiuix(
    printer: com.hpprint.app.data.model.hp.Printer?,
    isProbing: Boolean,
    onSetDefault: () -> Unit,
    onProbe: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_printer_detail),
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
            if (printer == null) {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    BasicComponent(title = stringResource(R.string.hp_no_printer))
                }
            } else {
                val (bg, fg) = printerStatusColors(printer.status)
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    BasicComponent(
                        title = printer.name,
                        summary = printer.model,
                        startAction = { Icon(Icons.Filled.Print, printer.name, modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        endActions = {
                            StatusTag(label = printerStatusLabel(printer.status), backgroundColor = bg, contentColor = fg)
                        },
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(title = stringResource(R.string.hp_printer_model), summary = printer.model)
                    BasicComponent(title = stringResource(R.string.hp_ip_address), summary = printer.ip)
                    BasicComponent(title = stringResource(R.string.hp_port), summary = printer.port.toString())
                    BasicComponent(title = stringResource(R.string.hp_connection), summary = if (printer.ip.isNotBlank()) "IPP / $printer.ip" else "-")
                }
                if (printer.supplies.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        printer.supplies.forEach { supply ->
                            BasicComponent(
                                title = supply.name,
                                summary = "${supply.levelPercent}%",
                                startAction = {
                                    Box(modifier = Modifier.padding(end = 12.dp).size(14.dp).background(supplyColorHex(supply.color), CircleShape))
                                },
                                endActions = {
                                    if (supply.isLow) {
                                        StatusTag(label = stringResource(R.string.hp_low_ink), backgroundColor = Color(0xFFFCE8E6), contentColor = Color(0xFFB3261E))
                                    }
                                },
                            )
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.hp_set_default),
                        summary = if (printer.isDefault) stringResource(R.string.hp_default) else "",
                        startAction = { Icon(Icons.Rounded.Star, stringResource(R.string.hp_set_default), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        enabled = !printer.isDefault,
                        onClick = onSetDefault,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.hp_test_probe),
                        summary = if (isProbing) stringResource(R.string.hp_searching) else stringResource(R.string.hp_ip_address),
                        startAction = { Icon(Icons.Rounded.NetworkCheck, stringResource(R.string.hp_test_probe), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        enabled = !isProbing,
                        onClick = onProbe,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.hp_remove_printer),
                        summary = stringResource(R.string.hp_remove),
                        startAction = { Icon(Icons.Rounded.Delete, stringResource(R.string.hp_remove_printer), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        onClick = onRemove,
                    )
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
private fun PrinterDetailMaterial(
    printer: com.hpprint.app.data.model.hp.Printer?,
    isProbing: Boolean,
    onSetDefault: () -> Unit,
    onProbe: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_printer_detail)) },
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
            if (printer != null) {
                val (bg, fg) = printerStatusColors(printer.status)
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(printer.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(printer.model) },
                            leadingContent = { Icon(Icons.Filled.Print, printer.name) },
                            trailingContent = { StatusTag(label = printerStatusLabel(printer.status), backgroundColor = bg, contentColor = fg) },
                        )
                    }
                }
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_printer_model)) },
                            trailingContent = { Text(printer.model) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_ip_address)) },
                            trailingContent = { Text(printer.ip) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_port)) },
                            trailingContent = { Text(printer.port.toString()) },
                        )
                    }
                }
                if (printer.supplies.isNotEmpty()) {
                    SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_supplies)) {
                        printer.supplies.forEach { supply ->
                            item {
                                SegmentedListItem(
                                    headlineContent = { Text(supply.name) },
                                    leadingContent = {
                                        Box(modifier = Modifier.size(14.dp).background(supplyColorHex(supply.color), CircleShape))
                                    },
                                    trailingContent = {
                                        Text(
                                            if (supply.isLow) stringResource(R.string.hp_low_ink) else "${supply.levelPercent}%",
                                            color = if (supply.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            onClick = onSetDefault,
                            enabled = !printer.isDefault,
                            headlineContent = { Text(stringResource(R.string.hp_set_default)) },
                            supportingContent = { Text(if (printer.isDefault) stringResource(R.string.hp_default) else "") },
                            leadingContent = { Icon(Icons.Rounded.Star, stringResource(R.string.hp_set_default)) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = onProbe,
                            enabled = !isProbing,
                            headlineContent = { Text(stringResource(R.string.hp_test_probe)) },
                            supportingContent = { Text(if (isProbing) stringResource(R.string.hp_searching) else printer.ip) },
                            leadingContent = { Icon(Icons.Rounded.NetworkCheck, stringResource(R.string.hp_test_probe)) },
                        )
                    }
                    item {
                        SegmentedListItem(
                            onClick = onRemove,
                            headlineContent = { Text(stringResource(R.string.hp_remove_printer)) },
                            leadingContent = { Icon(Icons.Rounded.Delete, stringResource(R.string.hp_remove_printer)) },
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