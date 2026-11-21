package com.hpprint.app.ui.screen.papertemplate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.rememberEdgeHaptic
import com.hpprint.app.ui.util.scrollEndVibration
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun PaperTemplateMiuix(
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
                    title = stringResource(R.string.paper_template_title),
                    navigationIcon = {
                        IconButton(onClick = onOpenGallery) {
                            Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.paper_template_select))
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (!exporting) onPrint() }) {
                            Icon(Icons.Rounded.Print, contentDescription = stringResource(R.string.paper_template_print))
                        }
                        IconButton(onClick = { if (!exporting) onSave() }) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = stringResource(R.string.paper_template_save))
                        }
                    },
                    scrollBehavior = scrollBehavior,
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
                    .scrollEndVibration()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Text(
                        text = "${type.category.label} · ${type.label}",
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PaperPreviewCanvas(
                                type = type,
                                config = config,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                borderColor = MiuixTheme.colorScheme.outline,
                                onClick = onPreviewClick,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
                item { PaperControlPanelMiuix(type, config, onConfigChange, onPickLogo, onClearLogo) }
                item { Spacer(Modifier.height(bottomInnerPadding)) }
            }
        }
    }
}

@Composable
private fun PaperControlPanelMiuix(
    type: PaperType,
    config: PaperConfig,
    onConfigChange: (PaperConfig) -> Unit,
    onPickLogo: () -> Unit,
    onClearLogo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.paper_template_page_size),
                items = PageSize.entries.map { it.label },
                selectedIndex = PageSize.entries.indexOf(config.pageSize).coerceAtLeast(0),
                onSelectedIndexChange = { i -> onConfigChange(config.copy(pageSize = PageSize.entries[i])) },
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.paper_template_orientation),
                items = Orientation.entries.map { it.label },
                selectedIndex = Orientation.entries.indexOf(config.orientation).coerceAtLeast(0),
                onSelectedIndexChange = { i -> onConfigChange(config.copy(orientation = Orientation.entries[i])) },
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.paper_template_pattern),
                items = BackgroundPattern.entries.map { it.label },
                selectedIndex = BackgroundPattern.entries.indexOf(config.pattern).coerceAtLeast(0),
                onSelectedIndexChange = { i -> onConfigChange(config.copy(pattern = BackgroundPattern.entries[i])) },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SectionTitleMiuix(stringResource(R.string.paper_template_paper_color))
            ColorSwatchRow(
                colors = Palette.paperColors,
                selected = config.paperColor,
                onSelect = { onConfigChange(config.copy(paperColor = it)) },
                selectedBorder = MiuixTheme.colorScheme.primary,
                unselectedBorder = MiuixTheme.colorScheme.outline,
            )
            SectionTitleMiuix(stringResource(R.string.paper_template_line_color))
            ColorSwatchRow(
                colors = Palette.lineColors,
                selected = config.lineColor,
                onSelect = { onConfigChange(config.copy(lineColor = it)) },
                selectedBorder = MiuixTheme.colorScheme.primary,
                unselectedBorder = MiuixTheme.colorScheme.outline,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            LabeledSliderMiuix(
                label = spacingLabel(type),
                value = config.spacingMm,
                range = spacingRange(type),
                display = fmt(config.spacingMm),
                onChange = { onConfigChange(config.copy(spacingMm = it)) },
            )
            if (type.usesSecondary) {
                LabeledSliderMiuix(
                    label = stringResource(R.string.paper_template_secondary),
                    value = config.secondaryMm,
                    range = spacingRange(type),
                    display = fmt(config.secondaryMm),
                    onChange = { onConfigChange(config.copy(secondaryMm = it)) },
                )
            }
            LabeledSliderMiuix(
                label = stringResource(R.string.paper_template_line_width),
                value = config.lineWidthMm,
                range = 0.1f..1.5f,
                display = fmt(config.lineWidthMm),
                onChange = { onConfigChange(config.copy(lineWidthMm = it)) },
            )
            LabeledSliderMiuix(
                label = stringResource(R.string.paper_template_margin),
                value = config.marginMm,
                range = 0f..30f,
                display = fmt(config.marginMm, integer = true),
                onChange = { onConfigChange(config.copy(marginMm = it)) },
            )
            LabeledSliderMiuix(
                label = stringResource(R.string.paper_template_page_count),
                value = config.pageCount.toFloat(),
                range = 1f..50f,
                display = config.pageCount.toString(),
                onChange = { onConfigChange(config.copy(pageCount = it.toInt())) },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.paper_template_watermark),
                checked = config.watermarkEnabled,
                onCheckedChange = { onConfigChange(config.copy(watermarkEnabled = it)) },
            )
            if (config.watermarkEnabled) {
                SectionTitleMiuix(stringResource(R.string.print_template_watermark_text))
                TextField(
                    value = config.watermarkText,
                    onValueChange = { onConfigChange(config.copy(watermarkText = it)) },
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SwitchPreference(
                title = stringResource(R.string.paper_template_logo),
                checked = config.logoEnabled,
                onCheckedChange = { onConfigChange(config.copy(logoEnabled = it)) },
            )
            SectionTitleMiuix(stringResource(R.string.print_template_logo_text))
            TextField(
                value = config.logoText,
                onValueChange = { onConfigChange(config.copy(logoText = it)) },
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ArrowPreference(
                title = stringResource(R.string.print_template_upload_logo),
                onClick = onPickLogo,
            )
            if (config.logoBitmap != null) {
                ArrowPreference(
                    title = stringResource(R.string.print_template_clear_logo),
                    onClick = onClearLogo,
                )
            }
        }
    }
}

@Composable
private fun SectionTitleMiuix(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun LabeledSliderMiuix(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    val haptic = rememberEdgeHaptic()
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = display,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
