package com.hpprint.app.data.monitor

import android.content.Context
import android.print.PrintJobInfo
import android.print.PrintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.hpprint.app.data.ipp.PrinterIpp
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.model.hp.PrinterProbe
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.model.hp.SystemPrintJob
import com.hpprint.app.data.repository.PrintJobRepositoryImpl
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import com.hpprint.app.hpApp
import java.util.concurrent.ConcurrentHashMap

/**
 * 后台状态采集：定时轮询打印机的 IPP 状态与系统打印队列，
 * 把真实结果写入共享 Repository，驱动 UI 实时刷新。
 */
object StatusMonitor {

    private const val PRINTER_INTERVAL_MS = 5_000L
    private const val JOB_INTERVAL_MS = 3_000L
    private const val OFFLINE_AFTER_FAILURES = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 每台打印机连续探测失败的次数，用于「忙碌容忍」：忙时偶发一次失败不判离线。
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                runCatching { refreshPrinters() }
                delay(PRINTER_INTERVAL_MS)
            }
        }
        scope.launch {
            while (isActive) {
                runCatching { refreshJobs() }
                delay(JOB_INTERVAL_MS)
            }
        }
    }

    suspend fun refreshPrinters() {
        val repo = PrinterRepositoryImpl.getInstance()
        repo.printers.value.forEach { p ->
            val probe = PrinterIpp.probe(p.ip, p.port, scheme = p.scheme, timeoutMs = 5000)
            if (probe.reachable) {
                consecutiveFailures.remove(p.id)
                repo.updateProbe(p.id, probe)
            } else {
                val count = (consecutiveFailures[p.id] ?: 0) + 1
                consecutiveFailures[p.id] = count
                // 忙碌容忍：连续失败达到阈值才降级为离线，避免打印时打印机短暂无响应被误判。
                if (count >= OFFLINE_AFTER_FAILURES) {
                    repo.updateProbe(p.id, PrinterProbe(reachable = false, status = PrinterStatus.OFFLINE))
                }
            }
        }
    }

    suspend fun refreshJobs() {
        val system = querySystemPrintJobs()
        PrintJobRepositoryImpl.getInstance().reconcileSystemJobs(system)
    }

    private fun querySystemPrintJobs(): List<SystemPrintJob> {
        val pm = hpApp.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return emptyList()
        val jobs = runCatching { pm.printJobs }.getOrDefault(emptyList())
        return jobs.mapNotNull { job ->
            val info = job.info
            val status = mapState(info.state) ?: return@mapNotNull null
            SystemPrintJob(label = info.label?.toString() ?: "", status = status)
        }
    }

    private fun mapState(state: Int): PrintJobStatus? = when (state) {
        PrintJobInfo.STATE_CREATED, PrintJobInfo.STATE_QUEUED -> PrintJobStatus.QUEUED
        PrintJobInfo.STATE_STARTED, PrintJobInfo.STATE_BLOCKED -> PrintJobStatus.PRINTING
        PrintJobInfo.STATE_COMPLETED -> PrintJobStatus.COMPLETED
        PrintJobInfo.STATE_FAILED -> PrintJobStatus.FAILED
        PrintJobInfo.STATE_CANCELED -> PrintJobStatus.CANCELLED
        else -> null
    }
}