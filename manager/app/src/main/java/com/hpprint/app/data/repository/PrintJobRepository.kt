package com.hpprint.app.data.repository

import kotlinx.coroutines.flow.StateFlow
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.model.hp.SystemPrintJob

interface PrintJobRepository {
    val jobs: StateFlow<List<PrintJob>>
    fun byId(id: String): PrintJob?
    suspend fun enqueue(job: PrintJob): PrintJob
    suspend fun updateStatus(id: String, status: PrintJobStatus)
    suspend fun cancel(id: String)
    suspend fun remove(id: String)
    suspend fun reconcileSystemJobs(systemJobs: List<SystemPrintJob>)
}