package com.application.jomato.sessions

import android.content.Context

/**
 * Standardized logout contract for any entity.
 *
 * Implementations handle server-side token invalidation followed by
 * local session deletion. If the server call fails, the local session
 * should still be deleted (graceful degradation).
 */
abstract class EntityLogoutHandler {

    /**
     * Invalidate the session server-side and delete it locally.
     * @return true if the server call succeeded; false if it failed
     *         (local session is still deleted either way).
     */
    abstract suspend fun logout(context: Context, session: BaseSession): Boolean
}
