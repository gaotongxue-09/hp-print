package com.hpprint.app

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.system.Os
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.hpprint.app.data.repository.SettingsRepositoryImpl
import com.hpprint.app.ui.viewmodel.SuperUserViewModel
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

lateinit var hpApp: HpPrintApplication

class HpPrintApplication : Application(), ViewModelStoreOwner {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    // 记录当前前台 Activity，供系统打印回退（PrintManager.print 仅允许 Activity 上下文）使用。
    private var currentActivityRef = WeakReference<Activity>(null)
    val currentActivity: Activity? get() = currentActivityRef.get()

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        hpApp = this

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef.get() == activity) currentActivityRef.clear()
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
        })

        if (!isUserUnlocked()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enable = SettingsRepositoryImpl().enablePredictiveBack
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

        val superUserViewModel = ViewModelProvider(this)[SuperUserViewModel::class.java]
        superUserViewModel.loadAppList()

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "HP Print/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
