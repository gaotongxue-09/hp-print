package com.hpprint.app.ui.screen.idprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hpprint.app.R
import com.hpprint.app.data.ipp.PrinterScan
import com.hpprint.app.data.ipp.ScanSettingsStore
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.CardKind
import com.hpprint.app.data.scan.core.DocDetector
import com.hpprint.app.data.scan.core.IdCardComposer
import com.hpprint.app.data.scan.core.Perspective
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveDialog
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.screen.idcard.CardAdjustDialog
import com.hpprint.app.ui.screen.scan.CardCameraCapture
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.PressableIconButton
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.rememberEdgeHaptic
import com.hpprint.app.ui.util.scrollEndVibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.io.File

private data class IdPrintItem(
    val file: PrintFile,
    val preview: Bitmap,
)

/** 证件打印：打印机扫描仪 / 手机摄像头 / 相册，三种来源统一自动矫正后进入打印。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdPrintScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<IdPrintItem>>(emptyList()) }
    var showCamera by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var adjustBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var cardKind by rememberSaveable { mutableStateOf(CardKind.default) }
    val haptic = rememberEdgeHaptic()

    fun addBitmap(bmp: Bitmap) {
        val path = saveJpeg(context, bmp)
        if (path == null) {
            Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
            bmp.recycle()
            return
        }
        items = items + IdPrintItem(PrintFile("IMAGE", path, ""), bmp)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val raw = decodeUri(context, uri) ?: return@mapNotNull null
                    val fixed = rectifyDocument(raw)
                    saveJpeg(context, fixed)?.let { IdPrintItem(PrintFile("IMAGE", it, ""), fixed) }
                }
            }
            working = false
            items = items + loaded
        }
    }

    fun scanFromPrinter() {
        val printer = PrinterRepositoryImpl.getInstance().default()
        if (printer == null) {
            Toast.makeText(context, context.getString(R.string.hp_no_printer), Toast.LENGTH_SHORT).show()
            return
        }
        working = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                val bytes = PrinterScan.scan(printer.ip, ScanSettingsStore.load()) ?: return@withContext null
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                rectifyDocument(raw)
            }
            working = false
            if (bmp == null) {
                Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
            } else {
                adjustBitmap = bmp
            }
        }
    }

    /** 打印时按 1:1 尺寸把每张证件排到 A4 页面上（一页一张，居中），再交给打印设置。 */
    fun printNow() {
        if (items.isEmpty()) return
        working = true
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                items.mapIndexedNotNull { index, item ->
                    val page = runCatching {
                        IdCardComposer.compose(
                            item.preview,
                            null,
                            IdCardComposer.LAYOUT_VERTICAL,
                            IdCardComposer.DPI_PRINT,
                            false,
                            cardKind.widthMm,
                            cardKind.heightMm,
                            cardKind.cornerMm,
                        )
                    }.getOrNull() ?: return@mapIndexedNotNull null
                    val file = File(context.cacheDir, "idprint_a4_${System.currentTimeMillis()}_$index.jpg")
                    val ok = BitmapUtil.saveJpeg(page, file, 95)
                    page.recycle()
                    if (ok) PrintFile("IMAGE", file.absolutePath, "") else null
                }
            }
            working = false
            if (files.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.hp_id_print_compose_failed), Toast.LENGTH_SHORT).show()
            } else {
                navigator.push(Route.PrintSetup(files = files))
            }
        }
    }

    val onDelete: (IdPrintItem) -> Unit = { target ->
        items = items.filterNot { it.file.sourceUri == target.file.sourceUri }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> IdPrintMiuix(
                items = items,
                cardKind = cardKind,
                onPickKind = { kind -> haptic(); cardKind = kind },
                onBack = { navigator.pop() },
                onPrint = ::printNow,
                onPreview = { showPreview = true },
                onScanPrinter = ::scanFromPrinter,
                onCamera = { showCamera = true },
                onAlbum = { galleryLauncher.launch(arrayOf("image/*")) },
                onDelete = onDelete,
            )

            UiMode.Material -> IdPrintMaterial(
                items = items,
                cardKind = cardKind,
                onPickKind = { kind -> haptic(); cardKind = kind },
                onBack = { navigator.pop() },
                onPrint = ::printNow,
                onPreview = { showPreview = true },
                onScanPrinter = ::scanFromPrinter,
                onCamera = { showCamera = true },
                onAlbum = { galleryLauncher.launch(arrayOf("image/*")) },
                onDelete = onDelete,
            )
        }

        if (working) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (LocalUiMode.current) {
                    UiMode.Miuix -> InfiniteProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MiuixTheme.colorScheme.onBackground,
                    )

                    UiMode.Material -> LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    if (showCamera) {
        CardCameraCapture(
            title = stringResource(R.string.hp_id_print),
            guideAspect = cardKind.aspect,
            onCancel = { showCamera = false },
            onCaptured = { bmp ->
                showCamera = false
                adjustBitmap = bmp
            },
        )
    }

    val adjusting = adjustBitmap
    if (adjusting != null) {
        CardAdjustDialog(
            title = stringResource(R.string.hp_card_adjust),
            source = adjusting,
            onCancel = {
                adjustBitmap = null
                adjusting.recycle()
            },
            onConfirm = { out ->
                adjustBitmap = null
                adjusting.recycle()
                addBitmap(out)
            },
        )
    }

    if (showPreview && items.isNotEmpty()) {
        IdPrintPreviewDialog(
            items = items,
            cardKind = cardKind,
            onDismiss = { showPreview = false },
            onPrint = {
                showPreview = false
                printNow()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdPrintMiuix(
    items: List<IdPrintItem>,
    cardKind: CardKind,
    onPickKind: (CardKind) -> Unit,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onScanPrinter: () -> Unit,
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onDelete: (IdPrintItem) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = stringResource(R.string.hp_id_print),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        PressableIconButton(onClick = onPrint, enabled = items.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.Print,
                                stringResource(R.string.hp_id_print_go),
                                tint = MiuixTheme.colorScheme.onBackground.copy(
                                    alpha = if (items.isEmpty()) 0.35f else 1f
                                ),
                            )
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
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
            item(key = "kind") {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    OverlayDropdownPreference(
                        items = CardKind.all.map { stringResource(it.labelRes) },
                        selectedIndex = CardKind.all.indexOf(cardKind),
                        title = stringResource(R.string.hp_id_print_card_kind),
                        startAction = {
                            Icon(
                                Icons.Rounded.ContactPage,
                                null,
                                modifier = Modifier.padding(end = 12.dp),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        onSelectedIndexChange = { index ->
                            CardKind.all.getOrNull(index)?.let(onPickKind)
                        },
                    )
                }
            }
            item(key = "sources") {
                Column(
                    modifier = Modifier,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.hp_id_print_scanner),
                            summary = stringResource(R.string.hp_id_print_source_hint),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Scanner,
                                    null,
                                    modifier = Modifier.padding(end = 12.dp),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            },
                            onClick = onScanPrinter,
                        )
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.hp_id_print_camera),
                            summary = stringResource(R.string.hp_id_print_camera_hint),
                            startAction = {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    null,
                                    modifier = Modifier.padding(end = 12.dp),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            },
                            onClick = onCamera,
                        )
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.hp_id_print_album),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ContactPage,
                                    null,
                                    modifier = Modifier.padding(end = 12.dp),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            },
                            onClick = onAlbum,
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item(key = "empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        MiuixText(
                            text = stringResource(R.string.hp_id_print_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                item(key = "count") {
                    MiuixText(
                        text = stringResource(R.string.hp_id_print_added, items.size),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                item(key = "preview") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.hp_id_print_preview),
                            summary = stringResource(R.string.hp_id_print_preview_hint),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Visibility,
                                    null,
                                    modifier = Modifier.padding(end = 12.dp),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            },
                            onClick = onPreview,
                        )
                    }
                }
                items(items, key = { it.file.sourceUri }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = stringResource(R.string.hp_id_print),
                            summary = stringResource(R.string.hp_id_print_source_hint),
                            startAction = {
                                Image(
                                    bitmap = item.preview.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            },
                            endActions = {
                                PressableIconButton(onClick = { onDelete(item) }) {
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

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdPrintMaterial(
    items: List<IdPrintItem>,
    cardKind: CardKind,
    onPickKind: (CardKind) -> Unit,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onPreview: () -> Unit,
    onScanPrinter: () -> Unit,
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onDelete: (IdPrintItem) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_id_print)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    PressableIconButton(onClick = onPrint, enabled = items.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Print,
                            stringResource(R.string.hp_id_print_go),
                            tint = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (items.isEmpty()) 0.35f else 1f
                            ),
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .scrollEndVibration()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.ContactPage,
                        title = stringResource(R.string.hp_id_print_card_kind),
                        items = CardKind.all.map { stringResource(it.labelRes) },
                        selectedIndex = CardKind.all.indexOf(cardKind),
                        onItemSelected = { index ->
                            CardKind.all.getOrNull(index)?.let(onPickKind)
                        },
                    )
                }
            }
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onScanPrinter,
                        leadingContent = { Icon(Icons.Rounded.Scanner, null) },
                        headlineContent = { Text(stringResource(R.string.hp_id_print_scanner)) },
                        supportingContent = { Text(stringResource(R.string.hp_id_print_source_hint)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onCamera,
                        leadingContent = { Icon(Icons.Rounded.PhotoCamera, null) },
                        headlineContent = { Text(stringResource(R.string.hp_id_print_camera)) },
                        supportingContent = { Text(stringResource(R.string.hp_id_print_camera_hint)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onAlbum,
                        leadingContent = { Icon(Icons.Rounded.ContactPage, null) },
                        headlineContent = { Text(stringResource(R.string.hp_id_print_album)) },
                    )
                }
            }

            if (items.isEmpty()) {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.hp_id_print_empty)) },
                            supportingContent = { Text(stringResource(R.string.hp_id_print_source_hint)) },
                        )
                    }
                }
            } else {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    items.forEach { item ->
                        item {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.hp_id_print)) },
                                supportingContent = { Text(stringResource(R.string.hp_id_print_added, items.size)) },
                                leadingContent = {
                                    Image(
                                        bitmap = item.preview.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        PressableIconButton(onClick = { onDelete(item) }) {
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
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            onClick = onPreview,
                            leadingContent = { Icon(Icons.Rounded.Visibility, null) },
                            headlineContent = { Text(stringResource(R.string.hp_id_print_preview)) },
                            supportingContent = { Text(stringResource(R.string.hp_id_print_preview_hint)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IdPrintPreviewDialog(
    items: List<IdPrintItem>,
    cardKind: CardKind,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
) {
    val haptic = rememberEdgeHaptic()
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var composing by remember { mutableStateOf(true) }

    LaunchedEffect(items, cardKind) {
        composing = true
        val result = withContext(Dispatchers.Default) {
            items.mapNotNull { item ->
                runCatching {
                    IdCardComposer.compose(
                        item.preview,
                        null,
                        IdCardComposer.LAYOUT_VERTICAL,
                        IdCardComposer.DPI_PREVIEW,
                        false,
                        cardKind.widthMm,
                        cardKind.heightMm,
                        cardKind.cornerMm,
                    )
                }.getOrNull()
            }
        }
        pages = result
        index = index.coerceIn(0, (result.size - 1).coerceAtLeast(0))
        composing = false
    }

    DisposableEffect(Unit) {
        onDispose { pages.forEach { it.recycle() } }
    }

    Dialog(
        onDismissRequest = {
            haptic()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (pages.isEmpty()) "" else "${index + 1} / ${pages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        haptic()
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.print_template_close),
                            tint = Color.White,
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val page = pages.getOrNull(index)
                    if (page != null) {
                        Image(
                            bitmap = page.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                        )
                    } else if (composing) {
                        Text(
                            text = stringResource(R.string.hp_scan_processing),
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                if (pages.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    haptic()
                                    index--
                                }
                            },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                contentDescription = stringResource(R.string.print_template_prev_page),
                                tint = if (index > 0) Color.White else Color(0xFF555555),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < pages.size - 1) {
                                    haptic()
                                    index++
                                }
                            },
                            enabled = index < pages.size - 1,
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = stringResource(R.string.print_template_next_page),
                                tint = if (index < pages.size - 1) Color.White else Color(0xFF555555),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            haptic()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.print_template_close), color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            haptic()
                            onPrint()
                        },
                        enabled = pages.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.hp_id_print_go), color = Color(0xFFFABB06))
                    }
                }
            }
        }
    }
}

private fun decodeUri(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

private fun rectifyDocument(bmp: Bitmap): Bitmap {
    val quad = runCatching { DocDetector.detect(bmp) }.getOrNull() ?: return bmp
    val warped = runCatching { Perspective.warpBitmap(bmp, quad, 3000) }.getOrNull() ?: return bmp
    if (warped !== bmp) bmp.recycle()
    return warped
}

private fun saveJpeg(context: Context, bmp: Bitmap): String? {
    val file = File(context.cacheDir, "idprint_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
    return if (BitmapUtil.saveJpeg(bmp, file, 92)) file.absolutePath else null
}
