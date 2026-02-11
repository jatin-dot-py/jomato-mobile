package com.application.jomato.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.R
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/jatin-dot-py/jomato-mobile/releases/latest")
    suspend fun getLatestRelease(): ResponseBody
}

@Composable
fun GitHubPill() {
    val context = LocalContext.current
    var updateAvailable by remember { mutableStateOf(false) }
    var apkUrl by remember { mutableStateOf("") }
    var newVersion by remember { mutableStateOf("") }

    var showAltText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            showAltText = !showAltText
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val api = Retrofit.Builder()
                    .baseUrl("https://api.github.com/")
                    .build()
                    .create(GitHubApi::class.java)

                val response = api.getLatestRelease().string()
                val json = JSONObject(response)
                val latestTag = json.getString("tag_name")
                val downloadLink = json.getJSONArray("assets")
                    .getJSONObject(0)
                    .getString("browser_download_url")

                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = pInfo.versionName

                if (latestTag != currentVersion) {
                    newVersion = latestTag
                    apkUrl = downloadLink
                    updateAvailable = true
                }
            } catch (e: Exception) {
                FileLogger.log(context, "GitHubPill", "Update check failed: ${e.message}", e)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = if (updateAvailable) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                } else {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jatin-dot-py/jomato-mobile"))
                }
                context.startActivity(intent)
            },
        color = JomatoTheme.SecondaryBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = "GitHub",
                tint = JomatoTheme.BrandBlack,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))

            val textToShow = if (updateAvailable) {
                "Update Available ($newVersion)"
            } else {
                if (showAltText) "Star us on GitHub" else "jatin-dot-py/jomato-mobile"
            }

            Text(
                text = textToShow,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color =  JomatoTheme.BrandBlack
            )

            if (updateAvailable) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = JomatoTheme.Brand,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NEW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = JomatoTheme.Background,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}