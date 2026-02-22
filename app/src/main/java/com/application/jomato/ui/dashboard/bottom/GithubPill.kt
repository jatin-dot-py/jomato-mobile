package com.application.jomato.ui.dashboard.bottom

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubPill() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showAltText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            showAltText = !showAltText
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            updateState = UpdateManager.checkForUpdate(context)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (val state = updateState) {
                    is UpdateState.Idle -> {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/jatin-dot-py/jomato-mobile")
                            )
                        )
                    }
                    is UpdateState.Available -> {
                        coroutineScope.launch(Dispatchers.IO) {
                            val file = UpdateManager.downloadAndVerify(
                                context = context,
                                apkUrl = state.apkUrl,
                                sha256 = state.sha256,
                                onProgress = { progress ->
                                    updateState = if (progress == -1) {
                                        UpdateState.Verifying
                                    } else {
                                        UpdateState.Downloading(progress)
                                    }
                                }
                            )
                            updateState = if (file != null) {
                                UpdateState.ReadyToInstall(file)
                            } else {
                                UpdateState.Error(state.apkUrl, state.sha256)
                            }
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        UpdateManager.installApk(context, state.file)
                    }
                    is UpdateState.Error -> {
                        coroutineScope.launch(Dispatchers.IO) {
                            val file = UpdateManager.downloadAndVerify(
                                context = context,
                                apkUrl = state.apkUrl,
                                sha256 = state.sha256,
                                onProgress = { progress ->
                                    updateState = if (progress == -1) {
                                        UpdateState.Verifying
                                    } else {
                                        UpdateState.Downloading(progress)
                                    }
                                }
                            )
                            updateState = if (file != null) {
                                UpdateState.ReadyToInstall(file)
                            } else {
                                UpdateState.Error(state.apkUrl, state.sha256)
                            }
                        }
                    }
                    is UpdateState.Downloading, UpdateState.Verifying -> { /* block clicks during download */ }
                }
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

            val textToShow = when (val state = updateState) {
                is UpdateState.Idle -> if (showAltText) "Star us on GitHub" else "jatin-dot-py/jomato-mobile"
                is UpdateState.Available -> "Update Available (${state.version})"
                is UpdateState.Downloading -> "Downloading ${state.progress}%"
                is UpdateState.Verifying -> "Verifying..."
                is UpdateState.ReadyToInstall -> "Tap to Install"
                is UpdateState.Error -> "Download Failed — Tap to Retry"
            }

            Text(
                text = textToShow,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = JomatoTheme.BrandBlack
            )

            if (updateState is UpdateState.Available || updateState is UpdateState.ReadyToInstall) {
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