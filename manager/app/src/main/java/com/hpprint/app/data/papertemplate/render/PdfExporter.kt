package com.hpprint.app.data.papertemplate.render

import android.graphics.pdf.PdfDocument
import com.hpprint.app.data.papertemplate.model.PaperConfig
import com.hpprint.app.data.papertemplate.model.PaperType
import java.io.OutputStream

/** PDF 导出：复用 PaperRenderer 的绘制引擎，输出矢量 PDF */
object PdfExporter {

    // 1mm 对应的 PDF point 数（PDF 标准 72 DPI）
    private const val POINTS_PER_MM = 72f / 25.4f

    fun export(type: PaperType, config: PaperConfig, out: OutputStream): Int {
        val widthPx = (config.contentWidthMm * POINTS_PER_MM).toInt()
        val heightPx = (config.contentHeightMm * POINTS_PER_MM).toInt()
        val pageCount = config.pageCountCoerced

        val doc = PdfDocument()
        try {
            for (page in 0 until pageCount) {
                val info = PdfDocument.PageInfo.Builder(widthPx, heightPx, page).create()
                val p = doc.startPage(info)
                PaperRenderer.draw(p.canvas, type, config)
                doc.finishPage(p)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return pageCount
    }
}
