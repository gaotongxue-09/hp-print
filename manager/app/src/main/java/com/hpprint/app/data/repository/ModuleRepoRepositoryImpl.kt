package com.hpprint.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.data.demo.DemoData
import com.hpprint.app.data.model.RepoModule

/**
 * 纯 UI 化改造：不再从 modules.kernelsu.org 抓取在线模块仓库。
 * 直接返回演示仓库模块列表。
 */
class ModuleRepoRepositoryImpl : ModuleRepoRepository {

    override suspend fun fetchModules(): Result<List<RepoModule>> = withContext(Dispatchers.IO) {
        runCatching { DemoData.repoModules }
    }
}