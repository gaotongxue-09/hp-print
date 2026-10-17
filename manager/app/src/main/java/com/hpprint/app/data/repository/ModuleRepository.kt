package com.hpprint.app.data.repository

import com.hpprint.app.data.model.Module
import com.hpprint.app.data.model.ModuleUpdateInfo

interface ModuleRepository {
    suspend fun getModules(): Result<List<Module>>
    suspend fun checkUpdate(module: Module): Result<ModuleUpdateInfo>
}
