package com.hpprint.app.ui.screen.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hpprint.app.R
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.DocStore
import com.hpprint.app.data.scan.core.Exporter
import com.hpprint.app.data.scan.core.Filters
import com.hpprint.app.data.scan.core.OcrEngine
import com.hpprint.app.data.scan.core.Page
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.PressableIconButton
import com.hpprint.app.ui.util.rememberBlurBackdrop
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** 扫描文档文字识别：整份识别、可复制/分享/导出，结果缓存在文档目录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOcrScreen(docId: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DocStore.get(context) }
    val doc = remember(docId) { store.findDoc(docId) }

    if (doc == null) {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }

    var text by remember { mutableStateOf(store.readText(doc) ?: "") }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(doc.pages.size) }

    fun runOcr() {
        if (running || doc.pages.isEmpty()) return
        running = true
        text = ""
        total = doc.pages.size
        progress = 0
        store.clearText(doc)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val sb = StringBuilder()
                doc.pages.forEachIndexed { i, page ->
                    val bmp = loadOcrBitmap(page)
                    val body = if (bmp != null) {
                        val r = OcrEngine.recognize(bmp)
                        bmp.recycle()
                        r.text
                    } else ""
                    sb.append(context.getString(R.string.hp_scan_ocr_page, i + 1))
                    sb.append('\n')
                    sb.append(if (body.isBlank()) context.getString(R.string.hp_scan_ocr_empty_page) else body)
                    if (i != doc.pages.lastIndex) sb.append("\n\n")
                    withContext(Dispatchers.Main) { progress = i + 1 }
                }
                sb.toString()
            }
            running = false
            text = result
            if (result.isNotBlank()) store.writeText(doc, result)
        }
    }

    LaunchedEffect(Unit) {
        if (text.isBlank()) runOcr()
    }

    val onCopy: () -> Unit = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(doc.name, text))
        Toast.makeText(
            context,
            context.getString(R.string.hp_scan_ocr_copied, text.length),
            Toast.LENGTH_SHORT,
        ).show()
    }
    val onShare: () -> Unit = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, doc.name))
    }
    val onExport: () -> Unit = {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                Exporter.saveTextToDownloads(context, text, doc.name)
            }
            Toast.makeText(
                context,
                if (path != null) context.getString(R.string.hp_scan_export_ok, path)
                else context.getString(R.string.hp_scan_ocr_export_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> OcrMiuix(
            docName = doc.name,
            pageCount = doc.pages.size,
            text = text,
            running = running,
            progress = progress,
            total = total,
            onBack = { navigator.pop() },
            onRedo = { runOcr() },
            onCopy = onCopy,
            onShare = onShare,
            onExport = onExport,
        )

        UiMode.Material -> OcrMaterial(
            docName = doc.name,
            pageCount = doc.pages.size,
            text = text,
            running = running,
            progress = progress,
            total = total,
            onBack = { navigator.pop() },
            onRedo = { runOcr() },
            onCopy = onCopy,
            onShare = onShare,
            onExport = onExport,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrMiuix(
    docName: String,
    pageCount: Int,
    text: String,
    running: Boolean,
    progress: Int,
    total: Int,
    onBack: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    title = stringResource(R.string.hp_scan_ocr_title),
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        PressableIconButton(onClick = onRedo, enabled = !running) {
                            Icon(
                                Icons.Rounded.Refresh,
                                stringResource(R.string.hp_scan_ocr_redo),
                                tint = MiuixTheme.colorScheme.onBackground,
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
            item(key = "meta") {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    BasicComponent(
                        title = docName,
                        summary = stringResource(
                            R.string.hp_scan_ocr_meta,
                            pageCount,
                            text.length,
                        ),
                    )
                }
            }
            item(key = "result") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OcrResultMiuix(text, running, progress, total)
                }
            }
            item(key = "actions") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OcrActionMiuix(
                            icon = Icons.Rounded.ContentCopy,
                            label = stringResource(R.string.hp_scan_ocr_copy),
                            enabled = text.isNotBlank(),
                            onClick = onCopy,
                        )
                        OcrActionMiuix(
                            icon = Icons.Rounded.IosShare,
                            label = stringResource(R.string.hp_scan_ocr_share),
                            enabled = text.isNotBlank(),
                            onClick = onShare,
                        )
                        OcrActionMiuix(
                            icon = Icons.Rounded.Download,
                            label = stringResource(R.string.hp_scan_ocr_export),
                            enabled = text.isNotBlank(),
                            onClick = onExport,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun OcrResultMiuix(
    text: String,
    running: Boolean,
    progress: Int,
    total: Int,
) {
    if (running) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InfiniteProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            MiuixText(
                text = stringResource(
                    R.string.hp_scan_ocr_progress,
                    (progress + 1).coerceAtMost(total.coerceAtLeast(1)),
                    total,
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
            )
        }
    } else {
        SelectionContainer {
            MiuixText(
                text = text.ifBlank { stringResource(R.string.hp_scan_ocr_no_text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun RowScope.OcrActionMiuix(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MiuixTheme.colorScheme.onBackground
    } else {
        MiuixTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        MiuixText(text = label, color = tint, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrMaterial(
    docName: String,
    pageCount: Int,
    text: String,
    running: Boolean,
    progress: Int,
    total: Int,
    onBack: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_scan_ocr_title)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                actions = {
                    PressableIconButton(onClick = onRedo, enabled = !running) {
                        Icon(
                            Icons.Rounded.Refresh,
                            stringResource(R.string.hp_scan_ocr_redo),
                        )
                    }
                },
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
                .scrollEndVibration()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        headlineContent = { Text(docName) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.hp_scan_ocr_meta,
                                    pageCount,
                                    text.length,
                                )
                            )
                        },
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                OcrResultMaterial(text, running, progress, total)
            }
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onCopy,
                        enabled = text.isNotBlank(),
                        leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                        headlineContent = { Text(stringResource(R.string.hp_scan_ocr_copy)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onShare,
                        enabled = text.isNotBlank(),
                        leadingContent = { Icon(Icons.Rounded.IosShare, null) },
                        headlineContent = { Text(stringResource(R.string.hp_scan_ocr_share)) },
                    )
                }
                item {
                    SegmentedListItem(
                        onClick = onExport,
                        enabled = text.isNotBlank(),
                        leadingContent = { Icon(Icons.Rounded.Download, null) },
                        headlineContent = { Text(stringResource(R.string.hp_scan_ocr_export)) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OcrResultMaterial(
    text: String,
    running: Boolean,
    progress: Int,
    total: Int,
) {
    if (running) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadingIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(
                    R.string.hp_scan_ocr_progress,
                    (progress + 1).coerceAtMost(total.coerceAtLeast(1)),
                    total,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    } else {
        SelectionContainer {
            Text(
                text = text.ifBlank { stringResource(R.string.hp_scan_ocr_no_text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                fontSize = 14.sp,
            )
        }
    }
}

private fun loadOcrBitmap(page: Page) =
    BitmapUtil.decodeFile(page.file.path, 2200)?.let { src ->
        val out = Filters.apply(src, Filters.SHADOW)
        if (out !== src) src.recycle()
        out
    }
