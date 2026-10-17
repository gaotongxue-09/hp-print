package com.hpprint.app.data.demo

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.hpprint.app.Natives
import com.hpprint.app.data.model.AppInfo
import com.hpprint.app.data.model.Author
import com.hpprint.app.data.model.Module
import com.hpprint.app.data.model.ReleaseAsset
import com.hpprint.app.data.model.RepoModule
import com.hpprint.app.data.model.TemplateInfo
import com.hpprint.app.ui.util.SulogFile

/**
 * 纯 UI 化改造的演示数据源。
 *
 * 集中提供各页面所需的假数据，让 SuperUser / Module / ModuleRepo / Template / Sulog
 * 等页面在没有 root、没有内核、没有网络的情况下也能渲染出完整内容。
 * 这里的 AppInfo 直接构造了带 profile 的对象，因此无需再经过内核/Natives 的真实查询链路。
 */
object DemoData {

    private val now = System.currentTimeMillis()

    private fun pkg(
        packageName: String,
        uid: Int,
        system: Boolean = false,
        installAgeHours: Int = 24,
    ): PackageInfo {
        val installTime = now - installAgeHours * 3600_000L
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = uid
            if (system) {
                this.flags = this.flags or ApplicationInfo.FLAG_SYSTEM
            }
        }
        return PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = appInfo
            this.firstInstallTime = installTime
            this.lastUpdateTime = now - (installAgeHours / 2) * 3600_000L
        }
    }

    private fun rootProfile(packageName: String, uid: Int, custom: Boolean): Natives.Profile =
        Natives.Profile(
            name = packageName,
            currentUid = uid,
            allowSu = true,
            rootUseDefault = !custom,
            umountModules = true,
        )

    val apps: List<AppInfo> = listOf(
        AppInfo(
            label = "支付宝",
            packageInfo = pkg("com.eg.android.AlipayGphone", 10100, installAgeHours = 2),
            profile = rootProfile("com.eg.android.AlipayGphone", 10100, custom = true),
        ),
        AppInfo(
            label = "微信",
            packageInfo = pkg("com.tencent.mm", 10101, installAgeHours = 6),
            profile = rootProfile("com.tencent.mm", 10101, custom = false),
        ),
        AppInfo(
            label = "Termux",
            packageInfo = pkg("com.termux", 10104, installAgeHours = 48),
            profile = rootProfile("com.termux", 10104, custom = false),
        ),
        AppInfo(
            label = "钛备份",
            packageInfo = pkg("com.keramidas.TitaniumBackup", 10102, installAgeHours = 3),
            profile = rootProfile("com.keramidas.TitaniumBackup", 10102, custom = true),
        ),
        AppInfo(
            label = "Shizuku",
            packageInfo = pkg("moe.shizuku.privileged.api", 10105, installAgeHours = 72),
            profile = Natives.Profile(
                name = "moe.shizuku.privileged.api",
                currentUid = 10105,
                allowSu = false,
                nonRootUseDefault = false,
                umountModules = false,
            ),
        ),
        AppInfo(
            label = "文件管理",
            packageInfo = pkg("com.miui.file", 10103, system = true, installAgeHours = 120),
            profile = null,
        ),
        AppInfo(
            label = "设置",
            packageInfo = pkg("com.android.settings", 1000, system = true, installAgeHours = 500),
            profile = null,
        ),
        AppInfo(
            label = "浏览器",
            packageInfo = pkg("com.android.browser", 10106, system = true, installAgeHours = 200),
            profile = null,
        ),
    )

    val userIds: List<Int> = listOf(0)

    val modules: List<Module> = listOf(
        Module(
            id = "zygisk_on_kernelsu",
            name = "Zygisk on HP Print",
            author = "Dr-TSNG",
            version = "1.0.5",
            versionCode = 1005,
            description = "在 HP Print 上加载 Zygisk 模块",
            enabled = true,
            update = false,
            remove = false,
            updateJson = "",
            hasWebUi = true,
            hasActionScript = false,
            metamodule = false,
            actionIconPath = null,
            webUiIconPath = null,
        ),
        Module(
            id = "metamodule_demo",
            name = "HP Print Metamodule",
            author = "tiann",
            version = "0.9.0",
            versionCode = 900,
            description = "示例 MetaModule，在其它模块之前挂载系统资源",
            enabled = true,
            update = false,
            remove = false,
            updateJson = "",
            hasWebUi = false,
            hasActionScript = false,
            metamodule = true,
            actionIconPath = null,
            webUiIconPath = null,
        ),
        Module(
            id = "playintegrityfix",
            name = "Play Integrity Fix",
            author = "chiteroman",
            version = "17.8",
            versionCode = 178,
            description = "修复 Play Integrity / SafetyNet 认证",
            enabled = true,
            update = true,
            remove = false,
            updateJson = "",
            hasWebUi = false,
            hasActionScript = true,
            metamodule = false,
            actionIconPath = null,
            webUiIconPath = null,
        ),
        Module(
            id = "shamiko",
            name = "Shamiko",
            author = "LSPosed Developers",
            version = "1.0.1",
            versionCode = 300,
            description = "在 Zygisk 模式下隐藏 root",
            enabled = false,
            update = false,
            remove = false,
            updateJson = "",
            hasWebUi = false,
            hasActionScript = false,
            metamodule = false,
            actionIconPath = null,
            webUiIconPath = null,
        ),
        Module(
            id = "systemless_hosts",
            name = "Systemless Hosts",
            author = "topjohnwu",
            version = "1.0",
            versionCode = 1,
            description = "系统级无痕 hosts 文件替换",
            enabled = true,
            update = false,
            remove = false,
            updateJson = "",
            hasWebUi = false,
            hasActionScript = false,
            metamodule = false,
            actionIconPath = null,
            webUiIconPath = null,
        ),
        Module(
            id = "dnscrypt_proxy",
            name = "DNSCrypt-Proxy",
            author = "Frank Denis",
            version = "2.1.5",
            versionCode = 205,
            description = "加密 DNS 代理服务",
            enabled = false,
            update = false,
            remove = true,
            updateJson = "",
            hasWebUi = false,
            hasActionScript = true,
            metamodule = false,
            actionIconPath = null,
            webUiIconPath = null,
        ),
    )

    val repoModules: List<RepoModule> = listOf(
        RepoModule(
            moduleId = "zygisk_next",
            moduleName = "Zygisk Next",
            authors = "Dr-TSNG",
            authorList = listOf(Author("Dr-TSNG", "https://github.com/Dr-TSNG")),
            summary = "下一代 Zygisk 实现，支持独立注入",
            metamodule = false,
            stargazerCount = 2841,
            updatedAt = "2026-09-28",
            createdAt = "2024-01-12",
            latestRelease = "1.2.7",
            latestReleaseTime = "2026-09-28",
            latestVersionCode = 127,
            latestAsset = ReleaseAsset("zygisk-next-1.2.7.zip", "https://example.com/zygisk.zip", 1024 * 1024L),
        ),
        RepoModule(
            moduleId = "playintegrityfix",
            moduleName = "Play Integrity Fix",
            authors = "chiteroman",
            authorList = listOf(Author("chiteroman", "https://github.com/chiteroman")),
            summary = "修复 Play Integrity 与 SafetyNet 认证",
            metamodule = false,
            stargazerCount = 5520,
            updatedAt = "2026-09-20",
            createdAt = "2023-11-02",
            latestRelease = "17.8",
            latestReleaseTime = "2026-09-20",
            latestVersionCode = 178,
            latestAsset = ReleaseAsset("playintegrityfix-17.8.zip", "https://example.com/pif.zip", 2048 * 1024L),
        ),
        RepoModule(
            moduleId = "lsposed",
            moduleName = "LSPosed",
            authors = "LSPosed Developers",
            authorList = listOf(Author("LSPosed Developers", "https://github.com/LSPosed")),
            summary = "Xposed 框架，运行 Xposed 模块",
            metamodule = false,
            stargazerCount = 14890,
            updatedAt = "2026-09-10",
            createdAt = "2021-01-01",
            latestRelease = "1.9.2",
            latestReleaseTime = "2026-09-10",
            latestVersionCode = 192,
            latestAsset = ReleaseAsset("lsposed-1.9.2.zip", "https://example.com/lsposed.zip", 4096 * 1024L),
        ),
        RepoModule(
            moduleId = "metamodule_demo",
            moduleName = "HP Print Metamodule",
            authors = "tiann",
            authorList = listOf(Author("tiann", "https://github.com/tiann")),
            summary = "可插拔的系统级无痕修改基础架构",
            metamodule = true,
            stargazerCount = 1024,
            updatedAt = "2026-08-30",
            createdAt = "2025-06-01",
            latestRelease = "0.9.0",
            latestReleaseTime = "2026-08-30",
            latestVersionCode = 900,
            latestAsset = null,
        ),
    )

    val templates: List<TemplateInfo> = listOf(
        TemplateInfo(
            id = "default_shell",
            name = "默认 Shell",
            description = "标准 root shell 环境",
            author = "HP Print",
            local = true,
        ),
        TemplateInfo(
            id = "isolated_app",
            name = "隔离应用",
            description = "最小权限、独立命名空间的 root 环境",
            author = "HP Print",
            local = true,
            namespace = Natives.Profile.Namespace.INDIVIDUAL.ordinal,
        ),
        TemplateInfo(
            id = "full_access",
            name = "完全访问",
            description = "授予全部 capabilities 的完整 root 环境",
            author = "tiann",
            local = false,
        ),
    )

    // Sulog 演示数据：文件名 + 日志行（供 SulogHelper mock 使用）
    val sulogFiles: List<SulogFile> = listOf(
        SulogFile(name = "sulog-2026-09-30.log", path = "/data/adb/ksu/log/sulog-2026-09-30.log"),
        SulogFile(name = "sulog-2026-09-29.log", path = "/data/adb/ksu/log/sulog-2026-09-29.log"),
    )

    val sulogLines: List<String> = listOf(
        """seq=120003 type=root_execve ts_ns=108123456789 uid=10100 gid=10100 comm="su" cwd="/" cmd="/system/bin/sh -c id"""",
        """seq=120002 type=ioctl_grant_root ts_ns=108123400000 uid=10104 pid=5231 ctx="u:r:ksu:s0"""",
        """seq=120001 type=sucompat ts_ns=108120000000 uid=2000 comm="su" allowed=1""",
        """seq=120000 type=daemon_start ts_ns=108110000000 restart=0 boot_id=7f2c1a9d04e1""",
        """seq=119999 type=root_execve ts_ns=108100000000 uid=10101 gid=10101 comm="su" cmd="mount -o remount,rw /system"""",
    )
}