package com.hpprint.app.ui.screen.idcardcopy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.ipp.PrinterScan
import com.hpprint.app.data.ipp.ScanSettingsStore
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.CardDetector
import com.hpprint.app.data.scan.core.CardOrientation
import com.hpprint.app.data.scan.core.CardTrimmer
import com.hpprint.app.data.scan.core.NativeScanEngine
import com.hpprint.app.data.scan.core.Doc
import com.hpprint.app.data.scan.core.DocStore
import com.hpprint.app.data.scan.core.Exporter
import com.hpprint.app.data.scan.core.IdCardComposer
import com.hpprint.app.data.scan.core.Perspective
import com.hpprint.app.data.scan.core.Quad
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveDialog
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.SegmentedSwitchItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.screen.idcard.CardAdjustDialog
import com.hpprint.app.ui.screen.scan.CardCameraCapture
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.DocumentRectify
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.rememberEdgeHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardCopyScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frontUri by remember { mutableStateOf<Uri?>(null) }
    var frontName by remember { mutableStateOf<String?>(null) }
    var backUri by remember { mutableStateOf<Uri?>(null) }
    var backName by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var scanFront by remember { mutableStateOf<Bitmap?>(null) }
    var scanBack by remember { mutableStateOf<Bitmap?>(null) }
    var scanStage by remember { mutableStateOf(0) }
    var scanBusy by remember { mutableStateOf(false) }
    var showScanSource by remember { mutableStateOf(false) }
    var cameraFront by remember { mutableStateOf<Boolean?>(null) }
    var layout by remember { mutableIntStateOf(IdCardComposer.LAYOUT_VERTICAL) }
    var border by remember { mutableStateOf(true) }
    // 拍完/扫完一面后进入参数调整，确认后才写回 scanFront / scanBack
    var adjustSource by remember { mutableStateOf<Bitmap?>(null) }
    var adjustFront by remember { mutableStateOf(true) }
    var adjustFromPrinter by remember { mutableStateOf(false) }
    var showFrontDone by remember { mutableStateOf(false) }
    val haptic = rememberEdgeHaptic()
    val docStore = remember { DocStore.get(context) }

    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            frontUri = uri
            frontName = uri.lastPathSegment
            preview = null
        }
    }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            backUri = uri
            backName = uri.lastPathSegment
            preview = null
        }
    }

    fun generate() {
        val fUri = frontUri
        val bUri = backUri
        val fScan = scanFront
        val bScan = scanBack
        if ((fScan == null && fUri == null) || (bScan == null && bUri == null)) return
        scope.launch {
            val sheet = withContext(Dispatchers.IO) {
                val fb = fScan ?: (fUri?.let { decodeBitmap(context, it) })
                val bb = bScan ?: (bUri?.let { decodeBitmap(context, it) })
                if (fb == null || bb == null) {
                    fb?.takeIf { it !== fScan }?.recycle()
                    bb?.takeIf { it !== bScan }?.recycle()
                    null
                } else {
                    // ScanLite 证件管线：检测四角 → 拉正 → 内缩去背景边 → 按文字方向转正
                    val fr = rectifyCard(fb, CardOrientation.SIDE_FRONT)
                    val br = rectifyCard(bb, CardOrientation.SIDE_BACK)
                    // 扫描稿（scanFront/scanBack）由状态持有，不能回收；仅回收本次解码的临时图
                    if (fr !== fb && fb !== fScan) fb.recycle()
                    if (br !== bb && bb !== bScan) bb.recycle()
                    val out = IdCardComposer.compose(fr, br, layout, IdCardComposer.DPI_PRINT, border)
                    if (fr !== fScan) fr.recycle()
                    if (br !== bScan) br.recycle()
                    out
                }
            }
            preview = sheet
        }
    }

    fun print() {
        val p = preview ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                val f = File(context.cacheDir, "idcardcopy.jpg")
                if (BitmapUtil.saveJpeg(p, f, 95)) f.absolutePath else null
            } ?: return@launch
            navigator.push(Route.PrintSetup(files = listOf(PrintFile("IMAGE", path, ""))))
        }
    }

    fun saveToDoc() {
        val p = preview ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val doc = docStore.createDoc(type = Doc.TYPE_IDCARD)
                docStore.addPage(doc, p) != null
            }
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.hp_id_card_saved else R.string.hp_scan_export_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun exportPdf() {
        val p = preview ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                Exporter.savePdfToDownloads(context, listOf(p), "idcard")
            }
            Toast.makeText(
                context,
                if (path != null) context.getString(R.string.hp_scan_export_ok, path)
                else context.getString(R.string.hp_scan_export_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun startScan() {
        showScanSource = true
    }

    fun startPrinterScan() {
        val printer = PrinterRepositoryImpl.getInstance().default()
        if (printer == null) {
            Toast.makeText(context, context.getString(R.string.hp_no_printer), Toast.LENGTH_SHORT).show()
            return
        }
        scanStage = if (scanFront == null) 1 else 2
    }

    fun startCameraScan() {
        cameraFront = if (scanFront == null) true else if (scanBack == null) false else true
    }

    fun applyAdjusted(bmp: Bitmap, front: Boolean) {
        val old = if (front) scanFront else scanBack
        if (front) {
            scanFront = bmp
            frontName = context.getString(R.string.hp_scan_saved)
        } else {
            scanBack = bmp
            backName = context.getString(R.string.hp_scan_saved)
        }
        if (old != null && old !== bmp) old.recycle()
        preview = null
        if (scanFront != null && scanBack != null) {
            generate()
        } else if (front) {
            // 第一面完成：打印机流程直接进入反面放置指引，相机流程弹窗提醒继续拍第二面
            if (adjustFromPrinter) scanStage = 2 else showFrontDone = true
        }
    }

    fun scanSide(front: Boolean) {
        val printer = PrinterRepositoryImpl.getInstance().default() ?: return
        scanBusy = true
        scope.launch {
            val settings = ScanSettingsStore.load()
            val bytes = withContext(Dispatchers.IO) { PrinterScan.scan(printer.ip, settings) }
            val raw = bytes?.let { decodeBitmapBytes(it) }
            // 自动裁边校正：定位稿台上的身份证并裁出，检测不到则保持原图（绝不破坏扫描结果）
            val bmp = raw?.let { withContext(Dispatchers.IO) { autoCropIdCard(it) } }
            scanBusy = false
            if (bmp == null) {
                Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                scanStage = 0
            } else {
                scanStage = 0
                adjustFromPrinter = true
                adjustFront = front
                adjustSource = bmp
            }
        }
    }

    val onBack = { navigator.pop() }
    val onPickFront = { frontPicker.launch("image/*") }
    val onPickBack = { backPicker.launch("image/*") }
    val onToggleLayout = {
        layout = if (layout == IdCardComposer.LAYOUT_VERTICAL) {
            IdCardComposer.LAYOUT_HORIZONTAL
        } else {
            IdCardComposer.LAYOUT_VERTICAL
        }
        if (preview != null) generate()
    }
    val readyCount =
        (if (scanFront != null || frontUri != null) 1 else 0) +
            (if (scanBack != null || backUri != null) 1 else 0)
    val scanSummary = when (readyCount) {
        1 -> stringResource(R.string.hp_id_card_scan_need_back)
        2 -> stringResource(R.string.hp_id_card_scan_ready)
        else -> stringResource(R.string.hp_id_card_scan_entry_desc)
    }
    val onBorderChange: (Boolean) -> Unit = { checked ->
        border = checked
        if (preview != null) generate()
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> IdCardCopyMiuix(
            frontName, backName, preview, layout, border, scanSummary,
            ::generate, ::print, ::saveToDoc, ::exportPdf, onToggleLayout, onBorderChange,
            onPickFront, onPickBack, ::startScan, onBack,
        )
        UiMode.Material -> IdCardCopyMaterial(
            frontName, backName, preview, layout, border, scanSummary,
            ::generate, ::print, ::saveToDoc, ::exportPdf, onToggleLayout, onBorderChange,
            onPickFront, onPickBack, ::startScan, onBack,
        )
    }

    if (scanStage != 0 || scanBusy) {
        val scanningFront = !scanBusy && scanStage == 1
        // 分步放置指引：第 2 步文案区分正面/反面，其余步骤通用
        val step2Res = if (scanningFront) R.string.hp_id_card_scan_step2_front else R.string.hp_id_card_scan_step2_back
        val steps = when {
            scanBusy -> listOf(stringResource(R.string.hp_scanning))
            else -> listOf(
                stringResource(R.string.hp_id_card_scan_step1),
                stringResource(step2Res),
                stringResource(R.string.hp_id_card_scan_step3),
                stringResource(R.string.hp_id_card_scan_step4),
            )
        }
        // 进度提示（第 1/2 次 或 第 2/2 次），让用户明确要扫描两面
        val progress = stringResource(R.string.hp_id_card_scanned_count, if (scanningFront) 0 else 1)
        val guideText = (listOf(progress) + steps).joinToString("\n\n")
        when (LocalUiMode.current) {
            UiMode.Miuix -> WindowDialog(
                show = true,
                title = stringResource(R.string.hp_id_card_scan),
                onDismissRequest = {
                    if (!scanBusy) {
                        haptic()
                        scanStage = 0
                    }
                },
                content = {
                    MiuixText(text = guideText)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        MiuixTextButton(
                            text = stringResource(R.string.hp_cancel),
                            onClick = {
                                haptic()
                                scanStage = 0
                            },
                            enabled = !scanBusy,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = stringResource(R.string.hp_id_card_scan_start),
                            onClick = {
                                haptic()
                                scanSide(scanningFront)
                            },
                            enabled = !scanBusy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                },
            )
            UiMode.Material -> ExpressiveDialog(
                onDismissRequest = {
                    if (!scanBusy) {
                        haptic()
                        scanStage = 0
                    }
                },
                title = { Text(stringResource(R.string.hp_id_card_scan)) },
                text = { Text(guideText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            haptic()
                            scanSide(scanningFront)
                        },
                        enabled = !scanBusy,
                    ) {
                        Text(stringResource(R.string.hp_id_card_scan_start))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            haptic()
                            scanStage = 0
                        },
                        enabled = !scanBusy,
                    ) {
                        Text(stringResource(R.string.hp_cancel))
                    }
                },
            )
        }
    }
    if (showScanSource) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> WindowDialog(
                show = true,
                title = stringResource(R.string.hp_scan_source_title),
                onDismissRequest = {
                    haptic()
                    showScanSource = false
                },
                content = {
                    MiuixText(text = stringResource(R.string.hp_scan_source_hint))
                    MiuixTextButton(
                        text = stringResource(R.string.hp_scan_source_printer),
                        onClick = {
                            haptic()
                            showScanSource = false
                            startPrinterScan()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.hp_scan_source_camera),
                        onClick = {
                            haptic()
                            showScanSource = false
                            startCameraScan()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                },
            )

            UiMode.Material -> ExpressiveDialog(
                onDismissRequest = {
                    haptic()
                    showScanSource = false
                },
                title = { Text(stringResource(R.string.hp_scan_source_title)) },
                text = { Text(stringResource(R.string.hp_scan_source_hint)) },
                confirmButton = {
                    TextButton(onClick = {
                        haptic()
                        showScanSource = false
                        startCameraScan()
                    }) {
                        Text(stringResource(R.string.hp_scan_source_camera))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        haptic()
                        showScanSource = false
                        startPrinterScan()
                    }) {
                        Text(stringResource(R.string.hp_scan_source_printer))
                    }
                },
            )
        }
    }

    if (showFrontDone) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> WindowDialog(
                show = true,
                title = stringResource(R.string.hp_id_card_front_done_title),
                onDismissRequest = {
                    haptic()
                    showFrontDone = false
                },
                content = {
                    MiuixText(text = stringResource(R.string.hp_id_card_front_done_msg))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        MiuixTextButton(
                            text = stringResource(R.string.hp_cancel),
                            onClick = {
                                haptic()
                                showFrontDone = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        MiuixTextButton(
                            text = stringResource(R.string.hp_id_card_continue_scan),
                            onClick = {
                                haptic()
                                showFrontDone = false
                                startCameraScan()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                },
            )
            UiMode.Material -> ExpressiveDialog(
                onDismissRequest = {
                    haptic()
                    showFrontDone = false
                },
                title = { Text(stringResource(R.string.hp_id_card_front_done_title)) },
                text = { Text(stringResource(R.string.hp_id_card_front_done_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        haptic()
                        showFrontDone = false
                        startCameraScan()
                    }) {
                        Text(stringResource(R.string.hp_id_card_continue_scan))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        haptic()
                        showFrontDone = false
                    }) {
                        Text(stringResource(R.string.hp_cancel))
                    }
                },
            )
        }
    }

    val captureFront = cameraFront
    if (captureFront != null) {
        CardCameraCapture(
            title = stringResource(
                if (captureFront) R.string.hp_id_card_shot_front_title
                else R.string.hp_id_card_shot_back_title,
            ),
            onCancel = { cameraFront = null },
            onCaptured = { bmp ->
                cameraFront = null
                adjustFromPrinter = false
                adjustFront = captureFront
                adjustSource = bmp
            },
        )
    }

    val adjusting = adjustSource
    if (adjusting != null) {
        CardAdjustDialog(
            title = stringResource(
                if (adjustFront) R.string.hp_id_card_side_front else R.string.hp_id_card_side_back,
            ),
            source = adjusting,
            onCancel = {
                adjustSource = null
                if (adjustFromPrinter) scanStage = if (adjustFront) 1 else 2
                if (adjusting !== scanFront && adjusting !== scanBack) adjusting.recycle()
            },
            onConfirm = { out ->
                adjustSource = null
                if (adjusting !== scanFront && adjusting !== scanBack) adjusting.recycle()
                applyAdjusted(out, adjustFront)
            },
        )
    }
}

