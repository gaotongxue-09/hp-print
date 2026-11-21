package com.hpprint.app.data.scan.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.wibo.doc.jni.DocDetect
import com.wibo.doc.jni.DocDetectApi
import com.wibo.doc.jni.DocFiltersApi
import com.wibo.doc.jni.DocPoint
import com.wibo.doc.jni.DocToolsApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 原生扫描引擎门面：直接复用 LocalScan 的 libdoc_sdk_v1.6.2.1.so 与
 * v5.vaimlite / yolo_fp16.vaimlite 模型，负责「找四角 / 透视矫正 / 滤镜 / 证件圆角」。
 *
 * 原生库只在 arm64-v8a 提供；其他 ABI 或加载失败时 [available] 恒为 false，
 * 所有方法返回 null，调用方自动回退到现有纯 Kotlin 实现。
 */
object NativeScanEngine {

    private val initStarted = AtomicBoolean(false)
    private val lock = Any()

    private val detectApi = DocDetectApi()
    private val filtersApi = DocFiltersApi()
    private val toolsApi = DocToolsApi()

    @Volatile
    var available: Boolean = false
        private set

    /** 异步加载 so 与模型（幂等，不阻塞调用线程）。 */
    fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread({
            available = try {
                System.loadLibrary("doc_sdk_v1.6.2.1")
                detectApi.initModel(app)
            } catch (_: Throwable) {
                false
            }
        }, "native-scan-init").apply {
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    /** 相册/拍照整图检测：返回 TL、TR、BR、BL 四角（原图坐标），未检出返回 null。 */
    fun detectBitmap(bitmap: Bitmap): List<PointF>? {
        if (!available || bitmap.isRecycled) return null
        return synchronized(lock) {
            try {
                // 直接调用声明层，未检出时保留 null（Api 封装会把 null 变成整图四角）
                val raw = DocDetect.runDocDetect(bitmap, null, 1) ?: return null
                if (raw.size != 4) return null
                val pts = Array(raw.size) { raw[it] as DocPoint }
                DocDetect.adjustPoints(pts, bitmap.width, bitmap.height, 0)
                    .map { PointF(it.x, it.y) }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** 透视矫正：返回新 Bitmap，失败返回 null。 */
    fun rectify(bitmap: Bitmap, corners: List<PointF>): Bitmap? {
        if (!available || bitmap.isRecycled || corners.size != 4) return null
        return synchronized(lock) {
            try {
                val pts = corners.map { DocPoint(it.x, it.y) }.toTypedArray()
                detectApi.callJniDocRectifyAlbum(bitmap, pts)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** 应用滤镜（入参为 [Filters] 的常量），返回新 Bitmap，失败返回 null。 */
    fun filter(src: Bitmap, mode: Int): Bitmap? {
        if (!available || src.isRecycled) return null
        return synchronized(lock) {
            try {
                when (mode) {
                    Filters.ENHANCE -> {
                        val bmp = copy(src) ?: return null
                        filtersApi.callJniEnhanceImg(bmp)
                        bmp
                    }
                    Filters.GRAY -> {
                        val bmp = copy(src) ?: return null
                        filtersApi.callJniEnhanceGray(bmp)
                        bmp
                    }
                    Filters.BW -> {
                        val bmp = copy(src) ?: return null
                        filtersApi.callJniEnhanceImg(bmp)
                        filtersApi.callJniEnhanceGray(bmp)
                        bmp
                    }
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** 证件圆角（cornerMm 圆角半径、cardWidthMm 卡宽），返回新 Bitmap，失败返回 null。 */
    fun cardCorner(src: Bitmap, cornerMm: Double, cardWidthMm: Double): Bitmap? {
        if (!available || src.isRecycled) return null
        return synchronized(lock) {
            try {
                val bmp = copy(src) ?: return null
                toolsApi.callJniMakeCardCorner(bmp, cornerMm, cardWidthMm)
                bmp
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun copy(src: Bitmap): Bitmap? =
        if (src.isRecycled) null else src.copy(Bitmap.Config.ARGB_8888, true)
}
