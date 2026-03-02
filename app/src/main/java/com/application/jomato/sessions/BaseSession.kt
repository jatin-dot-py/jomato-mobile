package com.application.jomato.sessions

abstract class BaseSession {
    abstract val sessionId: String
    abstract val createdAt: Long
    /** Human-readable name for this session (e.g. user name). Shown in the account manager. */
    abstract val displayName: String
}