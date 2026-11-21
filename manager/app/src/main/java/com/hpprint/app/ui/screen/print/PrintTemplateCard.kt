package com.hpprint.app.ui.screen.print

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.papertemplate.model.PaperType
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.screen.papertemplate.PaperThumb
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 打印页底部「打印模板」入口卡片展示的示例模板。 */
private val PRINT_TEMPLATE_EXAMPLES = listOf(
    PaperType.LINED,
    PaperType.GRID,
    PaperType.CHINESE_TIANZI,
    PaperType.MIZIGE,
    PaperType.MUSIC,
    PaperType.CORNELL,
    PaperType.WEEKLY_PLANNER,
    PaperType.STORYBOARD,
    PaperType.ISOMETRIC,
    PaperType.DOT,
)

@Composable
internal fun PrintTemplateCardMiuix(
    onOpenGallery: () -> Unit,
    onOpenTemplate: (PaperType) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(R.string.print_template_section),
            onClick = onOpenGallery,
        )
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PRINT_TEMPLATE_EXAMPLES, key = { it.key }) { type ->
                    TemplateExampleItem(
                        type = type,
                        name = type.label,
                        nameStyle = MiuixTheme.textStyles.body2,
                        nameColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        onClick = { onOpenTemplate(type) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PrintTemplateCardMaterial(
    onOpenGallery: () -> Unit,
    onOpenTemplate: (PaperType) -> Unit,
) {
    SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            SegmentedListItem(
                onClick = onOpenGallery,
                headlineContent = { Text(stringResource(R.string.print_template_section)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                },
            )
        }
        item {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(PRINT_TEMPLATE_EXAMPLES, key = { it.key }) { type ->
                        TemplateExampleItem(
                            type = type,
                            name = type.label,
                            nameStyle = MaterialTheme.typography.bodySmall,
                            nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onOpenTemplate(type) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateExampleItem(
    type: PaperType,
    name: String,
    nameStyle: TextStyle,
    nameColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
            contentAlignment = Alignment.Center,
        ) {
            PaperThumb(type = type, modifier = Modifier.fillMaxHeight())
        }
        MiuixText(
            text = name,
            style = nameStyle,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}
