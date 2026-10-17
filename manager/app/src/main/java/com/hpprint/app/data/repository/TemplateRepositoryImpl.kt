package com.hpprint.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.data.demo.DemoData
import com.hpprint.app.data.model.TemplateInfo

/**
 * 纯 UI 化改造：不再通过 ksud 命令或网络读写 App Profile 模板。
 * 直接返回演示模板。
 */
class TemplateRepositoryImpl : TemplateRepository {

    override suspend fun getTemplates(sync: Boolean): Result<List<TemplateInfo>> =
        withContext(Dispatchers.IO) {
            runCatching { DemoData.templates }
        }

    override suspend fun importTemplates(jsonString: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            Result.success(Unit)
        }

    override suspend fun exportTemplates(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { DemoData.templates.firstOrNull()?.toJSON()?.toString() ?: "[]" }
    }

    override suspend fun getTemplate(id: String): Result<TemplateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                DemoData.templates.firstOrNull { it.id == id }
                    ?: throw Exception("Template not found: $id")
            }
        }
}