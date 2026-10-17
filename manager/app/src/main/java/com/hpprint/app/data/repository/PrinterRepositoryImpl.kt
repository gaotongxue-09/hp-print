package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.model.hp.PrinterProbe
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.model.hp.PrinterType
import com.hpprint.app.data.store.JsonFileStore

class PrinterRepositoryImpl private constructor() : PrinterRepository {

    private val _printers = MutableStateFlow(load())
    override val printers: StateFlow<List<Printer>> = _printers.asStateFlow()

    private fun load(): List<Printer> {
        if (JsonFileStore.read<Boolean>("printers_demo_cleanup_v2", false)) {
            return JsonFileStore.read<List<Printer>>("printers", emptyList())
        }
        // 一次性清理：清空之前测试/演示遗留的所有打印机，只保留用户后续自己添加、连接的打印机。
        JsonFileStore.write("printers_demo_cleanup_v2", true)
        JsonFileStore.write("printers", emptyList<Printer>())
        return emptyList()
    }

    private fun persist(list: List<Printer>) = JsonFileStore.write("printers", list)

    private fun commit(list: List<Printer>) {
        _printers.value = list
        persist(list)
    }

    override fun byId(id: String): Printer? = _printers.value.firstOrNull { it.id == id }

    override fun default(): Printer? =
        _printers.value.firstOrNull { it.isDefault } ?: _printers.value.firstOrNull()

    override suspend fun addManual(name: String, model: String, ip: String, port: Int): Printer {
        val makeDefault = _printers.value.isEmpty()
        val printer = Printer(
            id = "manual-${System.currentTimeMillis()}",
            name = name,
            model = model,
            ip = ip,
            port = port,
            type = PrinterType.MANUAL,
            status = PrinterStatus.ONLINE,
            isDefault = makeDefault,
        )
        commit(_printers.value + printer)
        return printer
    }

    override suspend fun remove(id: String) {
        commit(_printers.value.filterNot { it.id == id })
    }

    override suspend fun setDefault(id: String) {
        commit(_printers.value.map { it.copy(isDefault = it.id == id) })
    }

    override suspend fun rename(id: String, name: String) {
        commit(_printers.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    override suspend fun updateProbe(id: String, probe: PrinterProbe) {
        var changed = false
        val updated = _printers.value.map {
            if (it.id == id) {
                val next = it.copy(
                    status = probe.status,
                    supplies = probe.supplies,
                    model = probe.model ?: it.model,
                    isColor = probe.isColor ?: it.isColor,
                    isDuplex = probe.isDuplex ?: it.isDuplex,
                )
                if (next != it) { changed = true; next } else it
            } else it
        }
        if (changed) commit(updated)
    }

    override fun mergeDiscovered(items: List<Printer>) {
        if (items.isEmpty()) return
        val existingIps = _printers.value.map { it.ip }.toSet()
        val newItems = items.filter { it.ip !in existingIps }
        if (newItems.isEmpty()) return
        commit(_printers.value + newItems)
    }

    companion object {
        @Volatile private var INSTANCE: PrinterRepositoryImpl? = null
        fun getInstance(): PrinterRepositoryImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrinterRepositoryImpl().also { INSTANCE = it }
            }
    }
}