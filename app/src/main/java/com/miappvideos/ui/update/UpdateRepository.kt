package com.miappvideos.ui.update

import android.content.Context

object UpdateRepository {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_LAST_VERSION_NAME = "last_version_name"
    private const val KEY_LAST_URL = "last_apk_url"
    private const val KEY_LAST_NOTES = "last_notes"

    // Cache de 12 horas
    private const val CACHE_DURATION_MS = 12 * 60 * 60 * 1000L

    fun checkUpdate(
        context: Context,
        onUpdateAvailable: (versionCode: Int, versionName: String, apkUrl: String, releaseNotes: List<String>) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0)
        val now = System.currentTimeMillis()

        // Si ha pasado menos de 12h, devolver resultado de caché
        if (now - lastCheck < CACHE_DURATION_MS) {
            serveCached(prefs, onUpdateAvailable, onNoUpdate)
            return
        }

        // Consultar en tiempo real
        UpdateManager.checkUpdate(context, onUpdateAvailable, onNoUpdate)
    }

    private fun serveCached(
        prefs: android.content.SharedPreferences,
        onUpdateAvailable: (versionCode: Int, versionName: String, apkUrl: String, releaseNotes: List<String>) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        val versionCode = prefs.getInt(KEY_LAST_VERSION_CODE, 0)
        val versionName = prefs.getString(KEY_LAST_VERSION_NAME, "") ?: ""
        val url = prefs.getString(KEY_LAST_URL, "") ?: ""
        val notesString = prefs.getString(KEY_LAST_NOTES, "") ?: ""
        val notes = if (notesString.isNotEmpty()) notesString.split("|") else emptyList()

        if (versionCode > UpdateManager.CURRENT_VERSION_CODE) {
            onUpdateAvailable(versionCode, versionName, url, notes)
        } else {
            onNoUpdate()
        }
    }

    fun forceCheckUpdate(
        context: Context,
        onUpdateAvailable: (versionCode: Int, versionName: String, apkUrl: String, releaseNotes: List<String>) -> Unit,
        onNoUpdate: () -> Unit
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        UpdateManager.checkUpdate(context, onUpdateAvailable, onNoUpdate)
    }
}
