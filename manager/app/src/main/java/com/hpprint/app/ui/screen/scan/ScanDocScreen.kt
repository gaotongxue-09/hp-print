package com.hpprint.app.ui.screen.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hpprint.app.R
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.DocStore
import com.hpprint.app.data.scan.core.Exporter
import com.hpprint.app.data.scan.core.Filters
import com.hpprint.app.data.scan.core.Page
import com.hpprint.app.data.scan.core.Perspective
import com.hpprint.app.data.scan.core.Quad
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveDialog
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.rememberEdgeHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

private class PageCrop(val bitmap: Bitmap, val index: Int)

/** 多页扫描文档：逐页预览、滤镜、旋转、裁切、删除，导出/打印/OCR。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDocScreen(docId: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DocStore.get(context) }
    val doc = remember(docId) { store.findDoc(docId) }

    if (doc == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }

    var version by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf<PageCrop?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var cropViewRef by remember { mutableStateOf<QuadCropView?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { version++ }

    val pageCount = doc.pages.size
    // 快照：doc.pages 是普通 MutableList，直接交给 LazyRow 时删除页会让布局仍按旧索引导航而越界闪退
    val pagesSnapshot = doc.pages.toList()

    LaunchedEffect(index, version) {
        val count = doc.pages.size
        if (count == 0) {
            preview = null
            return@LaunchedEffect
        }
        if (index !in 0 until count) {
            // 删除页后先修正索引，避免后续按越界索引读取页面导致崩溃
            index = index.coerceIn(0, count - 1)
            return@LaunchedEffect
        }
        val page = doc.pages.getOrNull(index) ?: return@LaunchedEffect
        preview = withContext(Dispatchers.IO) { loadFiltered(page, 1500) }
    }

    fun reload() {
        version++
    }

    fun setFilter(filter: Int) {
        val page = doc.pages.getOrNull(index) ?: return
        page.filter = filter
        store.persist()
        reload()
    }

    fun rotatePage() {
        val page = doc.pages.getOrNull(index) ?: return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val src = BitmapUtil.decodeFile(page.file.path, 2600) ?: return@withContext false
                val out = BitmapUtil.rotate(src, 90f)
                if (out !== src) src.recycle()
                val saved = BitmapUtil.saveJpeg(out, page.file)
                out.recycle()
                saved
            }
            busy = false
            if (ok) reload()
        }
    }

    fun deletePage() {
        if (pageCount <= 1) {
            askDelete = true
            return
        }
        val safe = index.coerceIn(0, doc.pages.size - 1)
        store.removePage(doc, safe)
        index = (safe - 1).coerceAtLeast(0)
        reload()
    }

    fun export(pdf: Boolean) {
        showExport = false
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmaps = doc.pages.mapNotNull { p -> loadFiltered(p, 2200) }
                if (bitmaps.isEmpty()) return@withContext null
                val out: String? = if (pdf) {
                    Exporter.savePdfToDownloads(context, bitmaps, doc.name)
                } else {
                    val n = Exporter.saveImagesToGallery(context, bitmaps, "HPPrint")
                    if (n > 0) "images:$n" else null
                }
                bitmaps.forEach { it.recycle() }
                out
            }
            busy = false
            when {
                result == null -> Toast.makeText(context, context.getString(R.string.hp_scan_export_failed), Toast.LENGTH_SHORT).show()
                result.startsWith("images:") -> {
                    val n = result.substringAfter(':').toIntOrNull() ?: 0
                    Toast.makeText(context, context.getString(R.string.hp_scan_export_images_ok, n), Toast.LENGTH_SHORT).show()
                }
                else -> Toast.makeText(context, context.getString(R.string.hp_scan_export_ok, result), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun printDoc() {
        showExport = false
        busy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                val bitmaps = doc.pages.mapNotNull { loadFiltered(it, 2200) }
                if (bitmaps.isEmpty()) return@withContext null
                val dir = File(context.cacheDir, "print")
                dir.mkdirs()
                val out = File(dir, "doc_${doc.id}.pdf")
                val ok = Exporter.exportPdf(bitmaps, out)
                bitmaps.forEach { it.recycle() }
                if (ok) out.absolutePath else null
            }
            busy = false
            if (path == null) {
                Toast.makeText(context, context.getString(R.string.hp_scan_export_failed), Toast.LENGTH_SHORT).show()
            } else {
                navigator.push(Route.PrintSetup(files = listOf(PrintFile("PDF", path, doc.name))))
            }
        }
    }

    if (crop != null) {
        val target = crop!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DarkIconButton(Icons.Rounded.ArrowBack) {
                    crop = null
                }
                Text(
                    text = stringResource(R.string.hp_scan_crop_page),
                    color = Color.White,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Capsule(stringResource(R.string.hp_scan_rotate), dark = true) { cropViewRef?.rotate90() }
                Spacer(Modifier.width(8.dp))
                Capsule(stringResource(R.string.hp_scan_reset), dark = true) { cropViewRef?.resetQuad() }
                Spacer(Modifier.width(8.dp))
                Capsule(stringResource(R.string.hp_scan_confirm), dark = false) {
                    val quad = cropViewRef?.getQuad()
                    if (quad != null) {
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val warped = Perspective.warpBitmap(target.bitmap, quad, 2600)
                                store.replacePage(doc, target.index, warped)
                                if (warped !== target.bitmap) warped.recycle()
                            }
                            crop = null
                            busy = false
                            reload()
                        }
                    }
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    QuadCropView(ctx).also { v ->
                        v.setImage(target.bitmap, null)
                        cropViewRef = v
                    }
                },
            )
            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp))
        }
        return
    }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val p = preview
                if (p != null && !p.isRecycled) {
                    Image(
                        bitmap = p.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                    )
                }
                if (pageCount > 0) {
                    Text(
                        text = "${index + 1} / $pageCount",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // 滤镜
            val currentFilter = doc.pages.getOrNull(index)?.filter ?: Filters.ORIGINAL
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filters.all.size) { i ->
                    val f = Filters.all[i]
                    FilterPill(
                        text = stringResource(filterLabel(f)),
                        selected = currentFilter == f,
                        onClick = { setFilter(f) },
                    )
                }
            }

            // 页面缩略图
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pagesSnapshot, key = { _, page -> page.file.path }) { i, page ->
                    PageThumb(
                        page = page,
                        index = i,
                        selected = i == index,
                        version = version,
                        onClick = { index = i },
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 74.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { navigator.push(Route.ScanCamera(doc.id)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.hp_scan_add_page))
                    }
                }
            }

            // 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionLabel(Icons.Rounded.RotateRight, stringResource(R.string.hp_scan_rotate)) { rotatePage() }
                ActionLabel(Icons.Rounded.Crop, stringResource(R.string.hp_scan_crop_page)) {
                    val page = doc.pages.getOrNull(index) ?: return@ActionLabel
                    busy = true
                    scope.launch {
                        val bmp = withContext(Dispatchers.IO) { BitmapUtil.decodeFile(page.file.path, 2600) }
                        busy = false
                        if (bmp != null) crop = PageCrop(bmp, index)
                    }
                }
                ActionLabel(Icons.Rounded.Delete, stringResource(R.string.hp_scan_delete_page)) { deletePage() }
                ActionLabel(Icons.Rounded.Print, stringResource(R.string.hp_print_button)) { printDoc() }
                ExportMenuAction(
                    show = showExport,
                    onOpen = { showExport = true },
                    onDismiss = { showExport = false },
                    onPdf = { export(pdf = true) },
                    onImages = { export(pdf = false) },
                    onPrint = { printDoc() },
                    onOcr = {
                        showExport = false
                        navigator.push(Route.ScanOcr(doc.id))
                    },
                )
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() + 8.dp,
                ),
            )
        }
    }

    val title = doc.name
    when (LocalUiMode.current) {
        UiMode.Miuix -> DocMiuix(title, { navigator.pop() }, { askDelete = true }, content)
        UiMode.Material -> DocMaterial(title, { navigator.pop() }, { askDelete = true }, content)
    }

    if (busy) {
        // 只保留一个跟随主题黑白的加载动画，不铺底色
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

    if (askDelete) {
        ConfirmDeleteDoc(
            name = doc.name,
            onDismiss = { askDelete = false },
            onConfirm = {
                askDelete = false
                store.deleteDoc(doc)
                navigator.pop()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocMiuix(
    title: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = title,
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { inner ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            content(inner)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocMaterial(
    title: String,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.hp_scan_delete_doc))
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { inner ->
        Box(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) {
            content(inner)
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val primary = if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val onPrimary = if (isMiuix) MiuixTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
    val soft = if (isMiuix) MiuixTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onSoft = if (isMiuix) MiuixTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val container = if (selected) primary else soft
    val contentColor = if (selected) onPrimary else onSoft
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = text, color = contentColor, fontSize = 12.sp)
    }
}

@Composable
private fun PageThumb(page: Page, index: Int, selected: Boolean, version: Int, onClick: () -> Unit) {
    val bmp = remember(page.file.path, version) {
        runCatching {
            BitmapFactory.decodeFile(
                page.file.path,
                BitmapFactory.Options().apply { inSampleSize = 8 },
            )
        }.getOrNull()
    }
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val surface = if (isMiuix) MiuixTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant
    val primary = if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val outline = if (isMiuix) MiuixTheme.colorScheme.outline else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 74.dp)
            .background(surface, RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) primary else outline,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        }
        Text(
            text = "${index + 1}",
            color = Color.White,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ActionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val tint = if (LocalUiMode.current == UiMode.Miuix) MiuixTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.onSurface
    val strongHaptic = rememberEdgeHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(if (pressed) 0.84f else 1f, label = "actionIconScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    strongHaptic()
                    onClick()
                },
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp).scale(iconScale),
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 11.sp)
    }
}

@Composable
private fun DarkIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Capsule(text: String, dark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFFFABB06),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (dark) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExportMenuAction(
    show: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPdf: () -> Unit,
    onImages: () -> Unit,
    onPrint: () -> Unit,
    onOcr: () -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.hp_scan_export_pdf),
        stringResource(R.string.hp_scan_export_images),
        stringResource(R.string.hp_scan_export_print),
        stringResource(R.string.hp_scan_ocr_title),
    )
    val actions = listOf(onPdf, onImages, onPrint, onOcr)
    fun perform(index: Int) {
        onDismiss()
        actions[index]()
    }
    Box {
        ActionLabel(Icons.Rounded.IosShare, stringResource(R.string.hp_scan_export), onOpen)
        when (LocalUiMode.current) {
            UiMode.Miuix -> OverlayDropdownPopup(
                entries = listOf(
                    DropdownEntry(
                        labels.mapIndexed { index, label ->
                            DropdownItem(text = label, onClick = { perform(index) })
                        },
                    ),
                ),
                show = show,
                onDismiss = onDismiss,
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
                collapseOnSelection = true,
            )

            UiMode.Material -> DropdownMenu(
                expanded = show,
                onDismissRequest = onDismiss,
            ) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { perform(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDoc(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val message = stringResource(R.string.hp_scan_delete_doc_confirm, name)
    val strongHaptic = rememberEdgeHaptic()
    fun tap(action: () -> Unit): () -> Unit = {
        strongHaptic()
        action()
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true,
            title = stringResource(R.string.hp_scan_delete_doc),
            onDismissRequest = onDismiss,
            content = {
                MiuixText(text = message)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.hp_cancel),
                        onClick = tap(onDismiss),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.hp_ok),
                        onClick = tap(onConfirm),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
        UiMode.Material -> ExpressiveDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.hp_scan_delete_doc)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = tap(onConfirm)) { Text(stringResource(R.string.hp_ok)) }
            },
            dismissButton = {
                TextButton(onClick = tap(onDismiss)) { Text(stringResource(R.string.hp_cancel)) }
            },
        )
    }
}

private fun filterLabel(filter: Int): Int = when (filter) {
    Filters.ENHANCE -> R.string.hp_scan_filter_enhance
    Filters.GRAY -> R.string.hp_scan_filter_gray
    Filters.BW -> R.string.hp_scan_filter_bw
    Filters.SHADOW -> R.string.hp_scan_filter_shadow
    else -> R.string.hp_scan_filter_original
}

private fun loadFiltered(page: Page, max: Int): Bitmap? {
    val src = BitmapUtil.decodeFile(page.file.path, max) ?: return null
    val out = Filters.apply(src, page.filter)
    if (out !== src) src.recycle()
    return out
}
