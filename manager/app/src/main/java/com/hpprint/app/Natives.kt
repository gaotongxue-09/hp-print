package com.hpprint.app

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import com.hpprint.app.Natives.Profile.RootProfileFlag

/**
 * @author weishu
 * @date 2022/12/8.
 *
 * 纯 UI 化改造：原实现通过 JNI 调用内核 supercall。此处已改为纯 Kotlin
 * mock 门面，返回"已安装、功能完整"的演示值，供 UI 层渲染使用。
 * 不再加载 libkernelsu，也不再触发任何内核/root 交互。
 */
object Natives {
    // minimal supported kernel version
    // 32513: add uapi version
    const val MINIMAL_SUPPORTED_KERNEL = 32513

    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"

    const val ROOT_UID = 0
    const val ROOT_GID = 0

    // 演示版本号（与 MINIMAL_SUPPORTED_KERNEL 保持一致，使 isFullFeatured() 为 true）
    const val DEMO_KERNEL_VERSION = 12144

    val version: Int
        get() = DEMO_KERNEL_VERSION

    val isSafeMode: Boolean
        get() = false

    val isLkmMode: Boolean
        get() = true

    val isLkmBundled: Boolean
        get() = true

    val isLateLoadMode: Boolean
        get() = false

    val isManager: Boolean
        get() = true

    val isPrBuild: Boolean
        get() = false

    fun uidShouldUmount(uid: Int): Boolean = true

    /**
     * Get the profile of the given package.
     * @param key usually the package name
     * @return return null if failed.
     */
    fun getAppProfile(key: String?, uid: Int): Profile = Profile(
        name = key.orEmpty(),
        currentUid = uid,
        allowSu = false,
    )

    fun setAppProfile(profile: Profile?): Boolean = true

    /**
     * `su` compat mode can be disabled temporarily.
     *  0: disabled
     *  1: enabled
     *  negative : error
     */
    fun isSuEnabled(): Boolean = true
    fun setSuEnabled(enabled: Boolean): Boolean = true

    /**
     * Kernel module umount can be disabled temporarily.
     */
    fun isKernelUmountEnabled(): Boolean = true
    fun setKernelUmountEnabled(enabled: Boolean): Boolean = true

    /**
     * SELinux hide can be disabled temporarily.
     */
    fun isSelinuxHideEnabled(): Boolean = true
    fun setSelinuxHideEnabled(enabled: Boolean): Int = 0

    /**
     * Get the user name for the uid.
     */
    fun getUserName(uid: Int): String? = "user"

    fun getSuperuserCount(): Int = 8

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    fun setDefaultUmountModules(umountModules: Boolean): Boolean {
        Profile(
            NON_ROOT_DEFAULT_PROFILE_KEY,
            NOBODY_UID,
            false,
            umountModules = umountModules
        ).let {
            return setAppProfile(it)
        }
    }

    fun isDefaultUmountModules(): Boolean {
        getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID).let {
            return it.umountModules
        }
    }

    val kernelUAPIVersion: Int
        get() = managerUAPIVersion

    val managerUAPIVersion: Int
        get() = MINIMAL_SUPPORTED_KERNEL

    fun isFullFeatured(): Boolean {
        return isManager && kernelUAPIVersion == managerUAPIVersion
    }

    @Keep
    @Immutable
    @Parcelize
    @Serializable
    data class Profile(
        // and there is a default profile for root and non-root
        val name: String,
        // current uid for the package, this is convivent for kernel to check
        // if the package name doesn't match uid, then it should be invalidated.
        val currentUid: Int = 0,

        // if this is true, kernel will grant root permission to this package
        val allowSu: Boolean = false,

        // these are used for root profile
        val rootUseDefault: Boolean = true,
        val rootTemplate: String? = null,
        val uid: Int = ROOT_UID,
        val gid: Int = ROOT_GID,
        val groups: List<Int> = mutableListOf(),
        val capabilities: List<Int> = mutableListOf(),
        val context: String = KERNEL_SU_DOMAIN,
        val namespace: Int = Namespace.INHERITED.ordinal,

        val nonRootUseDefault: Boolean = true,
        val umountModules: Boolean = true,
        var rules: String = "", // this field is save in ksud!!

        val flags: Long = FLAG_KSU_NO_NEW_PRIVS,
    ) : Parcelable {
        @Keep
        enum class RootProfileFlag(val display: String, val desc: Int) {
            NO_NEW_PRIVS(
                "NO_NEW_PRIVS",
                R.string.profile_flags_desc_no_new_privs
            )
        }

        enum class Namespace {
            INHERITED,
            GLOBAL,
            INDIVIDUAL,
        }

        constructor() : this("")
    }

    const val FLAG_KSU_NO_NEW_PRIVS = 1L
}

fun List<RootProfileFlag>.toRawFlags(): Long =
    fold(0L) { acc, flag -> acc.or(1L.shl(flag.ordinal)) }

fun List<RootProfileFlag>.toOrdinalList(): List<Int> =
    map { it.ordinal }

fun Long.toRootProfileFlags(): List<RootProfileFlag> =
    RootProfileFlag.entries.filter { 1L.shl(it.ordinal).and(this) != 0L }.toList()