@Composable
private fun IdCardCopyMiuix(
    frontName: String?,
    backName: String?,
    preview: Bitmap?,
    layout: Int,
    border: Boolean,
    scanSummary: String,
    onGenerate: () -> Unit,
    onPrint: () -> Unit,
    onSaveDoc: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleLayout: () -> Unit,
    onBorderChange: (Boolean) -> Unit,
    onPickFront: () -> Unit,
    onPickBack: () -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.hp_id_card_copy),
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
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_pick_front),
                    summary = frontName ?: stringResource(R.string.hp_id_card_front),
                    startAction = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_id_card_pick_front), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    onClick = onPickFront,
                )
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_pick_back),
                    summary = backName ?: stringResource(R.string.hp_id_card_back),
                    startAction = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_id_card_pick_back), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    onClick = onPickBack,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_scan_entry),
                    summary = scanSummary,
                    startAction = { Icon(Icons.Rounded.Print, stringResource(R.string.hp_id_card_scan_entry), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    onClick = onScan,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_generate_preview),
                    startAction = { Icon(Icons.Rounded.Print, stringResource(R.string.hp_generate_preview), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = frontName != null && backName != null,
                    onClick = onGenerate,
                )
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_layout),
                    summary = stringResource(
                        if (layout == IdCardComposer.LAYOUT_HORIZONTAL) R.string.hp_id_card_layout_horizontal
                        else R.string.hp_id_card_layout_vertical
                    ),
                    startAction = { Icon(Icons.Rounded.AccountBox, stringResource(R.string.hp_id_card_layout), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    onClick = onToggleLayout,
                )
                SwitchPreference(
                    title = stringResource(R.string.hp_id_card_border),
                    checked = border,
                    onCheckedChange = onBorderChange,
                )
            }

            preview?.let { bmp ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(210f / 297f).padding(6.dp),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_scan_export_print),
                    startAction = { Icon(Icons.Rounded.Print, stringResource(R.string.hp_scan_export_print), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = preview != null,
                    onClick = onPrint,
                )
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_save_doc),
                    startAction = { Icon(Icons.Rounded.Folder, stringResource(R.string.hp_id_card_save_doc), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = preview != null,
                    onClick = onSaveDoc,
                )
                ArrowPreference(
                    title = stringResource(R.string.hp_id_card_export_pdf),
                    startAction = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_id_card_export_pdf), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    enabled = preview != null,
                    onClick = onExportPdf,
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
private fun IdCardCopyMaterial(
    frontName: String?,
    backName: String?,
    preview: Bitmap?,
    layout: Int,
    border: Boolean,
    scanSummary: String,
    onGenerate: () -> Unit,
    onPrint: () -> Unit,
    onSaveDoc: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleLayout: () -> Unit,
    onBorderChange: (Boolean) -> Unit,
    onPickFront: () -> Unit,
    onPickBack: () -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_id_card_copy)) },
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
                    SegmentedListItem(
                        onClick = onPickFront,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_pick_front)) },
                        supportingContent = { Text(frontName ?: stringResource(R.string.hp_id_card_front)) },
                        leadingContent = { Icon(Icons.Rounded.Image, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onPickBack,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_pick_back)) },
                        supportingContent = { Text(backName ?: stringResource(R.string.hp_id_card_back)) },
                        leadingContent = { Icon(Icons.Rounded.Image, null) },
                    )
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onScan,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_scan_entry)) },
                        supportingContent = { Text(scanSummary) },
                        leadingContent = { Icon(Icons.Rounded.Print, null) },
                    )
                }
            }

            Button(
                onClick = onGenerate,
                enabled = frontName != null && backName != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hp_generate_preview))
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onToggleLayout,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_layout)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (layout == IdCardComposer.LAYOUT_HORIZONTAL) R.string.hp_id_card_layout_horizontal
                                    else R.string.hp_id_card_layout_vertical
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.AccountBox, null) },
                    )
                }
                item {
                    SegmentedSwitchItem(
                        title = stringResource(R.string.hp_id_card_border),
                        checked = border,
                        onCheckedChange = onBorderChange,
                    )
                }
            }

            preview?.let { bmp ->
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(210f / 297f).padding(8.dp),
                        )
                    }
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onPrint,
                        enabled = preview != null,
                        headlineContent = { Text(stringResource(R.string.hp_scan_export_print)) },
                        leadingContent = { Icon(Icons.Rounded.Print, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onSaveDoc,
                        enabled = preview != null,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_save_doc)) },
                        leadingContent = { Icon(Icons.Rounded.Folder, null) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onExportPdf,
                        enabled = preview != null,
                        headlineContent = { Text(stringResource(R.string.hp_id_card_export_pdf)) },
                        leadingContent = { Icon(Icons.Rounded.Image, null) },
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

private fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    while (w > 0 && h > 0 && w / 2 >= 2048 && h / 2 >= 2048) {
        w /= 2; h /= 2; sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

private fun decodeBitmapBytes(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    while (w > 0 && h > 0 && w / 2 >= 4096 && h / 2 >= 4096) {
        w /= 2; h /= 2; sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}.getOrNull()

/**
 * 扫描稿台整页图 → 自动定位身份证并裁出（对齐 HP Smart 的 PageLift 行为）。
 * 检测不到清晰身份证边界时返回原图（null 由调用方兜底为扫描失败），绝不破坏扫描结果。
 * 裁出的图再用 [DocumentRectify.rectify] 做透视校正，使身份证规整无畸变。
 */
private fun autoCropIdCard(src: Bitmap): Bitmap {
    val corners = DocumentRectify.detectCorners(src) ?: return src
    val rectified = DocumentRectify.rectify(src, corners, maxDim = 2400)
    // 裁出的结果尺寸异常（过小/比例严重失真）时回退原图，避免排版阶段再次拉伸变形
    if (rectified.width < 64 || rectified.height < 64) {
        if (rectified !== src) rectified.recycle()
        return src
    }
    return rectified
}

/**
 * ScanLite 证件矫正管线：四角检测 → 横向归一 → 透视拉正 → 去背景暗边 → 按文字方向转正。
 * 检测失败时原样返回，保证任何图片都能进入排版。
 */
private fun rectifyCard(src: Bitmap, side: Int): Bitmap {
    return try {
        val detected = detectCardQuad(src) ?: return src
        var quad = CardOrientation.landscape(insetEdges(detected, CardTrimmer.Insets(0.002f, 0.002f, 0.002f, 0.002f)))
        val w = IdCardComposer.CARD_PX_W
        val h = IdCardComposer.cardPxH(w)
        var out = Perspective.warpTo(src, quad, w, h)
        if (out === src) return src
        var pass = 0
        while (pass < 2) {
            val ins = CardTrimmer.measure(out)
            if (!ins.any) break
            val next = insetEdges(quad, ins)
            val warped = Perspective.warpTo(src, next, w, h)
            if (warped === src) break
            out.recycle()
            out = warped
            quad = next
            pass++
        }
        val fixed = CardOrientation.orient(out, side)
        if (fixed !== out) out.recycle()
        // 原生证件圆角（3.18mm / 85.6mm），不可用时保持直角
        val rounded = NativeScanEngine.cardCorner(fixed, ID_CARD_CORNER_MM, ID_CARD_WIDTH_MM)
        if (rounded != null && rounded !== fixed) {
            fixed.recycle()
            rounded
        } else {
            fixed
        }
    } catch (_: Throwable) {
        src
    }
}

/** 身份证四角检测：原生扫描模型优先，不可用或未检出时回退现有 [CardDetector]。 */
private fun detectCardQuad(src: Bitmap): Quad? {
    NativeScanEngine.detectBitmap(src)?.let { c ->
        return Quad(c[0], c[1], c[2], c[3])
    }
    return CardDetector.detect(src)
}

private const val ID_CARD_CORNER_MM = 3.18
private const val ID_CARD_WIDTH_MM = 85.6

/** 按四条边各自的收缩比例内缩四边形（双线性插值，保持透视关系） */
private fun insetEdges(q: Quad, i: CardTrimmer.Insets): Quad {
    fun lerp(a: PointF, b: PointF, t: Float) =
        PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    fun bil(u: Float, v: Float): PointF {
        val top = lerp(q.tl, q.tr, u)
        val bottom = lerp(q.bl, q.br, u)
        return lerp(top, bottom, v)
    }
    val l = i.left.coerceIn(0f, 0.2f)
    val r = 1f - i.right.coerceIn(0f, 0.2f)
    val t = i.top.coerceIn(0f, 0.2f)
    val b = 1f - i.bottom.coerceIn(0f, 0.2f)
    return Quad(bil(l, t), bil(r, t), bil(r, b), bil(l, b))
}