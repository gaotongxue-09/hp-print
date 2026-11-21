package com.hpprint.app.ui.screen.papertemplate

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hpprint.app.BuildConfig
import com.hpprint.app.R
import com.hpprint.app.data.papertemplate.model.PaperConfig
import com.hpprint.app.data.papertemplate.model.PaperType
import com.hpprint.app.data.papertemplate.render.PaperRenderer
import com.hpprint.app.data.papertemplate.render.PdfExporter
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.navigation3.Navigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hpprint.app.ui.util.rememberEdgeHaptic
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@Composable
fun PaperTemplatePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
    initialTypeKey: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var typeName by rememberSaveable {
        mutableStateOf(
            if (initialTypeKey.isBlank()) PaperType.LINED.name
            else PaperType.fromKey(initialTypeKey).name,
        )
    }
    val type = PaperType.valueOf(typeName)
    var config by remember { mutableStateOf(type.defaultConfig()) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var showFullPreview by rememberSaveable { mutableStateOf(false) }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bitmap != null) {
                    config = config.copy(logoBitmap = bitmap, logoEnabled = true)
                }
            }
        }
    }

    fun selectType(t: PaperType) {
        typeName = t.name
        config = t.defaultConfig()
        showGallery = false
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                exporting = true
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            PdfExporter.export(type, config, it)
                        } != null
                    }.getOrDefault(false)
                }
                exporting = false
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.paper_template_saved else R.string.paper_template_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun printNow() {
        scope.launch {
            exporting = true
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(context.cacheDir, "paper_template_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(f).use { PdfExporter.export(type, config, it) }
                    f
                }.getOrNull()
            }
            exporting = false
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    file,
                )
                navigator.push(Route.PrintSetup(files = listOf(PrintFile("PDF", uri.toString(), type.label))))
            } else {
                Toast.makeText(context, context.getString(R.string.paper_template_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PaperTemplateMiuix(
            type = type,
            config = config,
            showGallery = showGallery,
            exporting = exporting,
            bottomInnerPadding = bottomInnerPadding,
            onSelectType = ::selectType,
            onConfigChange = { config = it },
            onOpenGallery = { showGallery = true },
            onCloseGallery = { showGallery = false },
            onPrint = ::printNow,
            onSave = { saveLauncher.launch("paperme_${type.key}.pdf") },
            onPreviewClick = { showFullPreview = true },
            onPickLogo = { logoLauncher.launch("image/*") },
            onClearLogo = { config = config.copy(logoBitmap = null) },
        )

        UiMode.Material -> PaperTemplateMaterial(
            type = type,
            config = config,
            showGallery = showGallery,
            exporting = exporting,
            bottomInnerPadding = bottomInnerPadding,
            onSelectType = ::selectType,
            onConfigChange = { config = it },
            onOpenGallery = { showGallery = true },
            onCloseGallery = { showGallery = false },
            onPrint = ::printNow,
            onSave = { saveLauncher.launch("paperme_${type.key}.pdf") },
            onPreviewClick = { showFullPreview = true },
            onPickLogo = { logoLauncher.launch("image/*") },
            onClearLogo = { config = config.copy(logoBitmap = null) },
        )
    }

    if (showFullPreview) {
        PaperFullScreenPreview(type = type, config = config, onClose = { showFullPreview = false })
    }
}

/**
 * 全屏预览：支持双指缩放、拖动查看细节、多页翻页（对齐 PaperMe 原实现）。
 */
@Composable
private fun PaperFullScreenPreview(
    type: PaperType,
    config: PaperConfig,
    onClose: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = config.pageCountCoerced

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
        ) {
            ZoomablePaper(type = type, config = config)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    stringResource(R.string.print_template_page_of, page + 1, pageCount),
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onClose) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.print_template_close),
                        tint = Color.White,
                    )
                }
            }

            androidx.compose.material3.Text(
                stringResource(R.string.print_template_zoom_hint),
                color = Color(0xFFAAAAAA),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )

            if (pageCount > 1) {
                androidx.compose.material3.IconButton(
                    onClick = { if (page > 0) page-- },
                    enabled = page > 0,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(48.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.print_template_prev_page),
                        tint = if (page > 0) Color.White else Color(0xFF555555),
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { if (page < pageCount - 1) page++ },
                    enabled = page < pageCount - 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(48.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.print_template_next_page),
                        tint = if (page < pageCount - 1) Color.White else Color(0xFF555555),
                    )
                }
            }
        }
    }
}

/** 支持双指缩放 + 拖动的纸张预览。 */
@Composable
private fun ZoomablePaper(type: PaperType, config: PaperConfig) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                    scale = newScale
                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val paperW = config.contentWidthMm
            val paperH = config.contentHeightMm
            val baseScale = min(maxWidth.value / paperW, maxHeight.value / paperH)
            val w = (paperW * baseScale).dp
            val h = (paperH * baseScale).dp

            Canvas(
                modifier = Modifier
                    .size(w, h)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .background(Color(config.paperColor)),
            ) {
                PaperRenderer.draw(drawContext.canvas.nativeCanvas, type, config)
            }
        }
    }
}

