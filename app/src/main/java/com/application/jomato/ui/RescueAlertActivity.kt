package com.application.jomato.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.entity.zomato.rescue.FoodRescueAlerter
import com.application.jomato.entity.zomato.rescue.RescueAlertPlayer
import com.application.jomato.entity.zomato.rescue.RescueHistoryStore
import com.application.jomato.entity.zomato.service.FoodRescueService

/**
 * Full-screen takeover shown (also over the lock screen) when a rescue order appears.
 *
 * This is what makes the alert behave like an incoming call / alarm instead of a
 * notification that can be missed. It rings through [RescueAlertPlayer] and gives
 * the user exactly two actions: jump into Zomato, or dismiss.
 */
class RescueAlertActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { dismiss(openZomato = false, userInitiated = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showOverLockScreen()
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Idempotent: the service may have already started the ring.
        RescueAlertPlayer.start(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss(openZomato = false)
            }
        })

        setContent {
            MaterialTheme {
                AlertScreen(
                    onOpenZomato = { dismiss(openZomato = true) },
                    onDismiss = { dismiss(openZomato = false) }
                )
            }
        }

        // Never keep ringing / sitting on screen forever if nobody reacts.
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismissRunnable)
        RescueAlertPlayer.stop(this)
        super.onDestroy()
    }

    private fun dismiss(openZomato: Boolean, userInitiated: Boolean = true) {
        handler.removeCallbacks(autoDismissRunnable)

        // Only a real interaction counts as "opened" in the history. Being woken up by
        // the full-screen intent, or the 60s auto-dismiss, does not.
        if (userInitiated) {
            RescueHistoryStore.markOpened(this, intent?.getStringExtra(FoodRescueAlerter.EXTRA_EVENT_ID))
        }

        FoodRescueAlerter.dismiss(this)

        if (openZomato) {
            val launchIntent = packageManager.getLaunchIntentForPackage(FoodRescueService.TARGET_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(launchIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Zomato", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Zomato app not found on this device", Toast.LENGTH_SHORT).show()
            }
        }

        finishAndRemoveTask()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private companion object {
        const val AUTO_DISMISS_MS = 60_000L
    }
}

@Composable
private fun AlertScreen(onOpenZomato: () -> Unit, onDismiss: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rescuePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF7A2E3F), Color(0xFF2A151B), Color(0xFF140C0F))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(pulseScale)
                        .graphicsLayer { alpha = pulseAlpha }
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE23744)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "FOOD RESCUE ALERT",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "A cancelled order just went up for rescue. Claim it before someone else does.",
                color = Color(0xFFEDD9DE),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ringing for up to 45 seconds",
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onOpenZomato,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF7A2E3F)
                )
            ) {
                Text(
                    text = "OPEN ZOMATO",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = "DISMISS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}
