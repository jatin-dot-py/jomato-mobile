package com.application.jomato.entity.zomato.rescue

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.application.jomato.BuildConfig
import com.application.jomato.entity.zomato.ZomatoManager
import com.application.jomato.entity.zomato.api.OrderDetails
import com.application.jomato.entity.zomato.service.FoodRescueService
import com.application.jomato.ui.theme.JomatoTheme
import java.text.NumberFormat
import java.util.Locale

private fun formatRupee(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.maximumFractionDigits = 0
    nf.minimumFractionDigits = 0
    return "₹${nf.format(amount)}"
}

@Composable
fun RescueActiveView(
    state: FoodRescueState,
    onStopClick: () -> Unit
) {
    val context = LocalContext.current
    val claimedOrders = remember { ZomatoManager.getFrClaimedOrders(context) }
    val totalSaved = remember { ZomatoManager.getFrTotalSaved(context) }
    val hasClaims = claimedOrders.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (hasClaims) {
            MonitoringHeader(
                address = state.location.fullAddress,
                onStop = onStopClick
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (hasClaims) {
                SavingsHero(
                    totalSaved = totalSaved,
                    claimedCount = claimedOrders.size
                )
                Spacer(modifier = Modifier.height(16.dp))
                RecentClaimsSection(orders = claimedOrders)
            } else {
                EmptyClaimsView(
                    locationName = state.location.name,
                    onStop = onStopClick
                )
            }
        }
    }
}


// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun MonitoringHeader(address: String, onStop: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(pulseScale)
                    .graphicsLayer { alpha = pulseAlpha }
                    .clip(CircleShape)
                    .background(JomatoTheme.Brand)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(JomatoTheme.Brand)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = address,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = JomatoTheme.TextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        FilledTonalButton(
            onClick = onStop,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = JomatoTheme.Brand.copy(alpha = 0.1f),
                contentColor = JomatoTheme.Brand
            ),
            modifier = Modifier.height(32.dp)
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Savings hero ──────────────────────────────────────────────────────────────

@Composable
private fun SavingsHero(totalSaved: Double, claimedCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JomatoTheme.Brand.copy(alpha = 0.08f))
            .border(1.dp, JomatoTheme.Brand.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TOTAL SAVED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = JomatoTheme.Brand.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatRupee(totalSaved),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = JomatoTheme.Brand,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$claimedCount ${if (claimedCount == 1) "order claimed" else "orders claimed"}",
                fontSize = 13.sp,
                color = JomatoTheme.TextGray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Recent claims ─────────────────────────────────────────────────────────────

@Composable
private fun RecentClaimsSection(orders: List<OrderDetails>) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "RECENT CLAIMS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = JomatoTheme.TextGray.copy(alpha = 0.5f),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        orders.forEachIndexed { index, order ->
            ClaimedOrderRow(order = order)
            if (index < orders.lastIndex) {
                Divider(
                    color = JomatoTheme.Divider,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        if (BuildConfig.IS_DEV) {
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(
                onClick = {
                    ZomatoManager.clearClaimedOrders(context)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Clear Claim History",
                    fontSize = 12.sp,
                    color = JomatoTheme.TextGray.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ClaimedOrderRow(order: OrderDetails) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(order.restaurantImageUrl)
                .crossfade(true)
                .build(),
            contentDescription = order.restaurantName,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            loading = {
                Box(Modifier.fillMaxSize().background(JomatoTheme.Divider))
            },
            error = {
                Box(Modifier.fillMaxSize().background(JomatoTheme.Divider))
            }
        )

        Column(modifier = Modifier.weight(1f)) {

            // Restaurant name + savings badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = order.restaurantName ?: "Unknown Restaurant",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = JomatoTheme.BrandBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (order.cartTotal != null && order.paidAmount != null) {
                    val saved = order.cartTotal - order.paidAmount
                    if (saved > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(JomatoTheme.Brand.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "saved ${formatRupee(saved)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JomatoTheme.Brand
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Price row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (order.cartTotal != null && order.paidAmount != null && order.cartTotal != order.paidAmount) {
                    Text(
                        text = formatRupee(order.cartTotal),
                        fontSize = 12.sp,
                        color = JomatoTheme.TextGray.copy(alpha = 0.5f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                if (order.paidAmount != null) {
                    Text(
                        text = formatRupee(order.paidAmount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = JomatoTheme.BrandBlack
                    )
                }
            }

            // Items — one per line
            if (order.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                order.items.take(3).forEach { item ->
                    Text(
                        text = "· $item",
                        fontSize = 12.sp,
                        color = JomatoTheme.TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
                if (order.items.size > 3) {
                    Text(
                        text = "+${order.items.size - 3} more",
                        fontSize = 11.sp,
                        color = JomatoTheme.TextGray.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyClaimsView(locationName: String, onStop: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JomatoTheme.Brand.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = JomatoTheme.Brand,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Monitoring Active",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = JomatoTheme.BrandBlack
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Watching for cancelled orders\nnear $locationName.",
            fontSize = 14.sp,
            color = JomatoTheme.TextGray,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = JomatoTheme.Brand,
                contentColor = JomatoTheme.Background
            )
        ) {
            Text(
                "STOP MONITORING",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

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
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                JomatoTheme.TextGray.copy(alpha = 0.2f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = JomatoTheme.TextGray
            )
        ) {
            Text(
                "SEND TEST NOTIFICATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
