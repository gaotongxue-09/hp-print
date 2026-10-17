package com.hpprint.app.data.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import com.hpprint.app.hpApp
import java.io.File

/**
 * 极简 JSON 文件持久化：把结构化数据写到 filesDir/hp_<name>.json。
 * 不引入 Room / DataStore，避免过度设计。
 */
object JsonFileStore {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun file(name: String): File = File(hpApp.filesDir, "hp_$name.json")

    inline fun <reified T> read(name: String, default: T): T {
        return runCatching {
            val f = file(name)
            if (!f.exists()) default else json.decodeFromString<T>(f.readText())
        }.getOrDefault(default)
    }

    inline fun <reified T> write(name: String, value: T) {
        runCatching {
            file(name).writeText(json.encodeToString(serializer<T>(), value))
        }
    }

    fun delete(name: String) {
        runCatching { file(name).delete() }
    }
}