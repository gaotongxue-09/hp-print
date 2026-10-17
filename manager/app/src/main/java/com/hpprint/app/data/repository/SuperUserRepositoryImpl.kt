package com.hpprint.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hpprint.app.data.demo.DemoData
import com.hpprint.app.data.model.AppInfo

/**
 * 纯 UI 化改造：不再绑定 ksud 的 IPC 服务、不再调用内核/root。
 * 直接返回演示数据，供 SuperUser 页渲染完整列表。
 */
class SuperUserRepositoryImpl : SuperUserRepository {

    override suspend fun getAppList(): Result<Pair<List<AppInfo>, List<Int>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Pair(DemoData.apps, DemoData.userIds)
            }
        }

    override suspend fun refreshProfiles(currentApps: List<AppInfo>): Result<List<AppInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (currentApps.isEmpty()) emptyList() else currentApps
            }
        }
}