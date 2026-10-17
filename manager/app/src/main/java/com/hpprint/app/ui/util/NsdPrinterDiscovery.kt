package com.hpprint.app.ui.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.model.hp.PrinterStatus
import com.hpprint.app.data.model.hp.PrinterType
import com.hpprint.app.hpApp
import kotlin.coroutines.resume

/**
 * mDNS/NSD 打印机发现：
 * 同时监听 _ipps._tcp / _ipp._tcp（IPP）与 _pdl-datastream._tcp（PDL）三类服务，
 * 逐台 resolve 出 IP 后按 IP 去重，同一 IP 优先保留 IPP 类型，
 * 避免同一台打印机因广播多个服务类型而重复显示。
 */
object NsdPrinterDiscovery {

    private const val DISCOVER_TIMEOUT_MS = 6_000L
    private const val RESOLVE_TIMEOUT_MS = 4_000L

    private val SERVICE_TYPES = listOf(
        "_ipps._tcp.",
        "_ipp._tcp.",
        "_pdl-datastream._tcp.",
    )

    // resolveService 需串行调用，避免部分设备报 ALREADY_ACTIVE。
    private val resolveMutex = Mutex()

    suspend fun discover(): List<Printer> = withContext(Dispatchers.IO) {
        val nsd = hpApp.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@withContext emptyList()

        val found = mutableListOf<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found += serviceInfo }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            SERVICE_TYPES.forEach { type ->
                runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
            }
            delay(DISCOVER_TIMEOUT_MS)
        } finally {
            SERVICE_TYPES.forEach { type ->
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
        }

        val resolved = mutableListOf<Printer>()
        found.toList().distinctBy { it.serviceType to it.serviceName }.forEach { info ->
            resolveOne(nsd, info)?.let { resolved += it }
        }
        dedupByIp(resolved)
    }

    private suspend fun resolveOne(nsd: NsdManager, info: NsdServiceInfo): Printer? =
        resolveMutex.withLock {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(serviceInfo)
                        }
                    }
                    runCatching { nsd.resolveService(info, listener) }
                }
            }
        }?.let { service ->
            val host = service.host?.hostAddress ?: return@let null
            val isIpp = service.serviceType.startsWith("_ipp")
            val scheme = if (service.serviceType.startsWith("_ipps")) "https" else "http"
            Printer(
                id = "nsd-${service.serviceType.substringBefore('.')}-$host-${service.port}",
                name = service.serviceName.ifBlank { "HP Printer" },
                model = service.attributes?.get("ty")?.let { String(it, Charsets.UTF_8) } ?: "",
                ip = host,
                hostname = service.host?.hostName ?: "",
                port = if (isIpp) service.port else 631,
                scheme = scheme,
                type = if (isIpp) PrinterType.NSD_IPP else PrinterType.NSD_PDL,
                status = PrinterStatus.ONLINE,
            )
        }

    private fun dedupByIp(items: List<Printer>): List<Printer> =
        items.groupBy { it.ip }
            .map { (_, list) -> list.firstOrNull { it.type == PrinterType.NSD_IPP } ?: list.first() }
}