/** 实时预览：复用与 PDF 相同的绘制引擎，严格按纸张比例等比缩放，四周留白保证完整可见。 */
@Composable
internal fun PaperPreviewCanvas(
    type: PaperType,
    config: PaperConfig,
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0x22000000),
    onClick: (() -> Unit)? = null,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    BoxWithConstraints(modifier = m, contentAlignment = Alignment.Center) {
        val paperW = config.contentWidthMm
        val paperH = config.contentHeightMm
        val inset = 8.dp
        val availableW = (maxWidth - inset * 2).value.coerceAtLeast(1f)
        val availableH = (maxHeight - inset * 2).value.coerceAtLeast(1f)
        val scale = min(availableW / paperW, availableH / paperH)
        val w = (paperW * scale).dp
        val h = (paperH * scale).dp

        Canvas(
            modifier = Modifier
                .size(w, h)
                .clipToBounds()
                .background(Color(config.paperColor))
                .border(1.dp, borderColor)
        ) {
            PaperRenderer.draw(drawContext.canvas.nativeCanvas, type, config)
        }
    }
}

/** 色块选择（双风格共用）：卡片内 4 列等分排列，避免超出卡片宽度。 */
@Composable
internal fun ColorSwatchRow(
    colors: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedBorder: Color,
    unselectedBorder: Color,
) {
    val haptic = rememberEdgeHaptic()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        colors.chunked(4).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowColors.forEach { (_, color) ->
                    val isSel = color == selected
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (isSel) 3.dp else 1.dp,
                                    color = if (isSel) selectedBorder else unselectedBorder,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    haptic()
                                    onSelect(color)
                                },
                        )
                    }
                }
                repeat(4 - rowColors.size) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 间距参数名（根据纸张类型语义）。 */
internal fun spacingLabel(type: PaperType): String = when (type) {
    PaperType.MUSIC, PaperType.GUITAR_TAB, PaperType.BASS_TAB, PaperType.UKULELE,
    PaperType.GUITAR_CHORD_STAFF -> "谱线间距"

    PaperType.CHINESE_TIANZI, PaperType.MIZIGE, PaperType.JIUGONGGE, PaperType.HUIGONGGE,
    PaperType.HARDPEN_HUIGONGGE, PaperType.CROSSGRID, PaperType.PINYIN_TIANZI,
    PaperType.CALLIGRAPHY, PaperType.GENKOU_YOUSHI -> "格边长"

    PaperType.GRID, PaperType.DOT, PaperType.PINK_MILLIMETER_GRID,
    PaperType.ARITHMETIC, PaperType.ISOMETRIC, PaperType.HEXAGONAL, PaperType.SKETCH,
    PaperType.SEWING_GRID, PaperType.CHILDREN_DRAWING -> "格距"

    else -> "间距"
}

/** 间距取值范围（根据类型）。 */
internal fun spacingRange(type: PaperType): ClosedFloatingPointRange<Float> = when (type) {
    PaperType.MUSIC, PaperType.GUITAR_TAB, PaperType.BASS_TAB, PaperType.UKULELE,
    PaperType.GUITAR_CHORD_STAFF -> 1.5f..8f

    PaperType.PINK_MILLIMETER_GRID -> 1f..10f
    PaperType.BRAILLE -> 3f..8f
    else -> 3f..30f
}

/** 是否使用次间距参数。 */
internal val PaperType.usesSecondary: Boolean
    get() = this in setOf(
        PaperType.GRID_LINED_SPLIT,
        PaperType.PINYIN_TIANZI,
    )

internal fun fmt(v: Float, integer: Boolean = false): String =
    if (integer) v.toInt().toString() else String.format("%.1f", v)

/**
 * 数值手动输入弹窗：点击滑块旁的数字后弹出，输入值自动夹取到滑块范围内并保留 1 位小数。
 */
@Composable
internal fun NumberInputDialog(
    title: String,
    initial: Float,
    range: ClosedFloatingPointRange<Float>,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var input by remember { mutableStateOf(fmt(initial)) }
    val parsed = input.trim().toFloatOrNull()
    val haptic = rememberEdgeHaptic()
    fun confirm() {
        val value = parsed ?: return
        haptic()
        val clamped = value.coerceIn(range.start, range.endInclusive)
        onConfirm((clamped * 10f).roundToInt() / 10f)
    }
    val rangeText = stringResource(R.string.paper_template_value_range, fmt(range.start), fmt(range.endInclusive))

    fun sanitize(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                MiuixTextField(
                    value = input,
                    onValueChange = { input = sanitize(it) },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixText(
                    text = rangeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.hp_cancel),
                        onClick = {
                            haptic()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.hp_ok),
                        onClick = ::confirm,
                        enabled = parsed != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )

        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { androidx.compose.material3.Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = sanitize(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.Text(
                        text = rangeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = ::confirm, enabled = parsed != null) {
                    androidx.compose.material3.Text(stringResource(R.string.hp_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic()
                    onDismiss()
                }) {
                    androidx.compose.material3.Text(stringResource(R.string.hp_cancel))
                }
            },
        )
    }
}
