package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hpprint.app.data.ipp.PrinterIpp
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.repository.PrinterRepositoryImpl

@Immutable
data class PrinterDetailUiState(
    val printer: Printer? = null,
    val isProbing: Boolean = false,
)

class PrinterDetailViewModel(
    private val repo: PrinterRepositoryImpl = PrinterRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterDetailUiState())
    val uiState: StateFlow<PrinterDetailUiState> = _uiState.asStateFlow()

    private var printerId: String? = null

    init {
        viewModelScope.launch {
            repo.printers.collect { printers ->
                val pid = printerId
                _uiState.update { it.copy(printer = pid?.let { id -> printers.firstOrNull { p -> p.id == id } }) }
            }
        }
    }

    fun load(id: String) {
        printerId = id
        viewModelScope.launch {
            _uiState.update { it.copy(printer = repo.byId(id)) }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repo.setDefault(id) }
    }

    fun remove(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.remove(id)
            onDone()
        }
    }

    fun probe(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true) }
            val printer = repo.byId(id)
            if (printer != null) {
                repo.updateProbe(id, PrinterIpp.probe(printer.ip, printer.port, scheme = printer.scheme))
            }
            _uiState.update { it.copy(isProbing = false) }
        }
    }
}