package com.hpprint.app.ui.screen.scan

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hpprint.app.data.ipp.PrinterScan
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.data.model.hp.Scan
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.hp.formatTimestamp
import com.hpprint.app.ui.navigation3.Navigator
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.viewmodel.ScanViewModel
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
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<ScanViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scanFromPrinter: () -> Unit = {
        val printer = PrinterRepositoryImpl.getInstance().default()
        if (printer == null) {
            Toast.makeText(context, context.getString(R.string.hp_no_printer), Toast.LENGTH_SHORT).show()
        } else {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.hp_scanning), Toast.LENGTH_SHORT).show()
                val bytes = PrinterScan.scan(printer.ip)
                if (bytes != null) {
                    saveScanBytes(context, bytes)?.let { navigator.push(Route.ScanPreview(it)) }
                } else {
                    Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            saveBitmapToCache(context, bitmap)?.let { navigator.push(Route.ScanPreview(it)) }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.push(Route.ScanPreview(uri.toString()))
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanMiuix(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onCamera = { cameraLauncher.launch(null) },
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScanFromPrinter = scanFromPrinter,
            onDelete = viewModel::delete,
            onOpen = { navigator.push(Route.ScanPreview(it.imageUri)) },
        )
        UiMode.Material -> ScanMaterial(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onCamera = { cameraLauncher.launch(null) },
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScanFromPrinter = scanFromPrinter,
            onDelete = viewModel::delete,
            onOpen = { navigator.push(Route.ScanPreview(it.imageUri)) },
        )
    }
}

private fun saveBitmapToCache(context: android.content.Context, bitmap: Bitmap): String? {
    val dir = File(context.cacheDir, "scans")
    dir.mkdirs()
    val f = File(dir, "scan-${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        f.absolutePath
    }.getOrNull()
}

private fun saveScanBytes(context: android.content.Context, bytes: ByteArray): String? {
    val dir = File(context.cacheDir, "scans")
    dir.mkdirs()
    val f = File(dir, "scan-${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileOutputStream(f).use { it.write(bytes) }
        f.absolutePath
    }.getOrNull()
}

@Composable
private fun ScanMiuix(
    state: com.hpprint.app.ui.viewmodel.ScanUiState,
    bottomInnerPadding: Dp,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onScanFromPrinter: () -> Unit,
    onDelete: (Scan) -> Unit,
    onOpen: (Scan) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = stringResource(R.string.hp_scan_title), scrollBehavior = scrollBehavior) },
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_camera),
                            summary = stringResource(R.string.hp_scan_hint),
                            startAction = { Icon(Icons.Rounded.CameraAlt, stringResource(R.string.hp_scan_camera), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onCamera,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_from_gallery),
                            summary = stringResource(R.string.hp_photos_hint),
                            startAction = { Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.hp_scan_from_gallery), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onGallery,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_from_printer),
                            summary = stringResource(R.string.hp_scan_from_printer_hint),
                            startAction = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_from_printer), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onScanFromPrinter,
                        )
                    }

                    if (state.scans.isEmpty()) {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.hp_no_scans),
                                summary = stringResource(R.string.hp_scan_now),
                                startAction = {
                                    Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_no_scans), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                                },
                            )
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            state.scans.forEach { scan ->
                                BasicComponent(
                                    title = scan.title,
                                    summary = formatTimestamp(scan.createdAt),
                                    onClick = { onOpen(scan) },
                                    startAction = {
                                        Icon(Icons.Rounded.DocumentScanner, scan.title, modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                                    },
                                    endActions = {
                                        top.yukonga.miuix.kmp.basic.IconButton(onClick = { onDelete(scan) }) {
                                            top.yukonga.miuix.kmp.basic.Icon(
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
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun ScanMaterial(
    state: com.hpprint.app.ui.viewmodel.ScanUiState,
    bottomInnerPadding: Dp,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onScanFromPrinter: () -> Unit,
    onDelete: (Scan) -> Unit,
    onOpen: (Scan) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_scan_title)) },
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
                        onClick = onCamera,
                        headlineContent = { Text(stringResource(R.string.hp_scan_camera)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_hint)) },
                        leadingContent = { Icon(Icons.Rounded.CameraAlt, stringResource(R.string.hp_scan_camera)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onGallery,
                        headlineContent = { Text(stringResource(R.string.hp_scan_from_gallery)) },
                        supportingContent = { Text(stringResource(R.string.hp_photos_hint)) },
                        leadingContent = { Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.hp_scan_from_gallery)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onScanFromPrinter,
                        headlineContent = { Text(stringResource(R.string.hp_scan_from_printer)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_from_printer_hint)) },
                        leadingContent = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_from_printer)) },
                    )
                }
            }

            if (state.scans.isEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_scan_history)) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_no_scans)) },
                            leadingContent = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_no_scans)) },
                        )
                    }
                }
            } else {
                SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_scan_history)) {
                    state.scans.forEach { scan ->
                        item {
                            SegmentedListItem(
                                onClick = { onOpen(scan) },
                                headlineContent = { Text(scan.title) },
                                supportingContent = { Text(formatTimestamp(scan.createdAt)) },
                                leadingContent = { Icon(Icons.Rounded.DocumentScanner, scan.title) },
                                trailingContent = {
                                    IconButton(onClick = { onDelete(scan) }) {
                                        Icon(Icons.Rounded.Delete, stringResource(R.string.hp_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
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
    }
}