package com.hpprint.app.ui.screen.print

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.statustag.StatusTag
import com.hpprint.app.ui.hp.describeFile
import com.hpprint.app.ui.hp.HP_PRINT_DOC_MIME_TYPES
import com.hpprint.app.ui.hp.formatTimestamp
import com.hpprint.app.ui.hp.jobStatusColors
import com.hpprint.app.ui.hp.jobStatusLabel
import com.hpprint.app.ui.navigation3.Navigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.viewmodel.PrintJobViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.hpprint.app.ui.util.scrollEndVibration
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<PrintJobViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    val actions = PrintActions(
        onPrintDocuments = {
            docPicker.launch(HP_PRINT_DOC_MIME_TYPES)
        },
        onPrintPhotos = { photoPicker.launch(arrayOf("image/*")) },
        onWebPrint = { navigator.push(Route.WebPrint) },
        onCancel = viewModel::cancel,
        onRemove = viewModel::remove,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> PrintMiuix(state, viewModel::printerName, actions, bottomInnerPadding)
        UiMode.Material -> PrintMaterial(state, viewModel::printerName, actions, bottomInnerPadding)
    }
}

private data class PrintActions(
    val onPrintDocuments: () -> Unit,
    val onPrintPhotos: () -> Unit,
    val onWebPrint: () -> Unit,
    val onCancel: (PrintJob) -> Unit,
    val onRemove: (PrintJob) -> Unit,
)

@Composable
private fun PrintMiuix(
    state: com.hpprint.app.ui.viewmodel.PrintJobUiState,
    printerName: (String) -> String,
    actions: PrintActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.hp_print_jobs), scrollBehavior = scrollBehavior) },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
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
                    NewPrintCardMiuix(actions)
                    JobSectionMiuix(
                        title = stringResource(R.string.hp_active_jobs),
                        jobs = state.active,
                        printerName = printerName,
                        showCancel = true,
                        onAction = actions.onCancel,
                    )
                    JobSectionMiuix(
                        title = stringResource(R.string.hp_print_history),
                        jobs = state.history,
                        printerName = printerName,
                        showCancel = false,
                        onAction = actions.onRemove,
                    )
                    if (state.active.isEmpty() && state.history.isEmpty()) {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.hp_no_jobs),
                                summary = stringResource(R.string.hp_new_print),
                                startAction = {
                                    Icon(Icons.Rounded.Image, stringResource(R.string.hp_no_jobs), modifier = Modifier.padding(end = 12.dp))
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun NewPrintCardMiuix(actions: PrintActions) {
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
            startAction = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_print_photos), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onPrintPhotos,
        )
        ArrowPreference(
            title = stringResource(R.string.hp_web_print),
            summary = stringResource(R.string.hp_web_print_hint),
            startAction = { Icon(Icons.Filled.Language, stringResource(R.string.hp_web_print), modifier = Modifier.padding(end = 12.dp)) },
            onClick = actions.onWebPrint,
        )
    }
}

@Composable
private fun JobSectionMiuix(
    title: String,
    jobs: List<PrintJob>,
    printerName: (String) -> String,
    showCancel: Boolean,
    onAction: (PrintJob) -> Unit,
) {
    if (jobs.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        jobs.forEach { job ->
            val (bg, fg) = jobStatusColors(job.status)
            BasicComponent(
                title = job.title,
                summary = "${printerName(job.printerId)} · ${formatTimestamp(job.createdAt)}",
                startAction = {
                    Icon(
                        Icons.Filled.Description,
                        job.title,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                endActions = {
                    StatusTag(label = jobStatusLabel(job.status), backgroundColor = bg, contentColor = fg)
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { onAction(job) }) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            if (showCancel) Icons.Rounded.Close else Icons.Rounded.Delete,
                            title,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PrintMaterial(
    state: com.hpprint.app.ui.viewmodel.PrintJobUiState,
    printerName: (String) -> String,
    actions: PrintActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_print_jobs)) },
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
                        onClick = actions.onPrintDocuments,
                        headlineContent = { Text(stringResource(R.string.hp_print_documents)) },
                        supportingContent = { Text(stringResource(R.string.hp_documents_hint)) },
                        leadingContent = { Icon(Icons.Filled.Description, stringResource(R.string.hp_print_documents)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = actions.onPrintPhotos,
                        headlineContent = { Text(stringResource(R.string.hp_print_photos)) },
                        supportingContent = { Text(stringResource(R.string.hp_photos_hint)) },
                        leadingContent = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_print_photos)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = actions.onWebPrint,
                        headlineContent = { Text(stringResource(R.string.hp_web_print)) },
                        supportingContent = { Text(stringResource(R.string.hp_web_print_hint)) },
                        leadingContent = { Icon(Icons.Filled.Language, stringResource(R.string.hp_web_print)) },
                    )
                }
            }

            JobSectionMaterial(
                title = stringResource(R.string.hp_active_jobs),
                jobs = state.active,
                printerName = printerName,
                showCancel = true,
                onAction = actions.onCancel,
            )
            JobSectionMaterial(
                title = stringResource(R.string.hp_print_history),
                jobs = state.history,
                printerName = printerName,
                showCancel = false,
                onAction = actions.onRemove,
            )

            if (state.active.isEmpty() && state.history.isEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_print_jobs)) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_no_jobs)) },
                            leadingContent = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_no_jobs)) },
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(
                    bottomInnerPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

@Composable
private fun JobSectionMaterial(
    title: String,
    jobs: List<PrintJob>,
    printerName: (String) -> String,
    showCancel: Boolean,
    onAction: (PrintJob) -> Unit,
) {
    if (jobs.isEmpty()) return
    SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = title) {
        jobs.forEach { job ->
            item {
                val (bg, fg) = jobStatusColors(job.status)
                SegmentedListItem(
                    headlineContent = { Text(job.title) },
                    supportingContent = { Text("${printerName(job.printerId)} · ${formatTimestamp(job.createdAt)}") },
                    leadingContent = { Icon(Icons.Filled.Description, job.title) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusTag(label = jobStatusLabel(job.status), backgroundColor = bg, contentColor = fg)
                            IconButton(onClick = { onAction(job) }) {
                                Icon(
                                    if (showCancel) Icons.Rounded.Close else Icons.Rounded.Delete,
                                    title,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}