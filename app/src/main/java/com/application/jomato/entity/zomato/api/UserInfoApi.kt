package com.application.jomato.entity.zomato.api

import android.content.Context
import com.application.jomato.utils.FileLogger
import okhttp3.Request

internal object UserInfoApi {

    fun getUserInfo(context: Context, accessToken: String): UserInfo? {
        FileLogger.log(context, ApiBase.TAG, "Fetching User Info...")
        try {
            val url = "https://api.zomato.com/gw/user/info"
            val request = Request.Builder()
                .url(url)
                .headers(ApiBase.authenticatedHeaders(accessToken))
                .get()
                .build()

            val response = ApiBase.client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                FileLogger.log(context, ApiBase.TAG, "UserInfo Success: ${response.code}")
                return ApiBase.jsonParser.decodeFromString<UserInfo>(responseBody)
            }
            FileLogger.log(context, ApiBase.TAG, "UserInfo Failed. Code: ${response.code}")
            return null
        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "UserInfo Exception", e)
            return null
        }
    }
}
