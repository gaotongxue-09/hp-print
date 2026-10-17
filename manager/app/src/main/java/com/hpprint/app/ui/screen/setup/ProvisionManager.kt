package com.hpprint.app.ui.screen.setup

import android.content.Context
import com.hpprint.app.hpApp

/**
 * Tracks whether the first-run onboarding has been completed.
 *
 * @author HP Print
 */
object ProvisionManager {
    private const val PREFS_NAME = "oobe"
    private const val KEY_PROVISIONED = "is_provisioned"

    private val prefs
        get() = hpApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isProvisioned(): Boolean = prefs.getBoolean(KEY_PROVISIONED, false)

    fun setProvisioned(value: Boolean) {
        prefs.edit().putBoolean(KEY_PROVISIONED, value).apply()
    }
}