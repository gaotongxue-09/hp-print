package com.hpprint.app.data.model.hp

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Scan(
    val id: String,
    val title: String,
    val imageUri: String = "",
    val createdAt: Long = 0L,
)