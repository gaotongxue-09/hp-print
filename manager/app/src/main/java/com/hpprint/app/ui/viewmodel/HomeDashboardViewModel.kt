package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.monitor.StatusMonitor
import com.hpprint.app.data.repository.PrintJobRepositoryImpl
import com.hpprint.app.data.repository.PrinterRepositoryImpl

@Immutable
data class HomeDashboardUiState(
    val defaultPrinter: Printer? = null,
    val allPrinters: List<Printer> = emptyList(),
    val allPrintersEmpty: Boolean = false,
    val recentJobs: List<PrintJob> = emptyList(),
)

class HomeDashboardViewModel(
    private val printerRepo: PrinterRepositoryImpl = PrinterRepositoryImpl.getInstance(),
    private val jobRepo: PrintJobRepositoryImpl = PrintJobRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeDashboardUiState())
    val uiState: StateFlow<HomeDashboardUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            combine(printerRepo.printers, jobRepo.jobs) { p, j -> p to j }
                .collect { (printers, jobs) ->
                    _uiState.value = HomeDashboardUiState(
                        defaultPrinter = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull(),
                        allPrinters = printers,
                        allPrintersEmpty = printers.isEmpty(),
                        recentJobs = jobs.sortedByDescending { it.createdAt }.take(5),
                    )
                }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { printerRepo.setDefault(id) }
    }

    fun renamePrinter(id: String, name: String) {
        viewModelScope.launch { printerRepo.rename(id, name) }
    }

    /** 手动刷新所有打印机的实时状态。 */
    fun refreshPrinters() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                StatusMonitor.refreshPrinters()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}