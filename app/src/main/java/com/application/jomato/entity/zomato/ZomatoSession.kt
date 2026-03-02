package com.application.jomato.entity.zomato

import com.application.jomato.sessions.BaseSession

data class ZomatoSession(
    override val sessionId: String,
    override val createdAt: Long,
    val accessToken: String,
    val refreshToken: String,
    val userName: String,
    val userId: String
) : BaseSession() {
    override val displayName: String get() = userName
}
