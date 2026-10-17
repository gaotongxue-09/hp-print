package com.hpprint.app.ui.screen.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.ui.LocalMainPagerState
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TonalCard
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.statustag.StatusTag
import com.hpprint.app.ui.hp.describeFile
import com.hpprint.app.ui.hp.HP_PRINT_DOC_MIME_TYPES
import com.hpprint.app.ui.hp.formatTimestamp
import com.hpprint.app.ui.hp.jobStatusColors
import com.hpprint.app.ui.hp.jobStatusLabel
import com.hpprint.app.ui.hp.printerStatusColors
import com.hpprint.app.ui.hp.printerStatusLabel
import com.hpprint.app.ui.hp.supplyColorHex
import com.hpprint.app.ui.hp.supplyColorLabel
import com.hpprint.app.ui.navigation3.Navigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.viewmodel.HomeDashboardViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.hpprint.app.ui.util.scrollEndVibration
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<HomeDashboardViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mainState = LocalMainPagerState.current

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                val (kind, title) = describeFile(context, uri)
                PrintFile(kind.name, uri.toString(), title)
            }
            navigator.push(Route.PrintSetup(files = files))
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.map { uri ->
                val (_, title) = describeFile(context, uri)
                PrintFile("IMAGE", uri.toString(), title)
            }
            navigator.push(Route.PrintSetup(files = files))
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> DashboardMiuix(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onPrintDocuments = { docPicker.launch(HP_PRINT_DOC_MIME_TYPES) },
            onPrintPhotos = { photoPicker.launch(arrayOf("image/*")) },
            onWebPrint = { navigator.push(Route.WebPrint) },
            onScan = { mainState.animateToPage(2) },
            onManagePrinters = { navigator.push(Route.AddPrinter) },
            onSelectPrinter = viewModel::setDefault,
            onRenamePrinter = viewModel::renamePrinter,
            onRefreshPrinters = viewModel::refreshPrinters,
            isRefreshing = isRefreshing,
        )

        UiMode.Material -> DashboardMaterial(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onPrintDocuments = { docPicker.launch(HP_PRINT_DOC_MIME_TYPES) },
            onPrintPhotos = { photoPicker.launch(arrayOf("image/*")) },
            onWebPrint = { navigator.push(Route.WebPrint) },
            onScan = { mainState.animateToPage(2) },
            onManagePrinters = { navigator.push(Route.AddPrinter) },
            onSelectPrinter = viewModel::setDefault,
            onRenamePrinter = viewModel::renamePrinter,
            onRefreshPrinters = viewModel::refreshPrinters,
            isRefreshing = isRefreshing,
        )
    }
}

private data class DashboardActions(
    val onPrintDocuments: () -> Unit,
    val onPrintPhotos: () -> Unit,
    val onWebPrint: () -> Unit,
    val onScan: () -> Unit,
    val onManagePrinters: () -> Unit,
)

@Composable
private fun DashboardMiuix(
    state: com.hpprint.app.ui.viewmodel.HomeDashboardUiState,
    bottomInnerPadding: Dp,
    onPrintDocuments: () -> Unit,
    onPrintPhotos: () -> Unit,
    onWebPrint: () -> Unit,
    onScan: () -> Unit,
    onManagePrinters: () -> Unit,
    onSelectPrinter: (String) -> Unit,
    onRenamePrinter: (String, String) -> Unit,
    onRefreshPrinters: () -> Unit,
    isRefreshing: Boolean,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.app_name),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onRefreshPrinters) {
                            Icon(
                                Icons.Rounded.Refresh,
                                stringResource(R.string.hp_refresh),
                                tint = MiuixTheme.colorScheme.onBackground.copy(alpha = if (isRefreshing) 0.4f else 1f),
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic(HapticFeedbackType.LongPress)
                    .scrollEndVibration()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    PrinterStatusCardMiuix(state, onManagePrinters, onRenamePrinter)
                    InkLevelsMiuix(state.defaultPrinter)
                    if (state.allPrinters.size > 1) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.hp_default_printer),
                                summary = state.defaultPrinter?.name ?: "",
                                items = state.allPrinters.map { it.name },
                                startAction = {
                                    Icon(Icons.Filled.Print, stringResource(R.string.hp_default_printer), modifier = Modifier.padding(end = 6.dp))
                                },
                                selectedIndex = state.allPrinters.indexOfFirst { it.id == state.defaultPrinter?.id }.coerceAtLeast(0),
                                onSelectedIndexChange = { idx -> state.allPrinters.getOrNull(idx)?.let { onSelectPrinter(it.id) } },
                            )
                        }
                    }
                    QuickActionsMiuix(
                        actions = DashboardActions(onPrintDocuments, onPrintPhotos, onWebPrint, onScan, onManagePrinters),
                    )
                    RecentJobsMiuix(state)
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
            }
        }
    }
}

