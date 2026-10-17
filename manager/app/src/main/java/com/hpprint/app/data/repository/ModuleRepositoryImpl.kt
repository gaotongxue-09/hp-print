package com.hpprint.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.data.demo.DemoData
import com.hpprint.app.data.model.Module
import com.hpprint.app.data.model.ModuleUpdateInfo

/**
 * 纯 UI 化改造：不再执行 `ksud module list` 命令、不再发起更新检查网络请求。
 * 直接返回演示模块列表。
 */
class ModuleRepositoryImpl : ModuleRepository {

    override suspend fun getModules(): Result<List<Module>> = withContext(Dispatchers.IO) {
        runCatching { DemoData.modules }
    }

    override suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching { ModuleUpdateInfo.Empty }
        }
}