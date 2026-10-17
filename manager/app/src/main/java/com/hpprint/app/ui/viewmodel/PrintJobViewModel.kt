package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.repository.PrintJobRepositoryImpl
import com.hpprint.app.data.repository.PrinterRepositoryImpl

@Immutable
data class PrintJobUiState(
    val active: List<PrintJob> = emptyList(),
    val history: List<PrintJob> = emptyList(),
)

class PrintJobViewModel(
    private val jobRepo: PrintJobRepositoryImpl = PrintJobRepositoryImpl.getInstance(),
    private val printerRepo: PrinterRepositoryImpl = PrinterRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrintJobUiState())
    val uiState: StateFlow<PrintJobUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            jobRepo.jobs.collect { jobs ->
                val sorted = jobs.sortedByDescending { it.createdAt }
                _uiState.value = PrintJobUiState(
                    active = sorted.filter {
                        it.status == PrintJobStatus.QUEUED || it.status == PrintJobStatus.PRINTING
                    },
                    history = sorted.filter {
                        it.status == PrintJobStatus.COMPLETED ||
                            it.status == PrintJobStatus.FAILED ||
                            it.status == PrintJobStatus.CANCELLED
                    },
                )
            }
        }
    }

    fun printerName(id: String): String = printerRepo.byId(id)?.model ?: "-"

    fun cancel(job: PrintJob) {
        viewModelScope.launch { jobRepo.cancel(job.id) }
    }

    fun remove(job: PrintJob) {
        viewModelScope.launch { jobRepo.remove(job.id) }
    }
}