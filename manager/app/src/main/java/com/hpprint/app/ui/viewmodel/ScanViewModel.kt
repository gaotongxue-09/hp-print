package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.hpprint.app.data.model.hp.Scan
import com.hpprint.app.data.repository.ScanRepositoryImpl

@Immutable
data class ScanUiState(
    val scans: List<Scan> = emptyList(),
)

class ScanViewModel(
    private val repo: ScanRepositoryImpl = ScanRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.scans.collect { scans ->
                _uiState.value = ScanUiState(scans = scans.sortedByDescending { it.createdAt })
            }
        }
    }

    fun save(title: String, imageUri: String) {
        viewModelScope.launch { repo.save(title, imageUri) }
    }

    fun delete(scan: Scan) {
        viewModelScope.launch { repo.delete(scan.id) }
    }
}