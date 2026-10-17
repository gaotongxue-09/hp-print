package com.hpprint.app.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.hpprint.app.data.model.hp.PrintJob
import com.hpprint.app.data.model.hp.PrintJobKind
import com.hpprint.app.data.model.hp.PrintJobStatus
import com.hpprint.app.data.model.hp.Printer
import com.hpprint.app.data.repository.PrintJobRepositoryImpl
import com.hpprint.app.data.repository.PrinterRepositoryImpl
import com.hpprint.app.hpApp
import com.hpprint.app.ui.util.ManualDuplexController
import com.hpprint.app.ui.util.ManualDuplexResult
import com.hpprint.app.ui.util.PrintDispatcher
import com.hpprint.app.ui.util.PrintSpec

val PAPER_SIZES = listOf("A4", "A3", "A5", "B5", "Letter", "Legal", "Tabloid")
val COLOR_MODES = listOf("color", "grayscale")
val DUPLEX_MODES = listOf("off", "long", "short", "manual")
val QUALITY_OPTIONS = listOf(3, 4, 5)      // 草稿 / 标准 / 高质量
val ORIENTATION_OPTIONS = listOf(3, 4)     // 纵向 / 横向
val PAGE_RANGE_MODES = listOf("all", "custom")   // 全部页面 / 自定义范围

@Immutable
data class PrintSetupUiState(
    val printers: List<Printer> = emptyList(),
    val selectedPrinter: Printer? = null,
    val copies: Int = 1,
    val colorMode: String = "color",
    val paperSize: String = "A4",
    val duplex: String = "off",
    val quality: Int = 4,
    val orientation: Int = 3,
    val pageRange: String = "all",
    val pageStart: Int = 1,
    val pageEnd: Int = 1,
    val isDispatching: Boolean = false,
    val showManualDuplexGuide: Boolean = false,
)

class PrintSetupViewModel(
    private val printerRepo: PrinterRepositoryImpl = PrinterRepositoryImpl.getInstance(),
    private val jobRepo: PrintJobRepositoryImpl = PrintJobRepositoryImpl.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrintSetupUiState())
    val uiState: StateFlow<PrintSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            printerRepo.printers.collect { printers ->
                val def = printers.firstOrNull { it.isDefault } ?: printers.firstOrNull()
                _uiState.update { it.copy(printers = printers, selectedPrinter = def ?: it.selectedPrinter) }
            }
        }
    }

    fun selectPrinter(id: String) {
        _uiState.update { state ->
            state.copy(selectedPrinter = state.printers.firstOrNull { it.id == id })
        }
    }

    fun setCopies(value: Int) = _uiState.update { it.copy(copies = value.coerceIn(1, 99)) }
    fun setColorMode(value: String) = _uiState.update { it.copy(colorMode = value) }
    fun setPaperSize(value: String) = _uiState.update { it.copy(paperSize = value) }
    fun setDuplex(value: String) = _uiState.update { it.copy(duplex = value) }
    fun setQuality(value: Int) = _uiState.update { it.copy(quality = value) }
    fun setOrientation(value: Int) = _uiState.update { it.copy(orientation = value) }
    fun setPageRange(value: String) = _uiState.update { it.copy(pageRange = value) }
    fun setPageStart(value: Int) = _uiState.update { it.copy(pageStart = value.coerceIn(1, 99999)) }
    fun setPageEnd(value: Int) = _uiState.update { it.copy(pageEnd = value.coerceIn(1, 99999)) }

    private var guideGate: CompletableDeferred<Boolean>? = null

    /** 挂起当前协程，直到用户在手动双面「使用说明」中点击开始（true）或取消（false）。 */
    private suspend fun awaitManualGuide(): Boolean {
        val gate = CompletableDeferred<Boolean>()
        guideGate = gate
        _uiState.update { it.copy(showManualDuplexGuide = true) }
        try {
            return gate.await()
        } finally {
            _uiState.update { it.copy(showManualDuplexGuide = false) }
            guideGate = null
        }
    }

    fun confirmManualDuplexGuide() {
        guideGate?.complete(true)
    }

    fun cancelManualDuplexGuide() {
        guideGate?.complete(false)
    }

    fun dispatch(
        titles: List<String>,
        kinds: List<PrintJobKind>,
        sourceUris: List<String>,
        webUrl: String = "",
        onDispatched: () -> Unit = {},
    ) {
        val state = _uiState.value
        val printer = state.selectedPrinter ?: return
        val isManual = state.duplex == "manual"

        _uiState.update { it.copy(isDispatching = true) }

        val jobs = titles.mapIndexed { i, title ->
            PrintJob(
                id = "job-${System.currentTimeMillis()}-$i",
                title = title,
                printerId = printer.id,
                kind = kinds.getOrNull(i) ?: PrintJobKind.PDF,
                sourceUri = sourceUris.getOrNull(i) ?: "",
                copies = state.copies,
                colorMode = state.colorMode,
                paperSize = state.paperSize,
                duplex = state.duplex,
                status = PrintJobStatus.QUEUED,
                createdAt = System.currentTimeMillis(),
            )
        }

        fun specFor(job: PrintJob) = PrintSpec(
            title = job.title,
            kind = job.kind,
            sourceUri = job.sourceUri,
            webUrl = webUrl,
            copies = job.copies,
            colorMode = job.colorMode,
            paperSize = job.paperSize,
            duplex = job.duplex,
            quality = state.quality,
            orientation = state.orientation,
            pageRange = state.pageRange,
            pageStart = state.pageStart,
            pageEnd = state.pageEnd,
            printerIp = printer.ip,
            printerPort = printer.port,
            printerScheme = printer.scheme,
        )

        viewModelScope.launch {
            jobs.forEach { jobRepo.enqueue(it) }

            if (isManual) {
                // 手动双面：打印开始前先弹「使用说明」；用户在说明中取消则整批标记取消。
                val proceed = awaitManualGuide()
                if (!proceed) {
                    jobs.forEach { jobRepo.updateStatus(it.id, PrintJobStatus.CANCELLED) }
                    _uiState.update { it.copy(isDispatching = false) }
                    return@launch
                }
                // 请求通知权限（拒绝则静默降级为仅首页弹窗）
                ManualDuplexController.requestNotificationPermission()
                // 交给进程级后台协调器执行，立即返回首页，不再困在打印页；
                // 奇数页在后台打印，翻面提示改由首页承担。
                jobs.forEach { jobRepo.updateStatus(it.id, PrintJobStatus.PRINTING) }
                ManualDuplexController.start(hpApp, jobs.map { specFor(it) }) { i, result ->
                    jobRepo.updateStatus(
                        jobs[i].id,
                        when (result) {
                            ManualDuplexResult.SUCCESS -> PrintJobStatus.COMPLETED
                            ManualDuplexResult.CANCELLED -> PrintJobStatus.CANCELLED
                            ManualDuplexResult.FAILED -> PrintJobStatus.FAILED
                        },
                    )
                }
                _uiState.update { it.copy(isDispatching = false) }
                onDispatched()
            } else {
                // 普通打印：先启动后台直连打印，再返回上一页；网络耗时不再阻塞 UI
                jobs.forEach { jobRepo.updateStatus(it.id, PrintJobStatus.PRINTING) }
                PrintDispatcher.printListAsync(hpApp, jobs.map { specFor(it) }) { i, sent ->
                    jobRepo.updateStatus(
                        jobs[i].id,
                        if (sent) PrintJobStatus.COMPLETED else PrintJobStatus.FAILED,
                    )
                }
                _uiState.update { it.copy(isDispatching = false) }
                onDispatched()
            }
        }
    }
}