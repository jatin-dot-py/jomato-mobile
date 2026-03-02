package com.application.jomato.entity.zomato.rescue

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.R
import androidx.core.content.ContextCompat
import com.application.jomato.entity.zomato.ZomatoManager
import com.application.jomato.entity.zomato.api.ApiClient
import com.application.jomato.entity.zomato.api.TabbedHomeEssentials
import com.application.jomato.entity.zomato.api.UserLocation
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FoodRescueContent"

private sealed class ScreenState {
    object Loading : ScreenState()
    data class Error(val message: String) : ScreenState()
    data class Active(val state: FoodRescueState) : ScreenState()
    data class Setup(
        val locations: List<UserLocation>,
        val selectedLocation: UserLocation?,
        val essentials: TabbedHomeEssentials?,
        val isFetchingEssentials: Boolean
    ) : ScreenState()
}

@Composable
fun FoodRescueContent(sessionId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Loading) }

    fun loadActiveOrLocations(accessToken: String) {
        val activeState = ZomatoManager.getFoodRescueState(context)
        if (activeState != null) {
            screenState = ScreenState.Active(activeState)
            return
        }
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.getUserLocations(context, accessToken)
                }
                if (res.success) {
                    val locs = res.data
                    val firstLoc = locs.firstOrNull()
                    screenState = ScreenState.Setup(
                        locations = locs,
                        selectedLocation = firstLoc,
                        essentials = null,
                        isFetchingEssentials = firstLoc != null
                    )
                    if (firstLoc != null) {
                        val ess = withContext(Dispatchers.IO) {
                            ApiClient.getTabbedHomeEssentials(context, firstLoc.cellId, firstLoc.addressId, accessToken)
                        }
                        screenState = (screenState as? ScreenState.Setup)?.copy(
                            essentials = ess,
                            isFetchingEssentials = false
                        ) ?: screenState
                    }
                } else {
                    screenState = ScreenState.Setup(
                        locations = emptyList(),
                        selectedLocation = null,
                        essentials = null,
                        isFetchingEssentials = false
                    )
                }
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to load locations: ${e.message}", e)
                screenState = ScreenState.Error("Failed to load locations.")
            }
        }
    }

    LaunchedEffect(sessionId) {
        val session = ZomatoManager.getSession(context, sessionId)
        if (session == null) {
            screenState = ScreenState.Error("Session not found.")
            return@LaunchedEffect
        }

        val user = withContext(Dispatchers.IO) {
            try { ApiClient.getUserInfo(context, session.accessToken) } catch (_: Exception) { null }
        }
        if (user == null) {
            screenState = ScreenState.Error("Session expired. Please log in again.")
            return@LaunchedEffect
        }

        FileLogger.log(context, TAG, "Session valid for ${user.name}")
        loadActiveOrLocations(session.accessToken)
    }

    // Polls every 2 seconds to detect when service is stopped externally (e.g. notification stop button)
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (screenState is ScreenState.Active && !ZomatoManager.isFoodRescueActive(context)) {
                val session = ZomatoManager.getSession(context, sessionId)
                if (session != null) loadActiveOrLocations(session.accessToken)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (val state = screenState) {
                is ScreenState.Loading -> LoadingView()
                is ScreenState.Error -> ErrorView(state.message)
                is ScreenState.Active -> ActiveView(
                    state = state.state,
                    onStop = {
                        RescuePermissionUtils.deactivateRescue(context)
                        screenState = ScreenState.Loading
                        val session = ZomatoManager.getSession(context, sessionId)
                        if (session != null) loadActiveOrLocations(session.accessToken)
                    }
                )
                is ScreenState.Setup -> SetupView(
                    state = state,
                    onLocationSelected = { loc ->
                        val session = ZomatoManager.getSession(context, sessionId) ?: return@SetupView
                        screenState = state.copy(
                            selectedLocation = loc,
                            essentials = null,
                            isFetchingEssentials = true
                        )
                        scope.launch {
                            val ess = withContext(Dispatchers.IO) {
                                ApiClient.getTabbedHomeEssentials(context, loc.cellId, loc.addressId, session.accessToken)
                            }
                            screenState = (screenState as? ScreenState.Setup)?.copy(
                                essentials = ess,
                                isFetchingEssentials = false
                            ) ?: screenState
                        }
                    },
                    onStart = {
                        val loc = state.selectedLocation ?: return@SetupView
                        val ess = state.essentials ?: return@SetupView
                        RescuePermissionUtils.activateRescue(context, ess, loc, sessionId)
                        screenState = ScreenState.Active(
                            FoodRescueState(ess, loc, System.currentTimeMillis())
                        )
                    }
                )
            }
        }
    }
}

// ── Loading ─────────────────────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = JomatoTheme.Brand)
    }
}

// ── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = JomatoTheme.TextGray,
            fontSize = 14.sp
        )
    }
}

// ── Active monitoring ────────────────────────────────────────────────────────

@Composable
private fun ActiveView(
    state: FoodRescueState,
    onStop: () -> Unit
) {
    RescueActiveView(state = state, onStopClick = onStop)
}

// ── Location selection + start ───────────────────────────────────────────────

@Composable
private fun SetupView(
    state: ScreenState.Setup,
    onLocationSelected: (UserLocation) -> Unit,
    onStart: () -> Unit
) {
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            RescuePermissionUtils.checkBattery(
                context,
                onShowDialog = { RescuePermissionUtils.openBatterySettings(context) },
                onSuccess = onStart
            )
        }
    }

    fun onStartClick() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RescuePermissionUtils.checkBattery(
                context,
                onShowDialog = { RescuePermissionUtils.openBatterySettings(context) },
                onSuccess = onStart
            )
        }
    }

    if (state.locations.isEmpty()) {
        EmptyLocationsView()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            state.locations.forEachIndexed { index, loc ->
                RescueLocationItem(
                    location = loc,
                    isSelected = state.selectedLocation?.addressId == loc.addressId,
                    onClick = { onLocationSelected(loc) }
                )
                if (index < state.locations.lastIndex) {
                    Divider(
                        color = JomatoTheme.Divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        val canStart = state.selectedLocation != null &&
                state.essentials?.foodRescue != null &&
                !state.isFetchingEssentials

        Button(
            onClick = { onStartClick() },
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(50.dp)
                .shadow(if (canStart) 4.dp else 0.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JomatoTheme.Brand,
                contentColor = JomatoTheme.Background,
                disabledContainerColor = JomatoTheme.Brand.copy(alpha = 0.3f),
                disabledContentColor = JomatoTheme.Background.copy(alpha = 0.5f)
            )
        ) {
            if (state.isFetchingEssentials) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = JomatoTheme.Background
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = if (state.isFetchingEssentials) "Loading…" else "START MONITORING",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ── Empty locations fallback ─────────────────────────────────────────────────

@Composable
private fun EmptyLocationsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.saved_address_pointer),
            contentDescription = "Add an address in Zomato",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Add an address in Zomato to get started",
            fontSize = 13.sp,
            color = JomatoTheme.TextGray,
            textAlign = TextAlign.Center
        )
    }
}