package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.StateFlow
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.model.hp.PrinterProbe

interface PrinterRepository {
    val printers: StateFlow<List<Printer>>
    fun byId(id: String): Printer?
    fun default(): Printer?
    suspend fun addManual(name: String, model: String, ip: String, port: Int): Printer
    suspend fun remove(id: String)
    suspend fun setDefault(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun updateProbe(id: String, probe: PrinterProbe)
    fun mergeDiscovered(items: List<Printer>)
}