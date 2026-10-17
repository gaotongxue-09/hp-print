package com.hpprint.app.data.repository

import com.hpprint.app.data.model.RepoModule

interface ModuleRepoRepository {
    suspend fun fetchModules(): Result<List<RepoModule>>
}
