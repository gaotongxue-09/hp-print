package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.monitor.StatusMonitor
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import com.hpprint.app.ui.util.NsdPrinterDiscovery

@Immutable
data class PrinterUiState(
    val printers: List<Printer> = emptyList(),
    val isDiscovering: Boolean = false,
    val discovered: List<Printer> = emptyList(),
)

class PrinterViewModel(
    private val repo: PrinterRepositoryImpl = PrinterRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.printers.collect { printers ->
                _uiState.update { it.copy(printers = printers) }
            }
        }
    }

    fun addManual(name: String, model: String, ip: String, port: Int) {
        viewModelScope.launch { repo.addManual(name, model, ip, port) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repo.setDefault(id) }
    }

    fun discover() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, discovered = emptyList()) }
            val found = NsdPrinterDiscovery.discover()
            repo.mergeDiscovered(found)
            _uiState.update { it.copy(isDiscovering = false, discovered = found) }
        }
    }

    fun probeAll() {
        viewModelScope.launch { StatusMonitor.refreshPrinters() }
    }
}