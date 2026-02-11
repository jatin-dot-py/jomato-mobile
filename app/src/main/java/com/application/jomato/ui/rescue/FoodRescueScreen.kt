package com.application.jomato.ui.rescue

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.application.jomato.Prefs
import com.application.jomato.api.ApiClient
import com.application.jomato.api.TabbedHomeEssentials
import com.application.jomato.api.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.application.jomato.ui.theme.JomatoTheme


@Composable

fun FoodRescueScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeState by remember { mutableStateOf(Prefs.getFoodRescueState(context)) }
    var locations by remember { mutableStateOf<List<UserLocation>>(emptyList()) }
    var selectedLocation by remember { mutableStateOf<UserLocation?>(null) }
    var essentials by remember { mutableStateOf<TabbedHomeEssentials?>(null) }
    var isLoadingLocations by remember { mutableStateOf(false) }


    var showBatteryDialog by remember { mutableStateOf(false) }

    val isConnected: Boolean by Prefs.mqttStatus.collectAsState(initial = false)

    fun refreshActiveState() {
        val freshState = Prefs.getFoodRescueState(context)
        activeState = freshState
        if (freshState != null) {
            selectedLocation = freshState.location
            essentials = freshState.essentials
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshActiveState()
            delay(2000)
        }
    }

    fun loadLocations() {
        if (activeState != null) return
        isLoadingLocations = true
        scope.launch {
            val token = withContext(Dispatchers.IO) { Prefs.getToken(context) }
            if (token != null) {
                try {
                    val res = withContext(Dispatchers.IO) { ApiClient.getUserLocations(context, token) }
                    if (res.success) {
                        locations = res.data
                        if (selectedLocation == null && locations.isNotEmpty()) {
                            selectedLocation = locations[0]
                            val loc = locations[0]
                            essentials = withContext(Dispatchers.IO) {
                                ApiClient.getTabbedHomeEssentials(context, loc.cellId, loc.addressId, token)
                            }
                        }
                    }
                } catch (e: Exception) { }
            }
            isLoadingLocations = false
        }
    }

    fun onLocationSelected(loc: UserLocation) {
        selectedLocation = loc
        scope.launch {
            val token = withContext(Dispatchers.IO) { Prefs.getToken(context) } ?: ""
            try {
                essentials = withContext(Dispatchers.IO) {
                    ApiClient.getTabbedHomeEssentials(context, loc.cellId, loc.addressId, token)
                }
            } catch (e: Exception) { }
        }
    }

    fun startMonitoring() {
        if (selectedLocation != null && essentials?.foodRescue != null) {
            RescuePermissionUtils.startRescueService(context, essentials!!, selectedLocation!!)
            refreshActiveState()
        }
    }

    fun stopMonitoring() {
        RescuePermissionUtils.stopRescueService(context)
        refreshActiveState()
        loadLocations()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            RescuePermissionUtils.checkBattery(context, { showBatteryDialog = true }) { startMonitoring() }
        }
    }

    fun onStartClick() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                RescuePermissionUtils.checkBattery(context, { showBatteryDialog = true }) { startMonitoring() }
            }
        } else {
            RescuePermissionUtils.checkBattery(context, { showBatteryDialog = true }) { startMonitoring() }
        }
    }

    LaunchedEffect(Unit) {
        refreshActiveState()
        loadLocations()
    }

    if (showBatteryDialog) {
        RescueBatteryDialog(
            onDismiss = { showBatteryDialog = false },
            onConfirm = {
                showBatteryDialog = false
                RescuePermissionUtils.openBatterySettings(context)
            }
        )
    }

    Scaffold(
        containerColor = JomatoTheme.Background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JomatoTheme.Background)
                    .statusBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(JomatoTheme.Background)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = JomatoTheme.BrandBlack)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Food Rescue Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = JomatoTheme.BrandBlack
                    )
                }
            }
        },
        bottomBar = {
            ConnectionStatusStrip(
                isConnected = isConnected,
                isServiceRunning = activeState != null
            )
        }


    ) { padding ->
        val isMonitoring = activeState != null

        if (isMonitoring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                RescueActiveView(
                    state = activeState!!,
                    onStopClick = { stopMonitoring() }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "SELECT LOCATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JomatoTheme.TextGray,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoadingLocations) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JomatoTheme.Brand)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(locations) { loc ->
                            RescueLocationItem(
                                location = loc,
                                isSelected = selectedLocation?.addressId == loc.addressId,
                                onClick = { onLocationSelected(loc) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onStartClick() },
                        enabled = selectedLocation != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JomatoTheme.Brand,
                            contentColor = JomatoTheme.Background,
                            disabledContainerColor = JomatoTheme.TextGray
                        )
                    ) {
                        Text(
                            "START MONITORING",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}