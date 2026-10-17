package com.hpprint.app.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.SendLogBottomSheet
import com.hpprint.app.ui.component.material.SnackBarHost
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors

@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    var showBottomSheet by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = {
            SnackBarHost(
                hostState = snackBarHost,
                modifier = Modifier.padding(bottom = bottomInnerPadding)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenPrinters,
                            headlineContent = { Text(stringResource(id = R.string.hp_printer_management)) },
                            supportingContent = { Text(stringResource(id = R.string.hp_add_printer)) },
                            leadingContent = { Icon(Icons.Filled.Print, stringResource(id = R.string.hp_printer_management)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                )
            )

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = listOf(
                    {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.DisplaySettings,
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onItemSelected = actions.onSetUiModeIndex
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenTheme,
                            headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                            leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    }
                )
            )

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = { showBottomSheet = true },
                            headlineContent = { Text(stringResource(id = R.string.send_log)) },
                            leadingContent = { Icon(Icons.Filled.BugReport, stringResource(id = R.string.send_log)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAbout,
                            headlineContent = { Text(stringResource(id = R.string.about)) },
                            supportingContent = { Text(stringResource(id = R.string.hp_about_summary)) },
                            leadingContent = { Icon(Icons.Filled.Info, stringResource(id = R.string.about)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }

    if (showBottomSheet) {
        SendLogBottomSheet(
            onDismiss = { showBottomSheet = false },
            snackbarHostState = snackBarHost,
        )
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}