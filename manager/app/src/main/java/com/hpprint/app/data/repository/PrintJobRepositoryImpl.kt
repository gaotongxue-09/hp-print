package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.model.hp.SystemPrintJob
import com.hpprint.app.data.store.JsonFileStore

class PrintJobRepositoryImpl private constructor() : PrintJobRepository {

    private val _jobs = MutableStateFlow(load())
    override val jobs: StateFlow<List<PrintJob>> = _jobs.asStateFlow()

    private fun load(): List<PrintJob> =
        JsonFileStore.read<List<PrintJob>>("jobs", emptyList())

    private fun persist(list: List<PrintJob>) = JsonFileStore.write("jobs", list)

    private fun commit(list: List<PrintJob>) {
        _jobs.value = list
        persist(list)
    }

    override fun byId(id: String): PrintJob? = _jobs.value.firstOrNull { it.id == id }

    override suspend fun enqueue(job: PrintJob): PrintJob {
        commit(listOf(job) + _jobs.value.filterNot { it.id == job.id })
        return job
    }

    override suspend fun updateStatus(id: String, status: PrintJobStatus) {
        commit(_jobs.value.map {
            if (it.id == id) {
                it.copy(
                    status = status,
                    finishedAt = if (status == PrintJobStatus.COMPLETED ||
                        status == PrintJobStatus.FAILED ||
                        status == PrintJobStatus.CANCELLED
                    ) System.currentTimeMillis() else 0L,
                )
            } else it
        })
    }

    override suspend fun cancel(id: String) = updateStatus(id, PrintJobStatus.CANCELLED)

    override suspend fun remove(id: String) {
        commit(_jobs.value.filterNot { it.id == id })
    }

    override suspend fun reconcileSystemJobs(systemJobs: List<SystemPrintJob>) {
        if (systemJobs.isEmpty()) return
        val byLabel = systemJobs.associateBy { it.label }
        var changed = false
        val updated = _jobs.value.map { job ->
            val terminal = job.status == PrintJobStatus.COMPLETED ||
                job.status == PrintJobStatus.FAILED ||
                job.status == PrintJobStatus.CANCELLED
            if (terminal) {
                job
            } else {
                val sys = byLabel[job.title]
                if (sys != null && sys.status != job.status) {
                    changed = true
                    job.copy(
                        status = sys.status,
                        finishedAt = if (sys.status == PrintJobStatus.COMPLETED ||
                            sys.status == PrintJobStatus.FAILED ||
                            sys.status == PrintJobStatus.CANCELLED
                        ) System.currentTimeMillis() else 0L,
                    )
                } else job
            }
        }
        if (changed) commit(updated)
    }

    companion object {
        @Volatile private var INSTANCE: PrintJobRepositoryImpl? = null
        fun getInstance(): PrintJobRepositoryImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrintJobRepositoryImpl().also { INSTANCE = it }
            }
    }
}