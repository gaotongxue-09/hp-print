package com.hpprint.app.ui.screen.scan

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hpprint.app.data.ipp.PrinterScan
import com.hpprint.app.data.ipp.ScanSettingsStore
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
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.scan.core.ScanOutputFormat
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.Navigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hpprint.app.ui.util.scrollEndVibration
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.layerBackdrop
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scanFromPrinter: () -> Unit = {
        val printer = PrinterRepositoryImpl.getInstance().default()
        if (printer == null) {
            Toast.makeText(context, context.getString(R.string.hp_no_printer), Toast.LENGTH_SHORT).show()
        } else {
            coroutineScope.launch {
                Toast.makeText(context, context.getString(R.string.hp_scanning), Toast.LENGTH_SHORT).show()
                val settings = ScanSettingsStore.load()
                val bytes = PrinterScan.scan(printer.ip, settings)
                val path = bytes?.let { saveScanBytes(context, it) }
                if (path != null) {
                    // PDF 与图片统一进入预览页：预览页可保存到系统目录并写入扫描历史
                    navigator.push(Route.ScanPreview(path, autoRectify = false))
                } else {
                    Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) navigator.push(Route.ScanPreview(uri.toString()))
    }



    val onNewDoc = { navigator.push(Route.ScanCamera()) }
    val onOpenHistory = { navigator.push(Route.ScanHistory) }
    val onIdPhoto = { navigator.push(Route.IdPhoto) }
    val onIdCardCopy = { navigator.push(Route.IdCardCopy) }
    val onIdPrint = { navigator.push(Route.IdPrint) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanMiuix(
            bottomInnerPadding = bottomInnerPadding,
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScanFromPrinter = scanFromPrinter,
            onScanSettings = { navigator.push(Route.ScanSettings) },
            onNewDoc = onNewDoc,
            onOpenHistory = onOpenHistory,
            onIdPhoto = onIdPhoto,
            onIdCardCopy = onIdCardCopy,
            onIdPrint = onIdPrint,
        )

        UiMode.Material -> ScanMaterial(
            bottomInnerPadding = bottomInnerPadding,
            onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScanFromPrinter = scanFromPrinter,
            onScanSettings = { navigator.push(Route.ScanSettings) },
            onNewDoc = onNewDoc,
            onOpenHistory = onOpenHistory,
            onIdPhoto = onIdPhoto,
            onIdCardCopy = onIdCardCopy,
            onIdPrint = onIdPrint,
        )
    }
}

private fun saveScanBytes(context: android.content.Context, bytes: ByteArray): String? {
    val dir = File(context.cacheDir, "scans")
    dir.mkdirs()
    val wantPdf = ScanSettingsStore.load().format == ScanOutputFormat.PDF
    return runCatching {
        if (wantPdf) {
            val out = ScanOutputFormat.toPdf(bytes) ?: return null
            val f = File(dir, "scan-${System.currentTimeMillis()}.pdf")
            FileOutputStream(f).use { it.write(out) }
            f.absolutePath
        } else {
            val out = ScanOutputFormat.toJpeg(bytes) ?: return null
            val f = File(dir, "scan-${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { it.write(out) }
            f.absolutePath
        }
    }.getOrNull()
}

@Composable
private fun ScanMiuix(
    bottomInnerPadding: Dp,
    onGallery: () -> Unit,
    onScanFromPrinter: () -> Unit,
    onScanSettings: () -> Unit,
    onNewDoc: () -> Unit,
    onOpenHistory: () -> Unit,
    onIdPhoto: () -> Unit,
    onIdCardCopy: () -> Unit,
    onIdPrint: () -> Unit,
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
                    title = stringResource(R.string.hp_scan_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
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
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_settings),
                            summary = stringResource(R.string.hp_scan_settings),
                            startAction = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_settings), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onScanSettings,
                        )
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_multi_title),
                            summary = stringResource(R.string.hp_scan_multi_hint),
                            startAction = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_multi_title), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onNewDoc,
                        )
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.hp_scan_history),
                            summary = stringResource(R.string.hp_scan_history_hint),
                            startAction = { Icon(Icons.Rounded.History, stringResource(R.string.hp_scan_history), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onOpenHistory,
                        )
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.hp_id_photo),
                            summary = stringResource(R.string.hp_id_photo_hint),
                            startAction = { Icon(Icons.Rounded.AccountBox, stringResource(R.string.hp_id_photo), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onIdPhoto,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.hp_id_card_copy),
                            summary = stringResource(R.string.hp_id_card_copy_hint),
                            startAction = { Icon(Icons.Rounded.Badge, stringResource(R.string.hp_id_card_copy), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onIdCardCopy,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.hp_id_print),
                            summary = stringResource(R.string.hp_id_print_hint),
                            startAction = { Icon(Icons.Rounded.ContactPage, stringResource(R.string.hp_id_print), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            onClick = onIdPrint,
                        )
                    }

                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun ScanMaterial(
    bottomInnerPadding: Dp,
    onGallery: () -> Unit,
    onScanFromPrinter: () -> Unit,
    onScanSettings: () -> Unit,
    onNewDoc: () -> Unit,
    onOpenHistory: () -> Unit,
    onIdPhoto: () -> Unit,
    onIdCardCopy: () -> Unit,
    onIdPrint: () -> Unit,
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
                item {
                    SegmentedListItem(
                        onClick = onScanSettings,
                        headlineContent = { Text(stringResource(R.string.hp_scan_settings)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_settings)) },
                        leadingContent = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_settings)) },
                    )
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.hp_scan_multi_title)) {
                item {
                    SegmentedListItem(
                        onClick = onNewDoc,
                        headlineContent = { Text(stringResource(R.string.hp_scan_multi_title)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_multi_hint)) },
                        leadingContent = { Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.hp_scan_multi_title)) },
                    )
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onOpenHistory,
                        headlineContent = { Text(stringResource(R.string.hp_scan_history)) },
                        supportingContent = { Text(stringResource(R.string.hp_scan_history_hint)) },
                        leadingContent = { Icon(Icons.Rounded.History, stringResource(R.string.hp_scan_history)) },
                    )
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onIdPhoto,
                        headlineContent = { Text(stringResource(R.string.hp_id_photo)) },
                        supportingContent = { Text(stringResource(R.string.hp_id_photo_hint)) },
                        leadingContent = { Icon(Icons.Rounded.AccountBox, stringResource(R.string.hp_id_photo)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onIdCardCopy,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_copy)) },
                        supportingContent = { Text(stringResource(R.string.hp_id_card_copy_hint)) },
                        leadingContent = { Icon(Icons.Rounded.Badge, stringResource(R.string.hp_id_card_copy)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onIdPrint,
                        headlineContent = { Text(stringResource(R.string.hp_id_print)) },
                        supportingContent = { Text(stringResource(R.string.hp_id_print_hint)) },
                        leadingContent = { Icon(Icons.Rounded.ContactPage, stringResource(R.string.hp_id_print)) },
                    )
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
