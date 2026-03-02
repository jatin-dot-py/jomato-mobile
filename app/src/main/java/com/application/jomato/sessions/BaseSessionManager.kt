package com.application.jomato.sessions

import android.content.Context
import android.content.SharedPreferences

abstract class BaseSessionManager<T : BaseSession> {

    /** Entity this manager handles (e.g. Entity.ZOMATO for ZomatoManager). */
    abstract val entity: Entity

    protected val PREFS_NAME = "jomato_prefs"

    protected fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    abstract fun saveSession(context: Context, session: T)
    abstract fun getSession(context: Context, sessionId: String): T?
    abstract fun listSessions(context: Context): List<T>
    abstract fun deleteSession(context: Context, sessionId: String)
}