package com.application.jomato.entity.zomato.auth

import android.content.Context
import android.util.Base64
import com.application.jomato.utils.FileLogger
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import com.application.jomato.entity.zomato.api.ApiBase

object AuthClient {
    private const val TAG = "AuthClient"

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var codeVerifier: String = ""
    private var loginChallenge: String = ""

    data class AuthResult(val accessToken: String, val refreshToken: String)

    private val commonHeaders: Headers = ApiBase.commonHeaders

    fun preOtpFlow(context: Context, phone: String, otpPref: String): Boolean {
        try {
            FileLogger.log(context, TAG, "=== Starting preOtpFlow ===")
            FileLogger.log(context, TAG, "Phone: $phone, OTP Preference: $otpPref")

            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            codeVerifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
            val codeChallenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            FileLogger.log(context, TAG, "Generated PKCE")
            setCookies(context, codeVerifier)

            val urlAuthorizeUrl = "https://accounts.zomato.com/oauth2/auth".toHttpUrl().newBuilder()
                .addQueryParameter("approval_prompt", "auto")
                .addQueryParameter("scope", "offline openid")
                .addQueryParameter("response_type", "code")
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("redirect_uri", "https://accounts.zomato.com/zoauth/callback")
                .addQueryParameter("state", generateStateString())
                .addQueryParameter("client_id", "5276d7f1-910b-4243-92ea-d27e758ad02b")
                .addQueryParameter("code_challenge", codeChallenge)
                .build()

            FileLogger.log(context, TAG, "Authorization URL generated")

            val authRequest = Request.Builder()
                .url(urlAuthorizeUrl)
                .headers(commonHeaders)
                .build()

            val authResponse = client.newCall(authRequest).execute()
            val authResponseBody = authResponse.body?.string() ?: ""

            if (!authResponse.isSuccessful) {
                FileLogger.log(context, TAG, "Authorization failed. Code: ${authResponse.code}", Exception(authResponseBody))
                return false
            }

            val finalUrl = authResponse.request.url
            loginChallenge = finalUrl.queryParameter("login_challenge") ?: run {
                FileLogger.log(context, TAG, "Failed to extract login_challenge", Exception("URL: $finalUrl"))
                return false
            }

            FileLogger.log(context, TAG, "Login Challenge extracted")
            FileLogger.log(context, TAG, "Sending OTP via $otpPref...")

            val sendOtpUrl = "https://accounts.zomato.com/login/phone"
            val sendOtpForm = FormBody.Builder()
                .add("number", phone)
                .add("country_id", "1")
                .add("lc", loginChallenge)
                .add("type", "initiate")
                .add("verification_type", otpPref)
                .add("package_name", "com.application.zomato")
                .add("message_uuid", "")
                .build()

            val sendOtpRequest = Request.Builder()
                .url(sendOtpUrl)
                .headers(commonHeaders)
                .post(sendOtpForm)
                .build()

            val sendOtpResponse = client.newCall(sendOtpRequest).execute()
            val sendOtpBody = sendOtpResponse.body?.string() ?: "{}"

            FileLogger.log(context, TAG, "Send OTP Response Code: ${sendOtpResponse.code}")

            if (!sendOtpResponse.isSuccessful) {
                FileLogger.log(context, TAG, "Send OTP request failed", Exception(sendOtpBody))
                return false
            }

            val json = JSONObject(sendOtpBody)
            val status = json.optBoolean("status")

            if (!status) {
                val message = json.optString("message", "Unknown error")
                FileLogger.log(context, TAG, "Send OTP JSON Error: $message")
            }

            FileLogger.log(context, TAG, "=== preOtpFlow completed. Status: $status ===")
            return status

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Exception in preOtpFlow", e)
            return false
        }
    }