@Composable
private fun PrinterStatusCardMiuix(
    state: com.hpprint.app.ui.viewmodel.HomeDashboardUiState,
    onManagePrinters: () -> Unit,
    onRenamePrinter: (String, String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var renameText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Printer?>(null) }
    val printer = state.defaultPrinter
    if (printer == null) {
        Card(
            onClick = onManagePrinters,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            BasicComponent(
                title = stringResource(R.string.hp_no_printer),
                summary = stringResource(R.string.hp_add_printer),
                startAction = {
                    Icon(
                        Icons.Rounded.Warning,
                        stringResource(R.string.hp_no_printer),
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
            )
        }
        return
    }

    val working = printer.status == PrinterStatus.ONLINE ||
        printer.status == PrinterStatus.IDLE ||
        printer.status == PrinterStatus.BUSY
    val iconColor = if (working) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (working) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val textColor = Color(0xFF111111)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = containerColor),
            onClick = onManagePrinters,
            onLongPress = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                renameText = printer.name
                renameTarget = printer
            },
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(128.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 52.dp, y = 28.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(144.dp),
                        imageVector = if (working) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                        tint = iconColor,
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 22.dp, top = 20.dp, end = 118.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = printer.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            text = printer.model,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.72f),
                        )
                    }
                    top.yukonga.miuix.kmp.basic.Text(
                        text = printerStatusLabel(printer.status),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.78f),
                    )
                }
            }
        }
        if (state.recentJobs.isNotEmpty()) {
            val (dbg, dfg) = jobStatusColors(state.recentJobs.first().status)
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = state.recentJobs.first().title,
                    summary = "${stringResource(R.string.hp_print_to)} · ${jobStatusLabel(state.recentJobs.first().status)}",
                    startAction = {
                        Icon(
                            Icons.Rounded.DocumentScanner,
                            state.recentJobs.first().title,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    },
                    endActions = {
                        StatusTag(label = jobStatusLabel(state.recentJobs.first().status), backgroundColor = dbg, contentColor = dfg)
                    },
                )
            }
        }

        RenamePrinterDialogMiuix(
            show = renameTarget != null,
            name = renameText,
            onNameChange = { renameText = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget?.let { onRenamePrinter(it.id, renameText.trim()) }
                renameTarget = null
            },
        )
    }
}

