package com.hpprint.app.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val title: String,
    val appName: String,
    val versionName: String,
    val developerName: String,
    val developerUrl: String,
    val githubUrl: String,
    val coolapkUrl: String,
    val authorAvatar: Int,
    val references: List<ReferenceInfo>,
)

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    val onOpenLink: (String) -> Unit,
)