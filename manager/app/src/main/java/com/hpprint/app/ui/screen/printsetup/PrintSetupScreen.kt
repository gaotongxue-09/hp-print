package com.hpprint.app.ui.screen.printsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.SegmentedTextField
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.ExpressiveConfirmDialog
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.miuix.SuperEditArrow
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.viewmodel.COLOR_MODES
import com.hpprint.app.ui.viewmodel.DUPLEX_MODES
import com.hpprint.app.ui.viewmodel.ORIENTATION_OPTIONS
import com.hpprint.app.ui.viewmodel.PAPER_SIZES
import com.hpprint.app.ui.viewmodel.PAGE_RANGE_MODES
import com.hpprint.app.ui.viewmodel.QUALITY_OPTIONS
import com.hpprint.app.ui.viewmodel.PrintSetupViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PrintSetupScreen(
    files: List<PrintFile>,
    webUrl: String,
) {
    val viewModel = viewModel<PrintSetupViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    val titles = files.map { fileTitle(it) }
    val kinds = files.map { runCatching { PrintJobKind.valueOf(it.kind) }.getOrDefault(PrintJobKind.PDF) }
    val sourceUris = files.map { it.sourceUri }

    val onPrint = {
        viewModel.dispatch(
            titles = titles,
            kinds = kinds,
            sourceUris = sourceUris,
            webUrl = webUrl,
        ) {
            navigator.pop()
        }
    }
    val onPreview = { navigator.push(Route.PrintPreview(files, webUrl)) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PrintSetupMiuix(state, files, viewModel, onPrint, onPreview, { navigator.pop() })
        UiMode.Material -> PrintSetupMaterial(state, files, viewModel, onPrint, onPreview, { navigator.pop() })
    }
}

private fun fileTitle(file: PrintFile): String =
    file.title.ifBlank { file.sourceUri.substringAfterLast('/').ifBlank { "Document" } }