@Composable
private fun RenamePrinterDialogMiuix(
    show: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    top.yukonga.miuix.kmp.overlay.OverlayDialog(
        show = show,
        title = stringResource(R.string.hp_rename_printer),
        onDismissRequest = onDismiss,
        content = {
            top.yukonga.miuix.kmp.basic.TextField(
                value = name,
                onValueChange = onNameChange,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.hp_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.hp_save),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun InkLevelsMiuix(printer: Printer?) {
    if (printer == null) return
    val supplies = printer.supplies
    if (supplies.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(title = stringResource(R.string.hp_ink_level))
        supplies.forEach { supply ->
            BasicComponent(
                title = supplyColorLabel(supply.color),
                summary = "${supply.levelPercent}%",
                startAction = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(14.dp)
                            .background(supplyColorHex(supply.color), CircleShape),
                    )
                },
                endActions = {
                    if (supply.isLow) {
                        StatusTag(
                            label = stringResource(R.string.hp_low_ink),
                            backgroundColor = Color(0xFFFCE8E6),
                            contentColor = Color(0xFFB3261E),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun QuickActionsMiuix(actions: DashboardActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(R.string.hp_print_documents),
            summary = stringResource(R.string.hp_documents_hint),
            startAction = { Icon(Icons.Filled.Description, stringResource(R.string.hp_print_documents), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onPrintDocuments,
        )
        ArrowPreference(
            title = stringResource(R.string.hp_print_photos),
            summary = stringResource(R.string.hp_photos_hint),
            startAction = { Icon(Icons.Filled.Image, stringResource(R.string.hp_print_photos), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onPrintPhotos,
        )
        ArrowPreference(
            title = stringResource(R.string.hp_web_print),
            summary = stringResource(R.string.hp_web_print_hint),
            startAction = { Icon(Icons.Filled.Language, stringResource(R.string.hp_web_print), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onWebPrint,
        )
        ArrowPreference(
            title = stringResource(R.string.hp_start_scan),
            summary = stringResource(R.string.hp_scan_hint),
            startAction = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_start_scan), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onScan,
        )
    }
}

@Composable
private fun RecentJobsMiuix(state: com.hpprint.app.ui.viewmodel.HomeDashboardUiState) {
    if (state.recentJobs.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        state.recentJobs.forEach { job ->
            val (bg, fg) = jobStatusColors(job.status)
            BasicComponent(
                title = job.title,
                summary = "${stringResource(R.string.hp_copies)} ${job.copies} · ${job.paperSize} · ${formatTimestamp(job.createdAt)}",
                endActions = {
                    StatusTag(label = jobStatusLabel(job.status), backgroundColor = bg, contentColor = fg)
                },
            )
        }
    }
}

@Composable
private fun DashboardMaterial(
    state: com.hpprint.app.ui.viewmodel.HomeDashboardUiState,
    bottomInnerPadding: Dp,
    onPrintDocuments: () -> Unit,
    onPrintPhotos: () -> Unit,
    onWebPrint: () -> Unit,
    onScan: () -> Unit,
    onManagePrinters: () -> Unit,
    onSelectPrinter: (String) -> Unit,
    onRenamePrinter: (String, String) -> Unit,
    onRefreshPrinters: () -> Unit,
    isRefreshing: Boolean,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptic = LocalHapticFeedback.current
    var renameText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Printer?>(null) }
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onRefreshPrinters, enabled = !isRefreshing) {
                        Icon(
                            Icons.Rounded.Refresh,
                            stringResource(R.string.hp_refresh),
                            tint = if (isRefreshing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
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
            val printer = state.defaultPrinter
            if (printer == null) {
                TonalCard(onClick = onManagePrinters) {
                    SegmentedListItem(
                        onClick = onManagePrinters,
                        headlineContent = { Text(stringResource(R.string.hp_no_printer)) },
                        supportingContent = { Text(stringResource(R.string.hp_add_printer)) },
                        leadingContent = { Icon(Icons.Rounded.Warning, stringResource(R.string.hp_no_printer)) },
                    )
                }
            } else {
                val (bg, fg) = printerStatusColors(printer.status)
                val working = printer.status == PrinterStatus.ONLINE || printer.status == PrinterStatus.BUSY
                TonalCard(
                    containerColor = bg,
                    contentColor = fg,
                    onClick = onManagePrinters,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        renameText = printer.name
                        renameTarget = printer
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = printer.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            )
                            Text(
                                text = printer.model,
                                style = MaterialTheme.typography.bodySmall,
                                color = fg.copy(alpha = 0.75f),
                            )
                        }
                        AssistChip(
                            onClick = onManagePrinters,
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = fg,
                                leadingIconContentColor = fg,
                            ),
                            label = { Text(printerStatusLabel(printer.status)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (working) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }

            state.defaultPrinter?.takeIf { it.supplies.isNotEmpty() }?.let { printer ->
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_ink_level)) {
                    printer.supplies.forEach { supply ->
                        item {
                            SegmentedListItem(
                                headlineContent = { Text(supplyColorLabel(supply.color)) },
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

            if (state.allPrinters.size > 1) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.Print,
                            title = stringResource(R.string.hp_default_printer),
                            summary = state.defaultPrinter?.name ?: "",
                            items = state.allPrinters.map { it.name },
                            selectedIndex = state.allPrinters.indexOfFirst { it.id == state.defaultPrinter?.id }.coerceAtLeast(0),
                            onItemSelected = { idx -> state.allPrinters.getOrNull(idx)?.let { onSelectPrinter(it.id) } },
                        )
                    }
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onPrintDocuments,
                        headlineContent = { Text(stringResource(R.string.hp_print_documents)) },
                        supportingContent = { Text(stringResource(R.string.hp_documents_hint)) },
                        leadingContent = { Icon(Icons.Filled.Description, stringResource(R.string.hp_print_documents)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onPrintPhotos,
                        headlineContent = { Text(stringResource(R.string.hp_print_photos)) },
                        supportingContent = { Text(stringResource(R.string.hp_photos_hint)) },
                        leadingContent = { Icon(Icons.Filled.Image, stringResource(R.string.hp_print_photos)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onWebPrint,
                        headlineContent = { Text(stringResource(R.string.hp_web_print)) },
                        supportingContent = { Text(stringResource(R.string.hp_web_print_hint)) },
                        leadingContent = { Icon(Icons.Filled.Language, stringResource(R.string.hp_web_print)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onScan,
                        headlineContent = { Text(stringResource(R.string.hp_start_scan)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_hint)) },
                        leadingContent = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_start_scan)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }

            if (state.recentJobs.isNotEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_recent_jobs)) {
                    state.recentJobs.forEach { job ->
                        item {
                            val (bg, fg) = jobStatusColors(job.status)
                            SegmentedListItem(
                                headlineContent = { Text(job.title) },
                                supportingContent = { Text("${stringResource(R.string.hp_copies)} ${job.copies} · ${job.paperSize} · ${formatTimestamp(job.createdAt)}") },
                                trailingContent = { StatusTag(label = jobStatusLabel(job.status), backgroundColor = bg, contentColor = fg) },
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(
                    bottomInnerPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }

        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(stringResource(R.string.hp_rename_printer)) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.hp_printer_name)) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        renameTarget?.let { onRenamePrinter(it.id, renameText.trim()) }
                        renameTarget = null
                    }) { Text(stringResource(R.string.hp_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(stringResource(R.string.hp_cancel))
                    }
                },
            )
        }
    }
}