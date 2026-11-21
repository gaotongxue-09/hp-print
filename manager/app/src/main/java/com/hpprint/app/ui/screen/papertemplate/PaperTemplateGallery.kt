package com.hpprint.app.ui.screen.papertemplate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.papertemplate.model.PaperCategory
import com.hpprint.app.data.papertemplate.model.PaperType
import com.hpprint.app.data.papertemplate.render.PaperRenderer
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import com.hpprint.app.ui.util.scrollEndVibration
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.min

/**
 * 纸张模板库（二级页）：搜索 + 按分类分组的双列缩略图。
 * 预览图使用与 PDF 相同的绘制引擎实时渲染，保证所见即所得。
 */
@Composable
fun PaperTemplateGalleryScreen(
    onSelect: (PaperType) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) { filterPaperTypes(query) }
    val suggestions = remember(query, filtered) {
        if (query.isBlank()) emptyList() else filtered.take(6)
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> GalleryMiuix(filtered, query, suggestions, { query = it }, onSelect, onBack)
        UiMode.Material -> GalleryMaterial(filtered, query, suggestions, { query = it }, onSelect, onBack)
    }
}

/** 模糊搜索：忽略大小写与空格，多关键词需全部命中；名称前缀命中优先。 */
private fun filterPaperTypes(query: String): List<PaperType> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return PaperType.entries
    return PaperType.entries
        .mapNotNull { type ->
            val label = type.label.lowercase()
            val category = type.category.label.lowercase()
            val key = type.key.lowercase()
            val matched = tokens.all {
                label.contains(it) || category.contains(it) || key.contains(it)
            }
            if (!matched) {
                null
            } else {
                val score = when {
                    tokens.all { label.startsWith(it) } -> 0
                    label.contains(tokens.first()) -> 1
                    else -> 2
                }
                type to score
            }
        }
        .sortedBy { it.second }
        .map { it.first }
}

@Composable
private fun GalleryMiuix(
    types: List<PaperType>,
    query: String,
    suggestions: List<PaperType>,
    onQueryChange: (String) -> Unit,
    onSelect: (PaperType) -> Unit,
    onBack: () -> Unit,
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
                    title = stringResource(R.string.print_template_section),
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndVibration()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SuggestionRowMiuix(suggestions, onSelect)
                }
            }
            if (types.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHintMiuix()
                }
            }
            PaperCategory.entries.forEach { cat ->
                val items = types.filter { it.category == cat }
                if (items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "cat-${cat.name}") {
                        Text(
                            text = "${cat.label}（${items.size}）",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(items, key = { it.key }) { type ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(type) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PaperThumb(
                                        type = type,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                                    )
                                }
                                Text(
                                    text = type.label,
                                    style = MiuixTheme.textStyles.body2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun EmptyHintMiuix() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.print_template_empty),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryMaterial(
    types: List<PaperType>,
    query: String,
    suggestions: List<PaperType>,
    onQueryChange: (String) -> Unit,
    onSelect: (PaperType) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.print_template_section)) },
                colors = expressiveTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.print_template_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SuggestionRowMaterial(suggestions, onSelect)
                }
            }
            if (types.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.print_template_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PaperCategory.entries.forEach { cat ->
                val items = types.filter { it.category == cat }
                if (items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "cat-${cat.name}") {
                        Text(
                            text = "${cat.label}（${items.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(items, key = { it.key }) { type ->
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelect(type) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PaperThumb(
                                        type = type,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                                    )
                                }
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 搜索建议（Miuix）：输入时实时展示最匹配的模板，点击直达。 */
@Composable
private fun SuggestionRowMiuix(
    suggestions: List<PaperType>,
    onSelect: (PaperType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { type ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable { onSelect(type) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = type.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 搜索建议（Material）：与 Miuix 一致，实时展示最匹配的模板。 */
@Composable
private fun SuggestionRowMaterial(
    suggestions: List<PaperType>,
    onSelect: (PaperType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { type ->
            SuggestionChip(
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

/**
 * 模板缩略预览：按纸张比例渲染一页模板。
 * 放在固定高度的容器里使用（例如 [height] 140dp），会自适应缩放。
 */
@Composable
internal fun PaperThumb(
    type: PaperType,
    modifier: Modifier = Modifier,
) {
    val config = remember(type) { type.defaultConfig().copy(pageCount = 1, showMargin = false) }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / config.contentWidthMm, maxHeight.value / config.contentHeightMm)
        val w = (config.contentWidthMm * scale).dp
        val h = (config.contentHeightMm * scale).dp
        Canvas(
            modifier = Modifier
                .size(w, h)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(config.paperColor)),
        ) {
            PaperRenderer.draw(drawContext.canvas.nativeCanvas, type, config)
        }
    }
}

/** 搜索框下方的辅助留白（供外部布局复用）。 */
