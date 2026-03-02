package com.application.jomato.sessions

import android.content.Context
import com.application.jomato.entity.zomato.auth.AuthClient
import com.application.jomato.utils.FileLogger

object SessionMigration {

    private const val TAG = "SessionMigration"
    private const val PREFS_NAME = "jomato_prefs"
    private const val OLD_PREFS_NAME = "zomato_prefs"
    private const val KEY_INSTALL_ID = "install_id"

    fun runIfNeeded(context: Context) : Boolean {
        val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
        val newPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val accessToken = oldPrefs.getString("access_token", null)
        val refreshToken = oldPrefs.getString("refresh_token", null)
        val installId = oldPrefs.getString(KEY_INSTALL_ID, null)
            ?: newPrefs.getString(KEY_INSTALL_ID, null)

        if (accessToken == null && refreshToken == null) {
            FileLogger.log(context, TAG, "No old session found, skipping migration")
            return false
        }

        FileLogger.log(context, TAG, "Old session detected, starting migration")

        if (accessToken != null && refreshToken != null) {
            try {
                AuthClient.logout(context, accessToken, refreshToken)
                FileLogger.log(context, TAG, "Logout successful for old session")
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Logout failed, continuing migration anyway | ${e.message}", e)
            }
        }

        oldPrefs.edit().clear().apply()
        FileLogger.log(context, TAG, "Old prefs cleared")

        newPrefs.edit().clear().apply()
        FileLogger.log(context, TAG, "New prefs cleared")

        if (installId != null) {
            newPrefs.edit().putString(KEY_INSTALL_ID, installId).apply()
            FileLogger.log(context, TAG, "Install ID restored: $installId")
        }

        FileLogger.log(context, TAG, "Migration complete, user must log in again")
        return true
    }
}