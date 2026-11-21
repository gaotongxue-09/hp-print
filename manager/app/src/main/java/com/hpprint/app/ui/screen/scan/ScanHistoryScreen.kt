package com.hpprint.app.ui.screen.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.Scan
import com.hpprint.app.data.scan.core.Doc
import com.hpprint.app.data.scan.core.DocStore
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
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.PressableIconButton
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.scrollEndVibration
import com.hpprint.app.ui.viewmodel.ScanViewModel
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

private const val FILTER_ALL = 0
private const val FILTER_DOC = 1
private const val FILTER_IMAGE = 2
private const val FILTER_IDCARD = 3

private data class HistoryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val time: Long,
    val kind: Int,
    val doc: Doc? = null,
    val scan: Scan? = null,
)

private data class EntryGroup(val title: String, val entries: List<HistoryEntry>)

/**
 * 扫描历史二级页：独立路由，页面结构与打印历史一致
 * （大标题顶栏、顶栏下方筛选行、按日期分组卡片、无底栏）。
 */
@Composable
fun ScanHistoryScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = viewModel<ScanViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val docStore = remember { DocStore.get(context) }

    var version by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }
    val docs = remember(version) { docStore.docs().toList() }
    var filter by remember { mutableIntStateOf(FILTER_ALL) }
    var renameTarget by remember { mutableStateOf<HistoryEntry?>(null) }

    val allEntries = remember(docs, state.scans) {
        val list = mutableListOf<HistoryEntry>()
        docs.forEach { doc ->
            list.add(
                HistoryEntry(
                    key = doc.id,
                    title = doc.name,
                    subtitle = context.getString(R.string.hp_scan_doc_pages, doc.pages.size),
                    time = doc.created,
                    kind = if (doc.type == Doc.TYPE_IDCARD) FILTER_IDCARD else FILTER_DOC,
                    doc = doc,
                )
            )
        }
        state.scans.forEach { scan ->
            list.add(
                HistoryEntry(
                    key = scan.id,
                    title = scan.title,
                    subtitle = formatTimestamp(scan.createdAt),
                    time = scan.createdAt,
                    kind = FILTER_IMAGE,
                    scan = scan,
                )
            )
        }
        list.sortedByDescending { it.time }
    }

    val onOpen: (HistoryEntry) -> Unit = { entry ->
        val doc = entry.doc
        if (doc != null) {
            navigator.push(Route.ScanDoc(doc.id))
        } else {
            entry.scan?.let { navigator.push(Route.ScanPreview(it.imageUri, autoRectify = false)) }
        }
    }
    val onDelete: (HistoryEntry) -> Unit = { entry ->
        val doc = entry.doc
        if (doc != null) {
            docStore.deleteDoc(doc)
            version++
        } else {
            entry.scan?.let { viewModel.delete(it) }
        }
    }
    val onFilterChange: (Int) -> Unit = { filter = it }
    val onBack: () -> Unit = { navigator.pop() }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanHistoryMiuix(
            allEntries = allEntries,
            filter = filter,
            onFilterChange = onFilterChange,
            onOpen = onOpen,
            onDelete = onDelete,
            onRename = { renameTarget = it },
            onBack = onBack,
        )

        UiMode.Material -> ScanHistoryMaterial(
            allEntries = allEntries,
            filter = filter,
            onFilterChange = onFilterChange,
            onOpen = onOpen,
            onDelete = onDelete,
            onRename = { renameTarget = it },
            onBack = onBack,
        )
    }

    val target = renameTarget
    if (target != null) {
        RenameDialog(
            show = true,
            initial = target.title,
            onConfirm = { name ->
                val doc = target.doc
                if (doc != null) {
                    docStore.renameDoc(doc, name)
                    version++
                } else {
                    target.scan?.let { viewModel.rename(it, name) }
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun ScanHistoryFilterPreference(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            title = stringResource(R.string.hp_scan_history_filter_by),
            summary = stringResource(R.string.hp_scan_history_filter_summary),
            items = filterLabels(),
            startAction = {
                Icon(
                    Icons.AutoMirrored.Rounded.List,
                    contentDescription = stringResource(R.string.hp_scan_history_filter_by),
                    modifier = Modifier.padding(end = 6.dp),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            },
            selectedIndex = selected,
            onSelectedIndexChange = onSelect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanHistoryMiuix(
    allEntries: List<HistoryEntry>,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onRename: (HistoryEntry) -> Unit,
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
                    title = stringResource(R.string.hp_scan_history),
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
        AnimatedContent(
            targetState = filter,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { historyFilterTransition(initialState, targetState) },
            label = "scanHistoryFilterMiuix",
        ) { activeFilter ->
            val entries = remember(allEntries, activeFilter) {
                filterEntries(allEntries, activeFilter)
            }
            val groups = remember(entries) { groupByDay(entries) }
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
                item(key = "filter") {
                    ScanHistoryFilterPreference(
                        selected = filter,
                        onSelect = onFilterChange,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(
                                title = stringResource(R.string.hp_no_scans),
                                summary = stringResource(R.string.hp_scan_now),
                            )
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
                                group.entries.forEach { entry ->
                                    BasicComponent(
                                        title = entry.title,
                                        summary = entry.subtitle,
                                        onClick = { onOpen(entry) },
                                        startAction = {
                                            Icon(
                                                historyIcon(entry.kind),
                                                entry.title,
                                                modifier = Modifier.padding(end = 12.dp),
                                                tint = MiuixTheme.colorScheme.onBackground,
                                            )
                                        },
                                        endActions = {
                                            PressableIconButton(onClick = { onRename(entry) }) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    stringResource(R.string.hp_rename),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MiuixTheme.colorScheme.onBackground,
                                                )
                                            }
                                            PressableIconButton(onClick = { onDelete(entry) }) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanHistoryMaterial(
    allEntries: List<HistoryEntry>,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onRename: (HistoryEntry) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_scan_history)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { innerPadding ->
        AnimatedContent(
            targetState = filter,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { historyFilterTransition(initialState, targetState) },
            label = "scanHistoryFilterMaterial",
        ) { activeFilter ->
            val entries = remember(allEntries, activeFilter) {
                filterEntries(allEntries, activeFilter)
            }
            val groups = remember(entries) { groupByDay(entries) }
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
                            title = stringResource(R.string.hp_scan_history_filter_by),
                            summary = stringResource(R.string.hp_scan_history_filter_summary),
                            items = filterLabels(),
                            selectedIndex = filter,
                            onItemSelected = onFilterChange,
                        )
                    }
                }
                if (groups.isEmpty()) {
                    SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.hp_no_scans)) },
                                supportingContent = { Text(stringResource(R.string.hp_scan_now)) },
                            )
                        }
                    }
                } else {
                    groups.forEach { group ->
                        SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = group.title) {
                            group.entries.forEach { entry ->
                                item(key = entry.key) {
                                    SegmentedListItem(
                                        onClick = { onOpen(entry) },
                                        headlineContent = { Text(entry.title) },
                                        supportingContent = { Text(entry.subtitle) },
                                        leadingContent = { Icon(historyIcon(entry.kind), entry.title) },
                                        trailingContent = {
                                            Row {
                                                PressableIconButton(onClick = { onRename(entry) }) {
                                                    Icon(
                                                        Icons.Rounded.Edit,
                                                        stringResource(R.string.hp_rename),
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                PressableIconButton(onClick = { onDelete(entry) }) {
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
}

@Composable
private fun filterLabels(): List<String> = listOf(
    stringResource(R.string.hp_scan_history_all),
    stringResource(R.string.hp_scan_multi_title),
    stringResource(R.string.hp_scan_history_images),
    stringResource(R.string.hp_scan_history_idcard),
)

private fun filterEntries(entries: List<HistoryEntry>, filter: Int): List<HistoryEntry> =
    if (filter == FILTER_ALL) entries else entries.filter { it.kind == filter }

private fun groupByDay(entries: List<HistoryEntry>): List<EntryGroup> {
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return entries
        .groupBy { dayFormat.format(Date(it.time)) }
        .map { (day, list) -> EntryGroup(day, list) }
}

private fun historyIcon(kind: Int): ImageVector = when (kind) {
    FILTER_DOC -> Icons.Rounded.Folder
    FILTER_IMAGE -> Icons.Rounded.Image
    FILTER_IDCARD -> Icons.Rounded.Badge
    else -> Icons.AutoMirrored.Rounded.List
}

/** 筛选切换：按索引方向做水平位移 + 淡入淡出。 */
private fun historyFilterTransition(initial: Int, target: Int): ContentTransform {
    val forward = target >= initial
    return (
        slideInHorizontally(animationSpec = tween(240)) { if (forward) it / 5 else -it / 5 } +
            fadeIn(tween(200))
        ) togetherWith (
        slideOutHorizontally(animationSpec = tween(200)) { if (forward) -it / 6 else it / 6 } +
            fadeOut(tween(160))
        )
}
