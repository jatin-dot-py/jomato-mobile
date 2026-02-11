package com.application.jomato.ui.rescue

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.service.FoodRescueService

@Composable
fun RescueActiveView(
    state: FoodRescueState,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JomatoTheme.Brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = JomatoTheme.Brand,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Monitoring Active",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = JomatoTheme.BrandBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You will get a notification when there is a\ncancelled order nearby in ${state.location.name}.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = JomatoTheme.TextGray,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStopClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JomatoTheme.Brand,
                contentColor = JomatoTheme.Background
            )
        ) {
            Text(
                "STOP MONITORING",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val testIntent = Intent(context, FoodRescueService::class.java).apply {
                    action = FoodRescueService.ACTION_TEST
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(testIntent)
                } else {
                    context.startService(testIntent)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, JomatoTheme.TextGray.copy(alpha = 0.3f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JomatoTheme.TextGray)
        ) {
            Text(
                "SEND TEST NOTIFICATION",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}