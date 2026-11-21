package com.hpprint.app.ui.screen.webarticle

import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.miuix.EditText
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebArticleScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val normalized = normalizeUrl(url)
    val canFetch = normalized.isNotBlank() && !fetching

    val onFetch: () -> Unit = {
        error = null
        fetching = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchArticle(normalized) }.getOrNull()
            }
            fetching = false
            if (result == null) {
                error = ""
            } else {
                val (title, html) = result
                val file = withContext(Dispatchers.IO) {
                    writeHtml(context, html)
                }
                if (file == null) {
                    error = ""
                } else {
                    navigator.push(
                        Route.PrintSetup(
                            files = listOf(PrintFile("HTML", Uri.fromFile(file).toString(), title.ifBlank { "Article" })),
                        )
                    )
                }
            }
        }
    }
    val onBack = { navigator.pop() }

    when (LocalUiMode.current) {
        UiMode.Miuix -> WebArticleMiuix(url, { url = it }, canFetch, fetching, error, onFetch, onBack)
        UiMode.Material -> WebArticleMaterial(url, { url = it }, canFetch, fetching, error, onFetch, onBack)
    }
}

private fun normalizeUrl(url: String): String = when {
    url.isBlank() -> ""
    url.startsWith("http://") || url.startsWith("https://") -> url
    else -> "https://$url"
}

private fun fetchArticle(url: String): Pair<String, String>? {
    val doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
        .timeout(15000)
        .followRedirects(true)
        .get()
    val title = doc.title()?.trim().orEmpty()
    val paragraphs = doc.select("article p, .content p, .article p, main p, p")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    if (paragraphs.isEmpty()) {
        doc.body()?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { paragraphs.add(it) }
    }
    if (paragraphs.isEmpty()) return null
    return title to buildHtml(title, paragraphs)
}

private fun buildHtml(title: String, paragraphs: List<String>): String {
    val h = escapeHtml(title)
    val body = paragraphs.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }
    return """
        <!DOCTYPE html>
        <html lang="zh-CN"><head><meta charset="utf-8">
        <style>
            body { font-family: -apple-system, 'Noto Sans CJK SC', 'PingFang SC', sans-serif; font-size: 15px; line-height: 1.7; color: #222; padding: 24px; }
            h1 { font-size: 22px; line-height: 1.35; margin: 0 0 16px 0; }
            p { margin: 0 0 12px 0; }
        </style>
        </head><body>
        <h1>$h</h1>
        $body
        </body></html>
    """.trimIndent()
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun writeHtml(context: android.content.Context, html: String): File? = runCatching {
    val f = File(context.cacheDir, "article_${System.currentTimeMillis()}.html")
    f.writeText(html, Charsets.UTF_8)
    f
}.getOrNull()

@Composable
private fun WebArticleMiuix(
    url: String,
    onUrlChange: (String) -> Unit,
    canFetch: Boolean,
    fetching: Boolean,
    error: String?,
    onFetch: () -> Unit,
    onBack: () -> Unit,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    val fetchSummary = when {
        fetching -> stringResource(R.string.hp_article_fetching)
        error != null -> stringResource(R.string.hp_article_empty)
        url.isNotBlank() -> normalizeUrl(url)
        else -> stringResource(R.string.hp_article_enter_url)
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.hp_web_article_print),
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
                EditText(
                    title = stringResource(R.string.hp_article_enter_url),
                    value = url,
                    onValueChange = onUrlChange,
                    textHint = stringResource(R.string.hp_article_enter_url),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_article_fetch),
                    summary = fetchSummary,
                    startAction = {
                        Icon(Icons.Filled.Language, stringResource(R.string.hp_article_fetch), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    enabled = canFetch,
                    onClick = onFetch,
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

@Composable
private fun WebArticleMaterial(
    url: String,
    onUrlChange: (String) -> Unit,
    canFetch: Boolean,
    fetching: Boolean,
    error: String?,
    onFetch: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_web_article_print)) },
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
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.hp_article_enter_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Filled.Language, null) },
                isError = error != null,
            )
            if (error != null) {
                Text(
                    stringResource(R.string.hp_article_empty),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onFetch,
                enabled = canFetch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Language, null)
                Text(
                    text = stringResource(if (fetching) R.string.hp_article_fetching else R.string.hp_article_fetch),
                    modifier = Modifier.padding(start = 8.dp),
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

