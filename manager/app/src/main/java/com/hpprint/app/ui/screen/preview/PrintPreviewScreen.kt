package com.hpprint.app.ui.screen.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.R
import com.hpprint.app.data.render.DocumentLoader
import com.hpprint.app.data.render.DocumentPages
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MAX_PREVIEW_PAGES = 5
private const val PREVIEW_DPI = 110

private sealed interface Preview {
    data class Pages(val bitmaps: List<Bitmap>) : Preview
    data class TextContent(val text: String) : Preview
    data class Url(val url: String) : Preview
    data object Unavailable : Preview
}

private data class PreviewEntry(val file: PrintFile, val preview: Preview)

@Composable
fun PrintPreviewScreen(files: List<PrintFile>, webUrl: String) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val previews by produceState(initialValue = emptyList<PreviewEntry>(), files, webUrl) {
        value = withContext(Dispatchers.IO) {
            files.map { PreviewEntry(it, loadPreview(context, it, webUrl)) }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PrintPreviewMiuix(previews) { navigator.pop() }
        UiMode.Material -> PrintPreviewMaterial(previews) { navigator.pop() }
    }
}

@Composable
private fun PrintPreviewMiuix(previews: List<PreviewEntry>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_preview),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            previews.forEach { entry ->
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = entry.file.displayTitle(),
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(12.dp))
                        when (val preview = entry.preview) {
                            is Preview.Pages -> preview.bitmaps.forEach { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }

                            is Preview.TextContent -> top.yukonga.miuix.kmp.basic.Text(
                                text = preview.text,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                            )

                            is Preview.Url -> top.yukonga.miuix.kmp.basic.Text(
                                text = preview.url,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                            )

                            Preview.Unavailable -> top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.hp_preview_unavailable),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
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

@Composable
private fun PrintPreviewMaterial(previews: List<PreviewEntry>, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_preview)) },
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
            previews.forEach { entry ->
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = entry.file.displayTitle(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            when (val preview = entry.preview) {
                                is Preview.Pages -> preview.bitmaps.forEach { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                }

                                is Preview.TextContent -> Text(
                                    text = preview.text,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                is Preview.Url -> Text(
                                    text = preview.url,
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                Preview.Unavailable -> Text(
                                    text = stringResource(R.string.hp_preview_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
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

private fun PrintFile.displayTitle(): String =
    title.ifBlank { sourceUri.substringAfterLast('/').ifBlank { "Document" } }

private suspend fun loadPreview(context: Context, file: PrintFile, webUrl: String): Preview {
    val kind = runCatching { PrintJobKind.valueOf(file.kind) }.getOrDefault(PrintJobKind.PDF)
    return when (kind) {
        PrintJobKind.IMAGE -> renderImage(context, file.sourceUri)
        PrintJobKind.PDF -> renderPdf(context, file.sourceUri)
        PrintJobKind.WEB -> Preview.Url(webUrl.ifBlank { file.sourceUri })
        PrintJobKind.TEXT -> renderText(context, file.sourceUri, webUrl)
        PrintJobKind.HTML -> renderOfficePreview(context, file, "text/html")
        PrintJobKind.DOCX -> renderOfficePreview(context, file, null)
        PrintJobKind.XLSX -> renderOfficePreview(context, file, null)
        PrintJobKind.PPTX -> renderOfficePreview(context, file, null)
    }
}

private fun localPath(uri: String): String = uri.removePrefix("file://")

private fun renderImage(context: Context, uri: String): Preview {
    val bitmap = runCatching {
        if (uri.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        } else {
            BitmapFactory.decodeFile(localPath(uri))
        }
    }.getOrNull()
    return if (bitmap != null) Preview.Pages(listOf(bitmap)) else Preview.Unavailable
}

private fun renderPdf(context: Context, uri: String): Preview {
    return runCatching {
        val pfd = if (uri.startsWith("content://")) {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        } else {
            ParcelFileDescriptor.open(java.io.File(localPath(uri)), ParcelFileDescriptor.MODE_READ_ONLY)
        } ?: return@runCatching Preview.Unavailable

        pfd.use {
            val renderer = PdfRenderer(it)
            val bitmaps = mutableListOf<Bitmap>()
            val pageCount = minOf(renderer.pageCount, MAX_PREVIEW_PAGES)
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            renderer.close()
            Preview.Pages(bitmaps)
        }
    }.getOrElse { Preview.Unavailable }
}

private fun renderText(context: Context, uri: String, webUrl: String): Preview {
    val text = if (uri.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.bufferedReader().readText() }
    } else {
        runCatching { java.io.File(localPath(uri)).readText() }.getOrNull()
    }
    val content = text?.take(4000)
    return when {
        !content.isNullOrBlank() -> Preview.TextContent(content)
        webUrl.isNotBlank() -> Preview.Url(webUrl)
        else -> Preview.Unavailable
    }
}

/** Office/HTML 统一走 DocumentLoader 低 dpi 原生渲染，逐页位图作为预览（预览即所得）。 */
private suspend fun renderOfficePreview(context: Context, file: PrintFile, mime: String?): Preview =
    withContext(Dispatchers.IO) {
        runCatching {
            DocumentLoader.load(context, Uri.parse(file.sourceUri), mime, PREVIEW_DPI)
        }.getOrNull()?.let { pages ->
            try {
                renderPagesPreview(pages)
            } finally {
                runCatching { pages.close() }
            }
        } ?: Preview.Unavailable
    }

private fun renderPagesPreview(pages: DocumentPages): Preview {
    val bitmaps = mutableListOf<Bitmap>()
    val count = minOf(pages.pageCount, MAX_PREVIEW_PAGES)
    for (i in 0 until count) {
        if (i < 0 || i >= pages.pageCount) break
        val page = pages.renderPage(i)
        // 预览控制内存占用：超宽页面等比缩放到 1600px 内
        val target = if (page.width > 1600) {
            val h = (page.height * (1600f / page.width)).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(page, 1600, h, true)
        } else page
        if (target !== page) page.recycle()
        bitmaps.add(target)
    }
    return if (bitmaps.isEmpty()) Preview.Unavailable else Preview.Pages(bitmaps)
}