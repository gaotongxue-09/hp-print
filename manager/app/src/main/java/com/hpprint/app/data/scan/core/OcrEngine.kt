package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * 离线文字识别。模型（中文 + 拉丁字母）随 APK 内置，全程不联网。
 *
 * 注意：ML Kit 的 Task 不能在主线程 await，所有调用都必须放在后台线程。
 */
object OcrEngine {

    private const val MAX_SIDE = 2200
    private const val TIMEOUT_SEC = 60

    /** [angle] 为整行的倾角（度，逆时针为正），[confidence] 为 0..1 的置信度 */
    class Line(
        val text: String,
        val box: RectF?,
        val height: Float,
        val angle: Float = 0f,
        val confidence: Float = 0f
    )

    class PageResult(val lines: List<Line>, val text: String) {
        val isEmpty: Boolean get() = text.isBlank()
    }

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别一页，返回按阅读顺序重排后的文本 */
    fun recognize(bitmap: Bitmap): PageResult {
        val lines = lines(bitmap)
        return PageResult(lines, layoutText(lines))
    }

    /**
     * 只识别、不重排，保留每行的倾角与置信度。
     * 证件朝向判断要靠倾角区分「正立 / 倒置」。
     */
    fun lines(bitmap: Bitmap): List<Line> {
        val target = scaleForOcr(bitmap)
        return try {
            val image = InputImage.fromBitmap(target, 0)
            val result = Tasks.await(recognizer.process(image), TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
            val out = ArrayList<Line>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val t = line.text.trim()
                    if (t.isEmpty()) continue
                    val b = line.boundingBox
                    out.add(
                        Line(
                            t,
                            b?.let { RectF(it) },
                            (b?.height() ?: 0).toFloat(),
                            line.angle,
                            line.confidence
                        )
                    )
                }
            }
            out
        } catch (e: Throwable) {
            emptyList()
        } finally {
            if (target !== bitmap && !target.isRecycled) target.recycle()
        }
    }

    /**
     * 按几何位置重排成段落：
     * 同一基线的片段合成一行（多列排版也能正确拼回），
     * 行距明显大于「常规行距」时才当作段落间隔，避免每行都空一行。
     */
    private fun layoutText(lines: List<Line>): String {
        if (lines.isEmpty()) return ""
        val sorted = lines.sortedWith(
            compareBy({ it.box?.top ?: 0f }, { it.box?.left ?: 0f })
        )

        class Row(val items: MutableList<Line>) {
            var top = Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            fun height(): Float = if (bottom > top) bottom - top else 0f
        }

        // 1. 合并到同一文本行
        val rows = ArrayList<Row>()
        for (line in sorted) {
            val b = line.box
            if (b == null) {
                rows.add(Row(mutableListOf(line)))
                continue
            }
            val h = if (b.height() > 0f) b.height() else line.height.coerceAtLeast(1f)
            val center = b.centerY()
            val hit = rows.lastOrNull { r ->
                val rh = if (r.height() > 0f) r.height() else h
                center >= r.top - rh * 0.45f && center <= r.bottom + rh * 0.45f
            }
            if (hit != null) {
                hit.items.add(line)
                hit.top = minOf(hit.top, b.top)
                hit.bottom = maxOf(hit.bottom, b.bottom)
            } else {
                rows.add(Row(mutableListOf(line)).also {
                    it.top = b.top
                    it.bottom = b.bottom
                })
            }
        }

        // 2. 用中位数估计常规行距，据此判断段落间隔
        val gaps = ArrayList<Float>()
        for (i in 0 until rows.size - 1) {
            val g = rows[i + 1].top - rows[i].bottom
            if (g >= 0f && rows[i].height() > 0f && rows[i + 1].height() > 0f) gaps.add(g)
        }
        val medianGap = median(gaps)
        val medianHeight = median(rows.map { it.height() }.filter { it > 0f })
        val paraGap = maxOf(medianGap * 2.0f, medianGap + medianHeight * 0.7f, medianHeight * 1.1f)

        // 3. 逐行拼接
        val out = StringBuilder()
        for ((index, row) in rows.withIndex()) {
            if (index > 0) {
                val g = rows[index].top - rows[index - 1].bottom
                out.append(if (g >= paraGap) "\n\n" else "\n")
            }
            out.append(rowText(row.items))
        }
        return cleanup(out.toString())
    }

    private fun rowText(items: List<Line>): String {
        val ordered = items.sortedBy { it.box?.left ?: 0f }
        val sb = StringBuilder()
        var prevRight = -1f
        for ((i, part) in ordered.withIndex()) {
            if (i > 0) {
                val left = part.box?.left ?: prevRight
                val gap = if (prevRight >= 0f) left - prevRight else 0f
                val unit = part.height.takeIf { it > 0f } ?: 1f
                if (!isCjkBoundary(ordered[i - 1].text.last(), part.text.first()) && gap > unit * 0.35f) {
                    sb.append(' ')
                }
            }
            sb.append(part.text)
            prevRight = part.box?.right ?: prevRight
        }
        return sb.toString()
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 0) (s[mid - 1] + s[mid]) / 2f else s[mid]
    }

    private fun isCjkBoundary(a: Char?, b: Char?): Boolean =
        a != null && b != null && isCjk(a) && isCjk(b)

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
            (code in 0x3000..0x303F) || (code in 0xFF00..0xFFEF)
    }

    /** 清理中文之间被误插的空格、多余空行 */
    private fun cleanup(raw: String): String {
        val cjk = "\\u4e00-\\u9fa5\\u3000-\\u303f\\uff00-\\uffef"
        var s = raw
            .replace(Regex("[$cjk][ \t]+(?=[$cjk])"), { m -> m.value.trimEnd() })
            .replace(Regex("[ \t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
        s = s.lines().joinToString("\n") { it.trimEnd() }.trim()
        return s
    }

    /** 过长才缩小，短于 900 才放大；已在这个区间内的保持原样，避免二次重采样损失清晰度 */
    private fun scaleForOcr(src: Bitmap): Bitmap {
        val long = maxOf(src.width, src.height)
        if (long in 900..MAX_SIDE) return src
        val k = if (long < 900) 1600f / long else MAX_SIDE.toFloat() / long
        val w = (src.width * k).toInt().coerceAtLeast(1)
        val h = (src.height * k).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Throwable) {
        }
    }
}
