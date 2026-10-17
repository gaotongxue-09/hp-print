package com.hpprint.app.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors

@Composable
fun AboutScreenMaterial(
    state: AboutUiState,
    actions: AboutScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1f)
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = state.appName,
                        fontWeight = FontWeight.Medium,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                    )
                    Text(
                        text = state.versionName,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                }
            }
            item {
                val haptic = LocalHapticFeedback.current
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // 开发者（头像 + 高同学 → GitHub）
                    SegmentedColumn(
                        content = listOf {
                            SegmentedListItem(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    actions.onOpenLink(state.developerUrl)
                                },
                                leadingContent = {
                                    Image(
                                        painter = painterResource(state.authorAvatar),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(20.dp)),
                                    )
                                },
                                headlineContent = { Text(state.developerName) },
                                supportingContent = { Text(stringResource(R.string.hp_link_github)) },
                            )
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    // 链接（GitHub / 酷安）
                    SegmentedColumn(
                        content = listOf(
                            {
                                SegmentedListItem(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onOpenLink(state.githubUrl)
                                    },
                                    headlineContent = { Text(stringResource(R.string.hp_link_github)) },
                                    supportingContent = { Text(stringResource(R.string.hp_link_github_summary)) },
                                )
                            },
                            {
                                SegmentedListItem(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onOpenLink(state.coolapkUrl)
                                    },
                                    headlineContent = { Text(stringResource(R.string.hp_link_coolapk)) },
                                    supportingContent = { Text(stringResource(R.string.hp_link_coolapk_summary)) },
                                )
                            },
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    // 引用项目
                    SegmentedColumn(
                        content = state.references.map { ref ->
                            {
                                SegmentedListItem(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onOpenLink(ref.url)
                                    },
                                    headlineContent = { Text(ref.name) },
                                    supportingContent = { Text(ref.description) },
                                )
                            }
                        }
                    )
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                    WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
                        )
                    )
                }
            }
        }
    }
}
