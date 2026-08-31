package com.miappvideos.util

import android.content.Context

/**
 * Preferencias de ahorro de datos: audio-only (sin pista de video) y
 * limites de calidad para reducir el consumo de internet.
 */
object DataSaver {

    private const val PREFS = "data_saver_prefs"
    const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
