package com.hpprint.app.ui.screen.scansettings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpprint.app.R
import com.hpprint.app.data.ipp.ScanSettings
import com.hpprint.app.data.ipp.ScanSettingsStore
import com.hpprint.app.data.scan.core.ScanOutputFormat
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SCAN_RESOLUTIONS = listOf(150, 200, 300, 600)
private val SCAN_COLOR_VALUES = listOf("RGB24", "Grayscale8", "BlackAndWhite1")
private val SCAN_FORMATS = listOf(ScanOutputFormat.JPEG, ScanOutputFormat.PDF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSettingsScreen() {
    val navigator = LocalNavigator.current
    var settings by remember {
        val loaded = ScanSettingsStore.load()
        val normalized = if (loaded.format in SCAN_FORMATS) {
            loaded
        } else {
            loaded.copy(format = ScanOutputFormat.JPEG).also { ScanSettingsStore.save(it) }
        }
        mutableStateOf(normalized)
    }

    fun commit(s: ScanSettings) {
        settings = s
        ScanSettingsStore.save(s)
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanSettingsMiuix(settings, ::commit) { navigator.pop() }
        UiMode.Material -> ScanSettingsMaterial(settings, ::commit) { navigator.pop() }
    }
}

@Composable
private fun ScanSettingsMiuix(
    settings: ScanSettings,
    commit: (ScanSettings) -> Unit,
    onBack: () -> Unit,
) {
    val colorItems = listOf(
        stringResource(R.string.hp_scan_color_rgb),
        stringResource(R.string.hp_scan_color_gray),
        stringResource(R.string.hp_scan_color_bw),
    )
    val duplexItems = listOf(
        stringResource(R.string.hp_scan_duplex_off),
        stringResource(R.string.hp_scan_duplex_on),
    )
    val formatItems = listOf("JPEG", "PDF")
    val resItems = SCAN_RESOLUTIONS.map { "$it dpi" }

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.hp_scan_settings),
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_scan_resolution),
                    summary = "${settings.dpi} dpi",
                    items = resItems,
                    startAction = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_resolution), modifier = Modifier.padding(end = 6.dp)) },
                    selectedIndex = SCAN_RESOLUTIONS.indexOf(settings.dpi).coerceAtLeast(0),
                    onSelectedIndexChange = { commit(settings.copy(dpi = SCAN_RESOLUTIONS[it])) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_scan_color_mode),
                    summary = colorItems.getOrElse(SCAN_COLOR_VALUES.indexOf(settings.colorMode), { colorItems[0] }),
                    items = colorItems,
                    selectedIndex = SCAN_COLOR_VALUES.indexOf(settings.colorMode).coerceAtLeast(0),
                    onSelectedIndexChange = { commit(settings.copy(colorMode = SCAN_COLOR_VALUES[it])) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_scan_duplex),
                    summary = if (settings.duplex) stringResource(R.string.hp_scan_duplex_on) else stringResource(R.string.hp_scan_duplex_off),
                    items = duplexItems,
                    selectedIndex = if (settings.duplex) 1 else 0,
                    onSelectedIndexChange = { commit(settings.copy(duplex = it == 1)) },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_scan_format),
                    summary = formatItems.getOrElse(SCAN_FORMATS.indexOf(settings.format), { formatItems[0] }),
                    items = formatItems,
                    selectedIndex = SCAN_FORMATS.indexOf(settings.format).coerceAtLeast(0),
                    onSelectedIndexChange = { commit(settings.copy(format = SCAN_FORMATS[it])) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSettingsMaterial(
    settings: ScanSettings,
    commit: (ScanSettings) -> Unit,
    onBack: () -> Unit,
) {
    val colorItems = listOf(
        stringResource(R.string.hp_scan_color_rgb),
        stringResource(R.string.hp_scan_color_gray),
        stringResource(R.string.hp_scan_color_bw),
    )
    val duplexItems = listOf(
        stringResource(R.string.hp_scan_duplex_off),
        stringResource(R.string.hp_scan_duplex_on),
    )
    val formatItems = listOf("JPEG", "PDF")
    val resItems = SCAN_RESOLUTIONS.map { "$it dpi" }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_scan_settings)) },
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
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.DocumentScanner,
                        title = stringResource(R.string.hp_scan_resolution),
                        summary = "${settings.dpi} dpi",
                        items = resItems,
                        selectedIndex = SCAN_RESOLUTIONS.indexOf(settings.dpi).coerceAtLeast(0),
                        onItemSelected = { commit(settings.copy(dpi = SCAN_RESOLUTIONS[it])) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.DocumentScanner,
                        title = stringResource(R.string.hp_scan_color_mode),
                        summary = colorItems.getOrElse(SCAN_COLOR_VALUES.indexOf(settings.colorMode), { colorItems[0] }),
                        items = colorItems,
                        selectedIndex = SCAN_COLOR_VALUES.indexOf(settings.colorMode).coerceAtLeast(0),
                        onItemSelected = { commit(settings.copy(colorMode = SCAN_COLOR_VALUES[it])) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.DocumentScanner,
                        title = stringResource(R.string.hp_scan_duplex),
                        summary = if (settings.duplex) stringResource(R.string.hp_scan_duplex_on) else stringResource(R.string.hp_scan_duplex_off),
                        items = duplexItems,
                        selectedIndex = if (settings.duplex) 1 else 0,
                        onItemSelected = { commit(settings.copy(duplex = it == 1)) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.DocumentScanner,
                        title = stringResource(R.string.hp_scan_format),
                        summary = formatItems.getOrElse(SCAN_FORMATS.indexOf(settings.format), { formatItems[0] }),
                        items = formatItems,
                        selectedIndex = SCAN_FORMATS.indexOf(settings.format).coerceAtLeast(0),
                        onItemSelected = { commit(settings.copy(format = SCAN_FORMATS[it])) },
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