@Composable
private fun PrintSetupMiuix(
    state: com.hpprint.app.ui.viewmodel.PrintSetupUiState,
    files: List<PrintFile>,
    viewModel: PrintSetupViewModel,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val colorItems = listOf(stringResource(R.string.hp_color), stringResource(R.string.hp_grayscale))
    val duplexItems = listOf(
        stringResource(R.string.hp_duplex_off),
        stringResource(R.string.hp_duplex_long),
        stringResource(R.string.hp_duplex_short),
        stringResource(R.string.hp_duplex_manual),
    )
    val qualityItems = listOf(
        stringResource(R.string.hp_quality_draft),
        stringResource(R.string.hp_quality_normal),
        stringResource(R.string.hp_quality_high),
    )
    val orientationItems = listOf(
        stringResource(R.string.hp_orientation_portrait),
        stringResource(R.string.hp_orientation_landscape),
    )
    val rangeItems = listOf(
        stringResource(R.string.hp_page_range_all),
        stringResource(R.string.hp_page_range_custom),
    )
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_print_setup),
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
                files.forEach { file ->
                    val name = fileTitle(file)
                    top.yukonga.miuix.kmp.basic.BasicComponent(
                        title = name,
                        summary = stringResource(R.string.hp_source_file),
                        startAction = {
                            Icon(
                                Icons.Rounded.Description,
                                name,
                                modifier = Modifier.padding(end = 12.dp),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                val printers = state.printers.map { it.name }
                if (printers.isNotEmpty()) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.hp_select_printer),
                        summary = state.selectedPrinter?.name ?: "",
                        items = printers,
                        startAction = {
                            Icon(Icons.Filled.Print, stringResource(R.string.hp_select_printer), modifier = Modifier.padding(end = 6.dp))
                        },
                        selectedIndex = state.printers.indexOfFirst { it.id == state.selectedPrinter?.id }.coerceAtLeast(0),
                        onSelectedIndexChange = { idx -> state.printers.getOrNull(idx)?.let { viewModel.selectPrinter(it.id) } },
                    )
                }
                SuperEditArrow(
                    title = stringResource(R.string.hp_copies),
                    defaultValue = state.copies,
                    startAction = { Icon(Icons.Rounded.Description, stringResource(R.string.hp_copies), modifier = Modifier.padding(end = 6.dp)) },
                    onValueChange = { viewModel.setCopies(it) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_color_mode),
                    summary = colorItems.getOrElse(COLOR_MODES.indexOf(state.colorMode), { colorItems[0] }),
                    items = colorItems,
                    selectedIndex = COLOR_MODES.indexOf(state.colorMode).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setColorMode(COLOR_MODES[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_paper_size),
                    summary = state.paperSize,
                    items = PAPER_SIZES,
                    selectedIndex = PAPER_SIZES.indexOf(state.paperSize).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setPaperSize(PAPER_SIZES[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_duplex),
                    summary = duplexItems.getOrElse(DUPLEX_MODES.indexOf(state.duplex), { duplexItems[0] }),
                    items = duplexItems,
                    selectedIndex = DUPLEX_MODES.indexOf(state.duplex).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setDuplex(DUPLEX_MODES[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_quality),
                    summary = qualityItems.getOrElse(QUALITY_OPTIONS.indexOf(state.quality), { qualityItems[1] }),
                    items = qualityItems,
                    selectedIndex = QUALITY_OPTIONS.indexOf(state.quality).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setQuality(QUALITY_OPTIONS[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_orientation),
                    summary = orientationItems.getOrElse(ORIENTATION_OPTIONS.indexOf(state.orientation), { orientationItems[0] }),
                    items = orientationItems,
                    selectedIndex = ORIENTATION_OPTIONS.indexOf(state.orientation).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setOrientation(ORIENTATION_OPTIONS[it]) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_page_range),
                    summary = rangeItems.getOrElse(PAGE_RANGE_MODES.indexOf(state.pageRange), { rangeItems[0] }),
                    items = rangeItems,
                    selectedIndex = PAGE_RANGE_MODES.indexOf(state.pageRange).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setPageRange(PAGE_RANGE_MODES[it]) },
                )
                if (state.pageRange == "custom") {
                    SuperEditArrow(
                        title = stringResource(R.string.hp_page_start),
                        defaultValue = state.pageStart,
                        startAction = { Icon(Icons.Rounded.Description, stringResource(R.string.hp_page_start), modifier = Modifier.padding(end = 6.dp)) },
                        onValueChange = { viewModel.setPageStart(it) },
                    )
                    SuperEditArrow(
                        title = stringResource(R.string.hp_page_end),
                        defaultValue = state.pageEnd,
                        startAction = { Icon(Icons.Rounded.Description, stringResource(R.string.hp_page_end), modifier = Modifier.padding(end = 6.dp)) },
                        onValueChange = { viewModel.setPageEnd(it) },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_preview),
                    summary = stringResource(R.string.hp_preview_hint),
                    startAction = {
                        Icon(Icons.Rounded.Description, stringResource(R.string.hp_preview), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    onClick = onPreview,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_print_button),
                    summary = state.selectedPrinter?.let { "${stringResource(R.string.hp_print_to)} · ${it.name}" } ?: stringResource(R.string.hp_no_printer),
                    startAction = {
                        Icon(Icons.Filled.Print, stringResource(R.string.hp_print_button), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    enabled = state.selectedPrinter != null && !state.isDispatching,
                    onClick = onPrint,
                )
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }

    OverlayDialog(
        show = state.showManualDuplexGuide,
        title = stringResource(R.string.hp_manual_duplex_guide_title),
        onDismissRequest = { viewModel.cancelManualDuplexGuide() },
        content = {
            Column {
                MiuixText(
                    text = stringResource(R.string.hp_manual_duplex_guide_message),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.hp_manual_duplex_cancel),
                        onClick = { viewModel.cancelManualDuplexGuide() },
                    )
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.hp_manual_duplex_guide_start),
                        onClick = { viewModel.confirmManualDuplexGuide() },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun PrintSetupMaterial(
    state: com.hpprint.app.ui.viewmodel.PrintSetupUiState,
    files: List<PrintFile>,
    viewModel: PrintSetupViewModel,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val colorItems = listOf(stringResource(R.string.hp_color), stringResource(R.string.hp_grayscale))
    val duplexItems = listOf(
        stringResource(R.string.hp_duplex_off),
        stringResource(R.string.hp_duplex_long),
        stringResource(R.string.hp_duplex_short),
        stringResource(R.string.hp_duplex_manual),
    )
    val qualityItems = listOf(
        stringResource(R.string.hp_quality_draft),
        stringResource(R.string.hp_quality_normal),
        stringResource(R.string.hp_quality_high),
    )
    val orientationItems = listOf(
        stringResource(R.string.hp_orientation_portrait),
        stringResource(R.string.hp_orientation_landscape),
    )
    val rangeItems = listOf(
        stringResource(R.string.hp_page_range_all),
        stringResource(R.string.hp_page_range_custom),
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_print_setup)) },
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
                files.forEach { file ->
                    val name = fileTitle(file)
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text(stringResource(R.string.hp_source_file)) },
                            leadingContent = { Icon(Icons.Rounded.Description, name) },
                        )
                    }
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    val printerItems = state.printers.map { it.name }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.Print,
                        title = stringResource(R.string.hp_select_printer),
                        summary = state.selectedPrinter?.name ?: "",
                        items = printerItems,
                        selectedIndex = state.printers.indexOfFirst { it.id == state.selectedPrinter?.id }.coerceAtLeast(0),
                        onItemSelected = { idx -> state.printers.getOrNull(idx)?.let { viewModel.selectPrinter(it.id) } },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_copies),
                        summary = state.copies.toString(),
                        items = (1..20).map { it.toString() },
                        selectedIndex = (state.copies - 1).coerceIn(0, 19),
                        onItemSelected = { viewModel.setCopies(it + 1) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_color_mode),
                        summary = colorItems.getOrElse(COLOR_MODES.indexOf(state.colorMode), { colorItems[0] }),
                        items = colorItems,
                        selectedIndex = COLOR_MODES.indexOf(state.colorMode).coerceAtLeast(0),
                        onItemSelected = { viewModel.setColorMode(COLOR_MODES[it]) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_paper_size),
                        summary = state.paperSize,
                        items = PAPER_SIZES,
                        selectedIndex = PAPER_SIZES.indexOf(state.paperSize).coerceAtLeast(0),
                        onItemSelected = { viewModel.setPaperSize(PAPER_SIZES[it]) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_duplex),
                        summary = duplexItems.getOrElse(DUPLEX_MODES.indexOf(state.duplex), { duplexItems[0] }),
                        items = duplexItems,
                        selectedIndex = DUPLEX_MODES.indexOf(state.duplex).coerceAtLeast(0),
                        onItemSelected = { viewModel.setDuplex(DUPLEX_MODES[it]) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_quality),
                        summary = qualityItems.getOrElse(QUALITY_OPTIONS.indexOf(state.quality), { qualityItems[1] }),
                        items = qualityItems,
                        selectedIndex = QUALITY_OPTIONS.indexOf(state.quality).coerceAtLeast(0),
                        onItemSelected = { viewModel.setQuality(QUALITY_OPTIONS[it]) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_orientation),
                        summary = orientationItems.getOrElse(ORIENTATION_OPTIONS.indexOf(state.orientation), { orientationItems[0] }),
                        items = orientationItems,
                        selectedIndex = ORIENTATION_OPTIONS.indexOf(state.orientation).coerceAtLeast(0),
                        onItemSelected = { viewModel.setOrientation(ORIENTATION_OPTIONS[it]) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.hp_page_range),
                        summary = rangeItems.getOrElse(PAGE_RANGE_MODES.indexOf(state.pageRange), { rangeItems[0] }),
                        items = rangeItems,
                        selectedIndex = PAGE_RANGE_MODES.indexOf(state.pageRange).coerceAtLeast(0),
                        onItemSelected = { viewModel.setPageRange(PAGE_RANGE_MODES[it]) },
                    )
                }
                if (state.pageRange == "custom") {
                    item(key = "page_start") {
                        PageRangeNumberField(
                            label = stringResource(R.string.hp_page_start),
                            value = state.pageStart,
                            onValueChange = viewModel::setPageStart,
                            icon = Icons.Rounded.Description,
                        )
                    }
                    item(key = "page_end") {
                        PageRangeNumberField(
                            label = stringResource(R.string.hp_page_end),
                            value = state.pageEnd,
                            onValueChange = viewModel::setPageEnd,
                            icon = Icons.Rounded.Description,
                        )
                    }
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onPreview,
                        headlineContent = { Text(stringResource(R.string.hp_preview)) },
                        supportingContent = { Text(stringResource(R.string.hp_preview_hint)) },
                        leadingContent = { Icon(Icons.Rounded.Description, stringResource(R.string.hp_preview)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }

            Button(
                onClick = onPrint,
                enabled = state.selectedPrinter != null && !state.isDispatching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Print, null)
                Text(
                    text = stringResource(R.string.hp_print_button),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }

    if (state.showManualDuplexGuide) {
        ExpressiveConfirmDialog(
            title = stringResource(R.string.hp_manual_duplex_guide_title),
            confirmText = stringResource(R.string.hp_manual_duplex_guide_start),
            onConfirm = { viewModel.confirmManualDuplexGuide() },
            onDismiss = { viewModel.cancelManualDuplexGuide() },
            message = stringResource(R.string.hp_manual_duplex_guide_message),
            dismissText = stringResource(R.string.hp_manual_duplex_cancel),
        )
    }
}

@Composable
private fun PageRangeNumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    icon: ImageVector,
) {
    var text by remember { mutableStateOf(value.toString()) }
    SegmentedTextField(
        label = label,
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(4)
            text.toIntOrNull()?.let(onValueChange)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        leadingContent = { Icon(icon, null) },
    )
}