package com.hpprint.app.ui.screen.about

import androidx.compose.runtime.Immutable

/** 一条引用项目信息（名称 + 一句介绍 + 链接）。 */
@Immutable
data class ReferenceInfo(
    val name: String,
    val description: String,
    val url: String,
)