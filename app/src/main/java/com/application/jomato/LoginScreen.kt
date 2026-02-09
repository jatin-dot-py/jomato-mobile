package com.application.jomato

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.api.ApiClient
import com.application.jomato.auth.AuthClient
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.application.jomato.ui.theme.JomatoTheme



@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showOtpScreen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var otpPreference by remember { mutableStateOf("sms") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = JomatoTheme.Background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = JomatoTheme.BrandBlack,
                    contentColor = JomatoTheme.Background,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(JomatoTheme.BrandLight, Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                AnimatedContent(
                    targetState = showOtpScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + slideInHorizontally { it } with
                                fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -it }
                    },
                    label = "TitleAnimation"
                ) { isOtp ->
                    Column {
                        if (!isOtp) {
                            // Title with Italic Style
                            Text(
                                "Jomato",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = JomatoTheme.Brand,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                "Unofficial API Client",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JomatoTheme.TextGray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Connect your Zomato account",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = JomatoTheme.BrandBlack
                            )
                        } else {
                            Text(
                                "Verification",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = JomatoTheme.BrandBlack
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Enter the OTP sent to ",
                                    color = JomatoTheme.TextGray,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "+91 $phoneNumber",
                                    color = JomatoTheme.BrandBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                IconButton(onClick = {
                                    showOtpScreen = false
                                    otp = ""
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, "Edit", tint = JomatoTheme.Brand, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = showOtpScreen,
                    transitionSpec = {
                        slideInHorizontally(initialOffsetX = { it }) + fadeIn() with
                                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                    },
                    label = "ContentAnimation"
                ) { isOtp ->
                    if (!isOtp) {
                        Column {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { if (it.length <= 10) phoneNumber = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = JomatoTheme.Divider,
                                    focusedBorderColor = JomatoTheme.Brand,
                                    cursorColor = JomatoTheme.Brand,
                                    focusedTextColor = JomatoTheme.BrandBlack,
                                    unfocusedTextColor = JomatoTheme.BrandBlack
                                ),
                                prefix = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("+91 ", fontWeight = FontWeight.SemiBold, color = JomatoTheme.BrandBlack)
                                        Box(
                                            modifier = Modifier
                                                .height(20.dp)
                                                .width(1.dp)
                                                .background(JomatoTheme.TextGray)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                },
                                placeholder = { Text("Phone Number", color = JomatoTheme.TextGray) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                "Receive OTP via",
                                style = MaterialTheme.typography.labelLarge,
                                color = JomatoTheme.TextGray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OtpMethodChip(
                                    selected = otpPreference == "sms",
                                    label = "SMS",
                                    icon = Icons.Default.Email,
                                    onClick = { otpPreference = "sms" }
                                )
                                OtpMethodChip(
                                    selected = otpPreference == "whatsapp",
                                    label = "WhatsApp",
                                    icon = Icons.Default.Whatsapp,
                                    onClick = { otpPreference = "whatsapp" }
                                )
                                OtpMethodChip(
                                    selected = otpPreference == "call",
                                    label = "Call",
                                    icon = Icons.Default.Call,
                                    onClick = { otpPreference = "call" }
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            JomatoButton(
                                text = "Get OTP",
                                enabled = phoneNumber.length == 10 && !isLoading,
                                onClick = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            FileLogger.log(context, "LoginScreen", "Initiating Pre-OTP for $phoneNumber via $otpPreference")
                                            val success = withContext(Dispatchers.IO) {
                                                AuthClient.preOtpFlow(context, phoneNumber, otpPreference)
                                            }
                                            if (success) {
                                                FileLogger.log(context, "LoginScreen", "Pre-OTP Success. Showing OTP input.")
                                                showOtpScreen = true
                                            } else {
                                                FileLogger.log(context, "LoginScreen", "Pre-OTP Failed.")
                                                snackbarHostState.showSnackbar("Failed to send OTP. Try again.")
                                            }
                                        } catch (e: Exception) {
                                            FileLogger.log(context, "LoginScreen", "Pre-OTP Exception", e)
                                            snackbarHostState.showSnackbar("Error: ${e.message}")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                "This is an unofficial client. Not affiliated with Zomato, Eternal ltd.",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = JomatoTheme.TextGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                            )
                        }

                    } else {
                        Column {
                            OutlinedTextField(
                                value = otp,
                                onValueChange = { if (it.length <= 6) otp = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = JomatoTheme.Divider,
                                    focusedBorderColor = JomatoTheme.Brand,
                                    focusedTextColor = JomatoTheme.BrandBlack,
                                    unfocusedTextColor = JomatoTheme.BrandBlack
                                ),
                                placeholder = { Text("Enter 6-digit OTP", color = JomatoTheme.TextGray) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                trailingIcon = {
                                    if(isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = JomatoTheme.Brand)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            JomatoButton(
                                text = "Access Account",
                                enabled = otp.length >= 4 && !isLoading,
                                onClick = {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            FileLogger.log(context, "LoginScreen", "Verifying OTP...")
                                            val result = withContext(Dispatchers.IO) {
                                                AuthClient.postOtpFlow(context, phoneNumber, otp)
                                            }
                                            if (result != null) {
                                                FileLogger.log(context, "LoginScreen", "OTP Verified. Fetching profile...")
                                                val user = withContext(Dispatchers.IO) { ApiClient.getUserInfo(context, result.accessToken) }
                                                if (user != null) {
                                                    FileLogger.log(context, "LoginScreen", "Login Complete. Welcome ${user.name}")
                                                    Prefs.saveTokenAndUser(context, result.accessToken, result.refreshToken, user.name, user.id.toString())
                                                    navController.navigate("dashboard") {
                                                        popUpTo("login") { inclusive = true }
                                                    }
                                                } else {
                                                    FileLogger.log(context, "LoginScreen", "Profile fetch failed after valid OTP.")
                                                    snackbarHostState.showSnackbar("Login successful, but profile load failed.")
                                                }
                                            } else {
                                                FileLogger.log(context, "LoginScreen", "OTP Verification failed.")
                                                snackbarHostState.showSnackbar("Invalid OTP.")
                                            }
                                        } catch (e: Exception) {
                                            FileLogger.log(context, "LoginScreen", "Post-OTP Exception", e)
                                            snackbarHostState.showSnackbar("Login Error: ${e.message}")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JomatoTheme.Brand)
                }
            }
        }
    }
}


@Composable
fun JomatoButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(elevation = if (enabled) 4.dp else 0.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = JomatoTheme.Brand,
            disabledContainerColor = JomatoTheme.Brand.copy(alpha = 0.3f),
            contentColor = JomatoTheme.Background,
            disabledContentColor = JomatoTheme.Background.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun OtpMethodChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val borderColor = if (selected) JomatoTheme.Brand else Color(0xFFE0E0E0)
    val bgColor = if (selected) JomatoTheme.BrandLight else Color.Transparent
    val contentColor = if (selected) JomatoTheme.Brand else JomatoTheme.TextGray

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        color = bgColor,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}