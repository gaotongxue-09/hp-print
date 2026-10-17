package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.StateFlow
import com.hpprint.app.data.model.hp.Scan

interface ScanRepository {
    val scans: StateFlow<List<Scan>>
    suspend fun save(title: String, imageUri: String): Scan
    suspend fun delete(id: String)
}