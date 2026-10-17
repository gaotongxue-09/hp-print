package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hpprint.app.data.model.hp.Scan
import com.hpprint.app.data.store.JsonFileStore

class ScanRepositoryImpl private constructor() : ScanRepository {

    private val _scans = MutableStateFlow(load())
    override val scans: StateFlow<List<Scan>> = _scans.asStateFlow()

    private fun load(): List<Scan> =
        JsonFileStore.read<List<Scan>>("scans", emptyList())

    private fun persist(list: List<Scan>) = JsonFileStore.write("scans", list)

    private fun commit(list: List<Scan>) {
        _scans.value = list
        persist(list)
    }

    override suspend fun save(title: String, imageUri: String): Scan {
        val scan = Scan(
            id = "scan-${System.currentTimeMillis()}",
            title = title,
            imageUri = imageUri,
            createdAt = System.currentTimeMillis(),
        )
        commit(listOf(scan) + _scans.value)
        return scan
    }

    override suspend fun delete(id: String) {
        commit(_scans.value.filterNot { it.id == id })
    }

    companion object {
        @Volatile private var INSTANCE: ScanRepositoryImpl? = null
        fun getInstance(): ScanRepositoryImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanRepositoryImpl().also { INSTANCE = it }
            }
    }
}