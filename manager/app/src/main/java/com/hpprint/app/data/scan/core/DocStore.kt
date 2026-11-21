package com.hpprint.app.data.scan.core

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一页扫描件：保存的是矫正后未加滤镜的原图，滤镜在显示/导出时应用 */
class Page(val file: File, var filter: Int = Filters.ORIGINAL)

class Doc(
    val id: String,
    var name: String,
    val created: Long,
    val pages: MutableList<Page> = mutableListOf(),
    /** 文档类型：普通文档扫描 / 身份证复印，用于扫描历史分类。 */
    val type: String = TYPE_DOC,
) {
    companion object {
        const val TYPE_DOC = "doc"
        const val TYPE_IDCARD = "idcard"
    }
}

/**
 * 本地文档仓库，全部数据存在应用私有目录，不依赖账号或云服务。
 * 目录结构：files/scans/<docId>/page_x.jpg + files/scans/index.json
 */
class DocStore private constructor(private val context: Context) {

    private val root = File(context.filesDir, "scans").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private var cache: MutableList<Doc>? = null

    companion object {
        @Volatile
        private var instance: DocStore? = null

        fun get(context: Context): DocStore =
            instance ?: synchronized(this) {
                instance ?: DocStore(context.applicationContext).also { instance = it }
            }

        fun defaultName(): String {
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            return "扫描 " + fmt.format(Date())
        }
    }

    fun docs(): MutableList<Doc> {
        cache?.let { return it }
        val list = mutableListOf<Doc>()
        if (indexFile.exists()) {
            try {
                val arr = JSONArray(indexFile.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val name = o.optString("name", "扫描文档")
                    val created = o.optLong("created", System.currentTimeMillis())
                    val type = o.optString("type", Doc.TYPE_DOC)
                    val doc = Doc(id, name, created, type = type)
                    val pages = o.optJSONArray("pages") ?: JSONArray()
                    for (j in 0 until pages.length()) {
                        val po = pages.getJSONObject(j)
                        val f = File(root, po.getString("file"))
                        if (f.exists()) {
                            doc.pages.add(Page(f, po.optInt("filter", Filters.ORIGINAL)))
                        }
                    }
                    if (doc.pages.isNotEmpty()) list.add(doc)
                }
            } catch (e: Exception) {
                // 索引损坏时忽略，重新开始
            }
        }
        list.sortByDescending { it.created }
        cache = list
        return list
    }

    fun persist() {
        val list = cache ?: return
        val arr = JSONArray()
        for (d in list) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("name", d.name)
            o.put("created", d.created)
            o.put("type", d.type)
            val pages = JSONArray()
            for (p in d.pages) {
                val po = JSONObject()
                po.put("file", p.file.relativeTo(root).path.replace('\\', '/'))
                po.put("filter", p.filter)
                pages.put(po)
            }
            o.put("pages", pages)
            arr.put(o)
        }
        try {
            indexFile.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    fun createDoc(name: String = defaultName(), type: String = Doc.TYPE_DOC): Doc {
        val id = "d" + System.currentTimeMillis()
        File(root, id).mkdirs()
        val doc = Doc(id, name, System.currentTimeMillis(), type = type)
        docs().add(0, doc)
        persist()
        return doc
    }

    fun findDoc(id: String): Doc? = docs().firstOrNull { it.id == id }

    /** 文档目录（图片 + 识别结果都在里面，删除文档时一并清掉） */
    fun docDir(doc: Doc): File = File(root, doc.id).apply { mkdirs() }

    fun readText(doc: Doc): String? {
        val f = File(docDir(doc), "ocr.txt")
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    fun writeText(doc: Doc, text: String) {
        runCatching { File(docDir(doc), "ocr.txt").writeText(text) }
    }

    fun clearText(doc: Doc) {
        runCatching { File(docDir(doc), "ocr.txt").delete() }
    }

    fun addPage(doc: Doc, bitmap: Bitmap): Page? {
        val dir = File(root, doc.id).apply { mkdirs() }
        var n = doc.pages.size + 1
        var f = File(dir, "page_$n.jpg")
        while (f.exists()) {
            n++
            f = File(dir, "page_$n.jpg")
        }
        if (!BitmapUtil.saveJpeg(bitmap, f)) return null
        val page = Page(f, Filters.ORIGINAL)
        doc.pages.add(page)
        persist()
        return page
    }

    /** 插入到指定位置（用于重新排序/重拍） */
    fun replacePage(doc: Doc, index: Int, bitmap: Bitmap): Page? {
        val dir = File(root, doc.id).apply { mkdirs() }
        val f = File(dir, "page_${System.currentTimeMillis()}.jpg")
        if (!BitmapUtil.saveJpeg(bitmap, f)) return null
        val old = doc.pages.getOrNull(index)
        val page = Page(f, old?.filter ?: Filters.ORIGINAL)
        if (index in doc.pages.indices) {
            doc.pages[index] = page
            old?.file?.delete()
        } else {
            doc.pages.add(page)
        }
        persist()
        return page
    }

    /** 用外部图片文件替换某一页（手动裁切后用），保留原来的滤镜设置 */
    fun replacePageFile(doc: Doc, index: Int, src: File): Page? {
        if (index !in doc.pages.indices) return null
        if (!src.exists() || src.length() == 0L) return null
        val dir = File(root, doc.id).apply { mkdirs() }
        val f = File(dir, "page_${System.currentTimeMillis()}.jpg")
        try {
            src.copyTo(f, overwrite = true)
            src.delete()
        } catch (e: Exception) {
            f.delete()
            return null
        }
        val old = doc.pages[index]
        val page = Page(f, old.filter)
        doc.pages[index] = page
        old.file.delete()
        persist()
        return page
    }

    fun removePage(doc: Doc, index: Int) {
        val p = doc.pages.getOrNull(index) ?: return
        doc.pages.removeAt(index)
        p.file.delete()
        persist()
    }

    fun movePage(doc: Doc, from: Int, to: Int) {
        if (from !in doc.pages.indices || to !in doc.pages.indices) return
        val p = doc.pages.removeAt(from)
        doc.pages.add(to, p)
        persist()
    }

    fun renameDoc(doc: Doc, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        doc.name = trimmed
        persist()
    }

    fun deleteDoc(doc: Doc) {
        doc.pages.forEach { it.file.delete() }
        File(root, doc.id).deleteRecursively()
        docs().remove(doc)
        persist()
    }
}
