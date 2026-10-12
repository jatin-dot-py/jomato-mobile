package com.application.jomato.entity.zomato

import android.content.Context
import com.application.jomato.sessions.BaseSession
import com.application.jomato.sessions.EntityLogoutHandler
import com.application.jomato.entity.zomato.auth.AuthClient
import com.application.jomato.entity.zomato.rescue.RescuePermissionUtils
import com.application.jomato.utils.FileLogger

object ZomatoLogoutHandler : EntityLogoutHandler() {

    private const val TAG = "ZomatoLogoutHandler"

    override suspend fun logout(context: Context, session: BaseSession): Boolean {
        val zSession = session as? ZomatoSession ?: run {
            FileLogger.log(context, TAG, "logout called with non-Zomato session, deleting locally only")
            return false
        }

        // Food rescue keeps using this session's token; stop it before the session disappears.
        if (ZomatoManager.getFoodRescueSessionId(context) == zSession.sessionId) {
            FileLogger.log(context, TAG, "Session is driving food rescue, stopping it")
            RescuePermissionUtils.deactivateRescue(context)
        }

        val serverSuccess = try {
            AuthClient.logout(context, zSession.accessToken, zSession.refreshToken)
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Server logout failed: ${e.message}")
            false
        }

        ZomatoManager.deleteSession(context, zSession.sessionId)
        FileLogger.log(context, TAG, "Session ${zSession.sessionId.take(8)} removed (server=$serverSuccess)")
        return serverSuccess
    }
}
