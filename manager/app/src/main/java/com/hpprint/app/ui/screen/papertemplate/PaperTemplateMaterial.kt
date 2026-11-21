package com.hpprint.app.ui.screen.papertemplate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.papertemplate.model.BackgroundPattern
import com.hpprint.app.data.papertemplate.model.Orientation
import com.hpprint.app.data.papertemplate.model.PageSize
import com.hpprint.app.data.papertemplate.model.Palette
import com.hpprint.app.data.papertemplate.model.PaperCategory
import com.hpprint.app.data.papertemplate.model.PaperConfig
import com.hpprint.app.data.papertemplate.model.PaperType
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.SegmentedSwitchItem
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.util.rememberEdgeHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaperTemplateMaterial(
    type: PaperType,
    config: PaperConfig,
    showGallery: Boolean,
    exporting: Boolean,
    bottomInnerPadding: Dp,
    onSelectType: (PaperType) -> Unit,
    onConfigChange: (PaperConfig) -> Unit,
    onOpenGallery: () -> Unit,
    onCloseGallery: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onPreviewClick: () -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
) {
    if (showGallery) {
        PaperTemplateGalleryScreen(onSelect = onSelectType, onBack = onCloseGallery)
        return
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.paper_template_title)) },
                colors = expressiveTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.GridView, stringResource(R.string.paper_template_select))
                    }
                },
                actions = {
                    IconButton(onClick = { if (!exporting) onPrint() }) {
                        Icon(Icons.Filled.Print, stringResource(R.string.paper_template_print))
                    }
                    IconButton(onClick = { if (!exporting) onSave() }) {
                        Icon(Icons.Filled.PictureAsPdf, stringResource(R.string.paper_template_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PaperPreviewCanvas(
                            type = type,
                            config = config,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            onClick = onPreviewClick,
                        )
                    }
                }
            }

            PaperControlPanelMaterial(type, config, onConfigChange, onPickLogo, onClearLogo)

            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun PaperControlPanelMaterial(
    type: PaperType,
    config: PaperConfig,
    onConfigChange: (PaperConfig) -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SegmentedDropdownItem(
                    title = stringResource(R.string.paper_template_page_size),
                    items = PageSize.entries.map { it.label },
                    selectedIndex = PageSize.entries.indexOf(config.pageSize).coerceAtLeast(0),
                    onItemSelected = { i -> onConfigChange(config.copy(pageSize = PageSize.entries[i])) },
                )
            }
            item {
                SegmentedDropdownItem(
                    title = stringResource(R.string.paper_template_orientation),
                    items = Orientation.entries.map { it.label },
                    selectedIndex = Orientation.entries.indexOf(config.orientation).coerceAtLeast(0),
                    onItemSelected = { i -> onConfigChange(config.copy(orientation = Orientation.entries[i])) },
                )
            }
            item {
                SegmentedDropdownItem(
                    title = stringResource(R.string.paper_template_pattern),
                    items = BackgroundPattern.entries.map { it.label },
                    selectedIndex = BackgroundPattern.entries.indexOf(config.pattern).coerceAtLeast(0),
                    onItemSelected = { i -> onConfigChange(config.copy(pattern = BackgroundPattern.entries[i])) },
                )
            }
        }

        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SectionTitleMaterial(stringResource(R.string.paper_template_paper_color))
                ColorSwatchRow(
                    colors = Palette.paperColors,
                    selected = config.paperColor,
                    onSelect = { onConfigChange(config.copy(paperColor = it)) },
                    selectedBorder = MaterialTheme.colorScheme.primary,
                    unselectedBorder = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            item {
                SectionTitleMaterial(stringResource(R.string.paper_template_line_color))
                ColorSwatchRow(
                    colors = Palette.lineColors,
                    selected = config.lineColor,
                    onSelect = { onConfigChange(config.copy(lineColor = it)) },
                    selectedBorder = MaterialTheme.colorScheme.primary,
                    unselectedBorder = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Column {
                    LabeledSliderMaterial(
                        label = spacingLabel(type),
                        value = config.spacingMm,
                        range = spacingRange(type),
                        display = fmt(config.spacingMm),
                        onChange = { onConfigChange(config.copy(spacingMm = it)) },
                    )
                    if (type.usesSecondary) {
                        LabeledSliderMaterial(
                            label = stringResource(R.string.paper_template_secondary),
                            value = config.secondaryMm,
                            range = spacingRange(type),
                            display = fmt(config.secondaryMm),
                            onChange = { onConfigChange(config.copy(secondaryMm = it)) },
                        )
                    }
                    LabeledSliderMaterial(
                        label = stringResource(R.string.paper_template_line_width),
                        value = config.lineWidthMm,
                        range = 0.1f..1.5f,
                        display = fmt(config.lineWidthMm),
                        onChange = { onConfigChange(config.copy(lineWidthMm = it)) },
                    )
                    LabeledSliderMaterial(
                        label = stringResource(R.string.paper_template_margin),
                        value = config.marginMm,
                        range = 0f..30f,
                        display = fmt(config.marginMm, integer = true),
                        onChange = { onConfigChange(config.copy(marginMm = it)) },
                    )
                    LabeledSliderMaterial(
                        label = stringResource(R.string.paper_template_page_count),
                        value = config.pageCount.toFloat(),
                        range = 1f..50f,
                        display = config.pageCount.toString(),
                        onChange = { onConfigChange(config.copy(pageCount = it.toInt())) },
                    )
                }
            }
        }

        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SegmentedSwitchItem(
                    title = stringResource(R.string.paper_template_watermark),
                    checked = config.watermarkEnabled,
                    onCheckedChange = { onConfigChange(config.copy(watermarkEnabled = it)) },
                )
            }
            if (config.watermarkEnabled) {
                item {
                    OutlinedTextField(
                        value = config.watermarkText,
                        onValueChange = { onConfigChange(config.copy(watermarkText = it)) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.print_template_watermark_text)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item {
                SegmentedSwitchItem(
                    title = stringResource(R.string.paper_template_logo),
                    checked = config.logoEnabled,
                    onCheckedChange = { onConfigChange(config.copy(logoEnabled = it)) },
                )
            }
            item {
                OutlinedTextField(
                    value = config.logoText,
                    onValueChange = { onConfigChange(config.copy(logoText = it)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.print_template_logo_text)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                SegmentedListItem(
                    onClick = onPickLogo,
                    headlineContent = { Text(stringResource(R.string.print_template_upload_logo)) },
                )
            }
            if (config.logoBitmap != null) {
                item {
                    SegmentedListItem(
                        onClick = onClearLogo,
                        headlineContent = { Text(stringResource(R.string.print_template_clear_logo)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitleMaterial(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun LabeledSliderMaterial(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    val haptic = rememberEdgeHaptic()
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        haptic()
                        showInput = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
        )
    }
    if (showInput) {
        NumberInputDialog(
            title = label,
            initial = value,
            range = range,
            onDismiss = { showInput = false },
            onConfirm = {
                showInput = false
                onChange(it)
            },
        )
    }
}
