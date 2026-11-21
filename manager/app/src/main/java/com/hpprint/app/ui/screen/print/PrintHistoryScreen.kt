package com.hpprint.app.ui.screen.print

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.dialog.RenameDialog
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.hp.formatTimestamp
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.PressableIconButton
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.scrollEndVibration
import com.hpprint.app.ui.viewmodel.PrintJobViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GROUP_DATE = 0
private const val GROUP_KIND = 1

private data class JobGroup(val title: String, val jobs: List<PrintJob>)

/** 打印历史二级页：右上角下拉切换“按日期 / 按格式”两种分类方式。 */
@Composable
fun PrintHistoryScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<PrintJobViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val printerName = remember { { id: String -> viewModel.printerName(id) } }

    var groupMode by rememberSaveable { mutableIntStateOf(GROUP_DATE) }
    var renameTarget by remember { mutableStateOf<PrintJob?>(null) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PrintHistoryMiuix(
            history = state.history,
            groupMode = groupMode,
            onGroupMode = { groupMode = it },
            printerName = printerName,
            onRemove = viewModel::remove,
            onRename = { renameTarget = it },
            context = context,
        ) { navigator.pop() }
        UiMode.Material -> PrintHistoryMaterial(
            history = state.history,
            groupMode = groupMode,
            onGroupMode = { groupMode = it },
            printerName = printerName,
            onRemove = viewModel::remove,
            onRename = { renameTarget = it },
            context = context,
        ) { navigator.pop() }
    }

    renameTarget?.let { job ->
        RenameDialog(
            show = true,
            initial = job.title,
            onConfirm = { name ->
                viewModel.rename(job, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun PrintHistoryMiuix(
    history: List<PrintJob>,
    groupMode: Int,
    onGroupMode: (Int) -> Unit,
    printerName: (String) -> String,
    onRemove: (PrintJob) -> Unit,
    onRename: (PrintJob) -> Unit,
    context: Context,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = stringResource(R.string.hp_print_history),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val groups = remember(history, groupMode) { buildGroups(history, groupMode, context) }
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndVibration()
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "group_mode") {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.hp_history_group_by),
                        summary = stringResource(R.string.hp_history_group_summary),
                        items = listOf(
                            stringResource(R.string.hp_history_group_date),
                            stringResource(R.string.hp_history_group_format),
                        ),
                        startAction = {
                            Icon(
                                Icons.AutoMirrored.Rounded.List,
                                contentDescription = stringResource(R.string.hp_history_group_by),
                                modifier = Modifier.padding(end = 6.dp),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        selectedIndex = groupMode,
                        onSelectedIndexChange = onGroupMode,
                    )
                }
            }
            if (groups.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(title = stringResource(R.string.hp_no_jobs))
                    }
                }
            } else {
                groups.forEach { group ->
                    item(key = "header_${group.title}") {
                        Text(
                            text = group.title,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
                        )
                    }
                    item(key = "card_${group.title}") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            group.jobs.forEach { job ->
                                BasicComponent(
                                    title = job.title,
                                    summary = "${printerName(job.printerId)} · ${formatTimestamp(job.createdAt)}",
                                    startAction = {
                                        Icon(
                                            kindIcon(job.kind),
                                            job.title,
                                            modifier = Modifier.padding(end = 12.dp),
                                            tint = MiuixTheme.colorScheme.onBackground,
                                        )
                                    },
                                    endActions = {
                                        PressableIconButton(onClick = { onRename(job) }) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                stringResource(R.string.hp_rename),
                                                modifier = Modifier.size(20.dp),
                                                tint = MiuixTheme.colorScheme.onBackground,
                                            )
                                        }
                                        PressableIconButton(onClick = { onRemove(job) }) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                stringResource(R.string.hp_delete),
                                                modifier = Modifier.size(20.dp),
                                                tint = MiuixTheme.colorScheme.onBackground,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintHistoryMaterial(
    history: List<PrintJob>,
    groupMode: Int,
    onGroupMode: (Int) -> Unit,
    printerName: (String) -> String,
    onRemove: (PrintJob) -> Unit,
    onRename: (PrintJob) -> Unit,
    context: Context,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_print_history)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val groups = remember(history, groupMode) { buildGroups(history, groupMode, context) }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .scrollEndVibration()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                item {
                    SegmentedDropdownItem(
                        icon = Icons.AutoMirrored.Rounded.List,
                        title = stringResource(R.string.hp_history_group_by),
                        summary = stringResource(R.string.hp_history_group_summary),
                        items = listOf(
                            stringResource(R.string.hp_history_group_date),
                            stringResource(R.string.hp_history_group_format),
                        ),
                        selectedIndex = groupMode,
                        onItemSelected = onGroupMode,
                    )
                }
            }
            if (groups.isEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_no_jobs)) },
                        )
                    }
                }
            } else {
                groups.forEach { group ->
                    SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = group.title) {
                        group.jobs.forEach { job ->
                            item(key = job.id) {
                                SegmentedListItem(
                                    headlineContent = { Text(job.title) },
                                    supportingContent = {
                                        Text("${printerName(job.printerId)} · ${formatTimestamp(job.createdAt)}")
                                    },
                                    leadingContent = { Icon(kindIcon(job.kind), job.title) },
                                    trailingContent = {
                                        Row {
                                            PressableIconButton(onClick = { onRename(job) }) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    stringResource(R.string.hp_rename),
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            PressableIconButton(onClick = { onRemove(job) }) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    stringResource(R.string.hp_delete),
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun buildGroups(history: List<PrintJob>, mode: Int, context: Context): List<JobGroup> {
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return when (mode) {
        GROUP_KIND -> history
            .groupBy { it.kind }
            .map { (kind, jobs) -> JobGroup(kindLabel(kind, context), jobs) }
            .sortedByDescending { it.jobs.firstOrNull()?.createdAt ?: 0L }

        else -> history
            .groupBy { dayFormat.format(Date(it.createdAt)) }
            .map { (day, jobs) -> JobGroup(day, jobs) }
    }
}

private fun kindIcon(kind: PrintJobKind): ImageVector = when (kind) {
    PrintJobKind.IMAGE -> Icons.Rounded.Image
    PrintJobKind.PDF -> Icons.Rounded.PictureAsPdf
    PrintJobKind.TEXT -> Icons.AutoMirrored.Rounded.Article
    PrintJobKind.HTML, PrintJobKind.WEB -> Icons.Rounded.Language
    PrintJobKind.XLSX -> Icons.Rounded.TableChart
    PrintJobKind.PPTX -> Icons.Rounded.Slideshow
    PrintJobKind.OFD -> Icons.Rounded.Description
    PrintJobKind.DOCX -> Icons.Rounded.Description
}

private fun kindLabel(kind: PrintJobKind, context: Context): String = when (kind) {
    PrintJobKind.IMAGE -> context.getString(R.string.hp_kind_image)
    PrintJobKind.PDF -> "PDF"
    PrintJobKind.TEXT -> context.getString(R.string.hp_kind_text)
    PrintJobKind.HTML, PrintJobKind.WEB -> context.getString(R.string.hp_kind_web)
    PrintJobKind.DOCX -> "Word"
    PrintJobKind.XLSX -> "Excel"
    PrintJobKind.PPTX -> "PowerPoint"
    PrintJobKind.OFD -> "OFD"
}
