package com.hpprint.app.data.scan.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import androidx.annotation.StringRes
import com.hpprint.app.R

/** 扫描清晰度档位，对应拍照输出像素上限（参考 LocalScan 的超清/高清/标清）。 */
enum class ScanTier(val mp: Int, @StringRes val labelRes: Int) {
    ULTRA(12, R.string.hp_scan_quality_ultra),
    HD(8, R.string.hp_scan_quality_hd),
    SD(5, R.string.hp_scan_quality_sd);

    companion object {
        fun fromStored(value: String?): ScanTier = entries.firstOrNull { it.name == value } ?: HD
    }
}

/** 相机拍照清晰度：枚举后摄 JPEG 输出尺寸并按档位挑选，持久化用户选择。 */
object ScanQuality {

    private const val PREFS = "scan_settings"
    private const val KEY_TIER = "pixel_tier"

    fun load(context: Context): ScanTier =
        ScanTier.fromStored(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TIER, null))

    fun save(context: Context, tier: ScanTier) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TIER, tier.name)
            .apply()
    }

    /** 该档位实际可用的 JPEG 尺寸；相机不可用或读取失败时返回 null（沿用系统默认分辨率）。 */
    fun sizeFor(context: Context, tier: ScanTier): Size? {
        val sizes = jpegSizes(context)
        if (sizes.isEmpty()) return null
        val target = tier.mp * 1_000_000L
        val pool = sizes.filter { isFourThree(it) }.ifEmpty { sizes }
        val under = pool
            .filter { it.width.toLong() * it.height <= target }
            .maxByOrNull { it.width.toLong() * it.height }
        if (under != null) return under
        // 传感器最大像素低于档位时，取能拿到的最小尺寸
        return pool.minByOrNull { it.width.toLong() * it.height }
    }

    /** 档位对应的「万像素」文案数字，例如 1200 表示 1200 万像素。 */
    fun megaPixels(context: Context, tier: ScanTier): Int {
        val size = sizeFor(context, tier) ?: return tier.mp * 100
        return (size.width.toLong() * size.height / 10_000L).toInt()
    }

    private fun isFourThree(size: Size): Boolean {
        val longSide = maxOf(size.width, size.height)
        val shortSide = minOf(size.width, size.height)
        if (shortSide == 0) return false
        val ratio = longSide.toFloat() / shortSide
        return kotlin.math.abs(ratio - 4f / 3f) < 0.02f
    }

    private fun jpegSizes(context: Context): List<Size> {
        return runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val backId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return emptyList()
            val configs = manager.getCameraCharacteristics(backId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return emptyList()
            configs.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }
}