    fun postOtpFlow(context: Context, phone: String, otp: String): AuthResult? {
        try {
            FileLogger.log(context, TAG, "=== Starting postOtpFlow ===")
            FileLogger.log(context, TAG, "Phone: $phone, OTP: ******")

            FileLogger.log(context, TAG, "Step 1: Submitting OTP...")
            val submitOtpForm = FormBody.Builder()
                .add("number", phone)
                .add("otp", otp)
                .add("country_id", "1")
                .add("lc", loginChallenge)
                .add("type", "verify")
                .add("trust_this_device", "true")
                .add("device_token", "")
                .build()

            val submitOtpRequest = Request.Builder()
                .url("https://accounts.zomato.com/login/phone")
                .headers(commonHeaders)
                .post(submitOtpForm)
                .build()

            val submitOtpResponse = client.newCall(submitOtpRequest).execute()
            val submitOtpBody = submitOtpResponse.body?.string() ?: "{}"

            if (!submitOtpResponse.isSuccessful) {
                FileLogger.log(context, TAG, "Submit OTP failed. Code: ${submitOtpResponse.code}", Exception(submitOtpBody))
                return null
            }

            val submitJson = JSONObject(submitOtpBody)
            if (!submitJson.optBoolean("status")) {
                val message = submitJson.optString("message", "Invalid OTP")
                FileLogger.log(context, TAG, "Submit OTP JSON status false: $message")
                return null
            }

            val redirectUrl1 = submitJson.getString("redirect_to")
            FileLogger.log(context, TAG, "Step 2: Following redirect to get consent_challenge...")

            val consentPageRequest = Request.Builder()
                .url(redirectUrl1)
                .headers(commonHeaders)
                .build()

            val consentPageResponse = client.newCall(consentPageRequest).execute()
            val consentPageBody = consentPageResponse.body?.string() ?: ""

            val consentChallenge = consentPageResponse.request.url.queryParameter("consent_challenge")
            if (consentChallenge == null) {
                FileLogger.log(context, TAG, "Failed to extract consent_challenge", Exception(consentPageResponse.request.url.toString()))
                return null
            }

            FileLogger.log(context, TAG, "Step 3: Posting consent...")
            val postConsentForm = FormBody.Builder()
                .add("cc", consentChallenge)
                .build()

            val postConsentRequest = Request.Builder()
                .url("https://accounts.zomato.com/consent")
                .headers(commonHeaders)
                .post(postConsentForm)
                .build()

            val postConsentResponse = client.newCall(postConsentRequest).execute()
            val postConsentBody = postConsentResponse.body?.string() ?: "{}"

            if (!postConsentResponse.isSuccessful) {
                FileLogger.log(context, TAG, "Post consent failed. Code: ${postConsentResponse.code}")
                return null
            }

            val consentJson = JSONObject(postConsentBody)
            if (!consentJson.optBoolean("status")) {
                val message = consentJson.optString("message", "Consent failed")
                FileLogger.log(context, TAG, "Post consent JSON status false: $message")
                return null
            }

            val redirectUrl2 = consentJson.getString("redirect_to")
            FileLogger.log(context, TAG, "Step 4: Following final redirect...")

            val finalRedirectRequest = Request.Builder()
                .url(redirectUrl2)
                .headers(commonHeaders)
                .build()

            val finalRedirectResponse = client.newCall(finalRedirectRequest).execute()
            val finalRedirectBody = finalRedirectResponse.body?.string() ?: ""

            val finalUrl = finalRedirectResponse.request.url
            val code = finalUrl.queryParameter("code")
            val state = finalUrl.queryParameter("state")
            val scope = finalUrl.queryParameter("scope")

            if (code == null || state == null) {
                FileLogger.log(context, TAG, "Failed to extract code/state", Exception("URL: $finalUrl"))
                return null
            }

            FileLogger.log(context, TAG, "Step 5: Exchanging authorization code for tokens...")
            val getTokenFormBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("state", state)
                .add("code_verifier", codeVerifier)
                .add("client_id", "5276d7f1-910b-4243-92ea-d27e758ad02b")
                .add("redirect_uri", "https://accounts.zomato.com/zoauth/callback")

            if (scope != null) {
                getTokenFormBuilder.add("scope", scope)
            }

            val getTokenForm = getTokenFormBuilder.build()

            val tokenHeaders = commonHeaders.newBuilder()
                .set("Accept", "application/json, text/plain, */*")
                .set("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val getTokenRequest = Request.Builder()
                .url("https://accounts.zomato.com/token")
                .headers(tokenHeaders)
                .post(getTokenForm)
                .build()

            val getTokenResponse = client.newCall(getTokenRequest).execute()
            val getTokenBody = getTokenResponse.body?.string() ?: "{}"

            if (!getTokenResponse.isSuccessful) {
                FileLogger.log(context, TAG, "Get token failed. Code: ${getTokenResponse.code}", Exception(getTokenBody))
                return null
            }

            val tokenJson = JSONObject(getTokenBody)
            if (tokenJson.optBoolean("status")) {
                val tokenData = tokenJson.getJSONObject("token")
                val accessToken = tokenData.getString("access_token")
                val refreshToken = tokenData.optString("refresh_token", "")

                FileLogger.log(context, TAG, "Successfully obtained tokens!")
                FileLogger.log(context, TAG, "=== postOtpFlow completed successfully ===")

                return AuthResult(
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            } else {
                val message = tokenJson.optString("message", "Token exchange failed")
                FileLogger.log(context, TAG, "Token exchange failed: $message")
                return null
            }

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Exception in postOtpFlow", e)
            return null
        }
    }


    fun logout(context: Context, accessToken: String, refreshToken: String): Boolean {
        try {
            FileLogger.log(context, TAG, "=== Starting Logout ===")

            val logoutClient = client.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .build()

            val pythonCookieString = "zxcv=; rurl=https://accounts.zomato.com/zoauth/callback; cid=5276d7f1-910b-4243-92ea-d27e758ad02b"

            val logoutHeaders = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("X-Zomato-Refresh-Token", refreshToken)
                .add("Cookie", pythonCookieString)
                .build()

            val request = Request.Builder()
                .url("https://accounts.zomato.com/signout")
                .headers(logoutHeaders)
                .post(FormBody.Builder().build())
                .build()

            val response = logoutClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            FileLogger.log(context, TAG, "Logout Response Code: ${response.code}")

            val json = JSONObject(responseBody)
            val success = json.optBoolean("status") || json.optString("status") == "success"

            if (success) {
                cookieManager.cookieStore.removeAll()
                FileLogger.log(context, TAG, "Cookies cleared. Logout local success.")
            }

            return success

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Logout failed exception", e)
            return false
        }
    }

    private fun setCookies(context: Context, codeVerifier: String) {
        try {
            val uri = URI("https://accounts.zomato.com")

            val zxcvCookie = HttpCookie("zxcv", codeVerifier).apply {
                domain = ".zomato.com"
                path = "/"
            }
            val cidCookie = HttpCookie("cid", "5276d7f1-910b-4243-92ea-d27e758ad02b").apply {
                domain = ".zomato.com"
                path = "/"
            }
            val rurlCookie = HttpCookie("rurl", "https://accounts.zomato.com/zoauth/callback").apply {
                domain = ".zomato.com"
                path = "/"
            }

            cookieManager.cookieStore.add(uri, zxcvCookie)
            cookieManager.cookieStore.add(uri, cidCookie)
            cookieManager.cookieStore.add(uri, rurlCookie)

            FileLogger.log(context, TAG, "Cookies set: zxcv, cid, rurl")

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed to set cookies", e)
        }
    }

    private fun generateStateString(length: Int = 32): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { kotlin.random.Random.nextInt(0, alphabet.length) }
            .map(alphabet::get)
            .joinToString("")
    }
}