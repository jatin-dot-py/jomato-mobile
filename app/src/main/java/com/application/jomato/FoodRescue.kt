package com.application.jomato

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.application.jomato.api.ApiClient
import com.application.jomato.api.TabbedHomeEssentials
import com.application.jomato.api.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PageBackground = Color(0xFFF7F7F7)
private val CardWhite = Color.White
private val DarkText = Color(0xFF111827)
private val MutedText = Color(0xFF6B7280)
private val BrandGradient = Brush.horizontalGradient(listOf(BrandColor, Color(0xFFFF5252)))
private val StopRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodRescueScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeState by remember { mutableStateOf(Prefs.getFoodRescueState(context)) }
    var locations by remember { mutableStateOf<List<UserLocation>>(emptyList()) }
    var selectedLocation by remember { mutableStateOf<UserLocation?>(null) }
    var essentials by remember { mutableStateOf<TabbedHomeEssentials?>(null) }
    var isLoadingLocations by remember { mutableStateOf(false) }

    var showLocationDialog by remember { mutableStateOf(activeState == null) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    fun refreshActiveState() {
        activeState = Prefs.getFoodRescueState(context)
        if (activeState != null) {
            selectedLocation = activeState!!.location
            essentials = activeState!!.essentials
            showLocationDialog = false
        }
    }

    LaunchedEffect(activeState != null) {
        while (true) {
            val freshState = Prefs.getFoodRescueState(context)
            if (freshState != null) activeState = freshState else refreshActiveState()
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
                    if (res.success) locations = res.data
                } catch (e: Exception) { }
            }
            isLoadingLocations = false
        }
    }

    fun fetchEssentials(loc: UserLocation) {
        essentials = null
        scope.launch {
            val token = withContext(Dispatchers.IO) { Prefs.getToken(context) } ?: ""
            try {
                essentials = withContext(Dispatchers.IO) {
                    ApiClient.getTabbedHomeEssentials(context, loc.cellId, loc.addressId, token)
                }
            } catch (e: Exception) { }
        }
    }

    fun toggleSubscription() {
        if (activeState != null) {
            Prefs.stopFoodRescue(context)
            val intent = Intent(context, com.application.jomato.service.FoodRescueService::class.java)
            intent.action = com.application.jomato.service.FoodRescueService.ACTION_STOP
            context.startService(intent)
            refreshActiveState()
            navController.popBackStack()
        } else {
            if (selectedLocation != null && essentials?.foodRescue != null) {
                Prefs.activateFoodRescue(context, essentials!!, selectedLocation!!)
                val intent = Intent(context, com.application.jomato.service.FoodRescueService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                refreshActiveState()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshActiveState()
        loadLocations()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) checkBatteryAndProceed(context, { showBatteryDialog = true }) { toggleSubscription() }
    }

    fun onMainButtonClick() {
        if (activeState != null) {
            toggleSubscription()
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkBatteryAndProceed(context, { showBatteryDialog = true }) { toggleSubscription() }
            }
        } else {
            checkBatteryAndProceed(context, { showBatteryDialog = true }) { toggleSubscription() }
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Power Settings") },
            text = { Text("To monitor in the background, please allow unrestricted battery usage.") },
            confirmButton = {
                Button(onClick = {
                    showBatteryDialog = false
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }, colors = ButtonDefaults.buttonColors(containerColor = BrandColor)) { Text("Open Settings") }
            }
        )
    }

    if (showLocationDialog) {
        Dialog(
            onDismissRequest = { if (selectedLocation != null) showLocationDialog = false else navController.popBackStack() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp) // Padding from screen edges
                    .heightIn(max = 600.dp),
                shape = RoundedCornerShape(20.dp),
                color = CardWhite,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Zone", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        if (locations.isNotEmpty()) {
                            Text("${locations.size} available", fontSize = 12.sp, color = MutedText)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    if (isLoadingLocations) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandColor)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(locations) { loc ->
                                LocationRowItem(loc) {
                                    selectedLocation = loc
                                    showLocationDialog = false
                                    fetchEssentials(loc)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Food Rescue Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PageBackground)
            )
        }
    ) { padding ->
        val loc = activeState?.location ?: selectedLocation
        val isMonitoring = activeState != null

        if (loc != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

                    LocationSelector(loc) { if (!isMonitoring) showLocationDialog = true }

                    if (isMonitoring) {
                        ActiveStatusCard(activeState!!)
                    } else {
                        InactiveStatusCard()
                    }
                }

                val btnColor = if (isMonitoring) DarkText else BrandColor
                val btnText = if (isMonitoring) "STOP MONITORING" else "START MONITORING"

                Button(
                    onClick = { onMainButtonClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnColor,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
                ) {
                    Text(
                        text = btnText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}


@Composable
fun LocationSelector(location: UserLocation, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = CardWhite,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Place, null, tint = BrandColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Monitoring Zone", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText, letterSpacing = 0.5.sp)
                Text(location.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = MutedText)
        }
    }
}

@Composable
fun ActiveStatusCard(state: FoodRescueState) {
    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(50)) // Light Green Pill
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                Spacer(modifier = Modifier.width(8.dp))
                Text("LIVE UPDATES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big Number with subtle pulse background
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = state.totalCancelledMessages.toString(),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black, // Extra Heavy
                    color = BrandColor,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.scale(pulseScale)
                )
            }

            Text("Cancellation Messages Received", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DarkText)

            Spacer(modifier = Modifier.height(32.dp))
            Divider(color = PageBackground, thickness = 2.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MutedText, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Claimed nearby: ${state.totalClaimedMessages}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MutedText)
            }
        }
    }
}

@Composable
fun InactiveStatusCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PageBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.RestaurantMenu, null, modifier = Modifier.size(32.dp), tint = MutedText)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Ready to Start", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "System is idle. Select a zone and start\nmonitoring to catch orders.",
                textAlign = TextAlign.Center,
                color = MutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun LocationRowItem(loc: UserLocation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PageBackground,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LocationOn, null, tint = DarkText, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(loc.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
            Text(loc.fullAddress, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MutedText, fontSize = 13.sp)
        }

        Icon(Icons.Rounded.ChevronRight, null, tint = MutedText)
    }
}

fun checkBatteryAndProceed(context: Context, onShowDialog: () -> Unit, onSuccess: () -> Unit) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) onShowDialog() else onSuccess()
    } else {
        onSuccess()
    }
}