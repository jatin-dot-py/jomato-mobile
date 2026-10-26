package com.application.jomato

import android.content.Context
import android.content.SharedPreferences
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object Prefs {

    private const val PREFS_NAME = "jomato_prefs"
    private const val TAG = "Prefs"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_HIDE_INTEGRITY = "hide_integrity_dialog"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ALERT_STYLE = "alert_style"

    /** Loud full-screen takeover + looping ring on the alarm stream. */
    const val ALERT_STYLE_ALARM = "alarm"

    /** Loud notification sound, no screen takeover. */
    const val ALERT_STYLE_SOUND = "sound"

    /** Vibration only. */
    const val ALERT_STYLE_SILENT = "silent"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlertStyle(context: Context): String =
        prefs(context).getString(KEY_ALERT_STYLE, ALERT_STYLE_ALARM) ?: ALERT_STYLE_ALARM

    fun setAlertStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_ALERT_STYLE, style).apply()
        FileLogger.log(context, TAG, "Alert style set to $style")
    }

    fun getInstallId(context: Context): String {
        var installId = prefs(context).getString(KEY_INSTALL_ID, null)
        if (installId == null) {
            val bytes = ByteArray(8)
            java.security.SecureRandom().nextBytes(bytes)
            installId = bytes.joinToString("") { "%02x".format(it) }
            prefs(context).edit().putString(KEY_INSTALL_ID, installId).apply()
            FileLogger.log(context, TAG, "Generated new install ID: $installId")
        }
        return installId!!
    }

    fun getHideIntegrity(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_INTEGRITY, false)

    fun setHideIntegrity(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_INTEGRITY, hide).apply()
    }

    /** "system" | "dark" | "light" */
    private val _themeMode = MutableStateFlow("system")
    val themeMode = _themeMode.asStateFlow()

    fun loadThemeMode(context: Context) {
        _themeMode.value = prefs(context).getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun cycleThemeMode(context: Context) {
        val next = when (_themeMode.value) {
            "system" -> "dark"
            "dark" -> "light"
            else -> "system"
        }
        _themeMode.value = next
        prefs(context).edit().putString(KEY_THEME_MODE, next).apply()
    }

}