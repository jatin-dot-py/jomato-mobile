package com.application.jomato.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.ui.theme.JomatoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: JomatoFeature, isActive: Boolean, onClick: () -> Unit) {
    Card(
        onClick = { if (!feature.comingSoon) onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (feature.comingSoon) JomatoTheme.SecondaryBg else JomatoTheme.Background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = JomatoTheme.Divider
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (feature.comingSoon)
                            JomatoTheme.TextGray.copy(alpha = 0.1f)
                        else
                            JomatoTheme.Brand.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    feature.icon,
                    null,
                    tint = if (feature.comingSoon) JomatoTheme.TextGray else JomatoTheme.Brand,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    feature.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (feature.comingSoon) JomatoTheme.TextGray else JomatoTheme.BrandBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JomatoTheme.TextGray,
                    fontSize = 14.sp
                )
            }
            if (feature.comingSoon) {
                ComingSoonBadge()
            } else if (isActive) {
                LiveBadge()
            } else {
                Icon(
                    Icons.Rounded.ArrowForward,
                    null,
                    tint = JomatoTheme.TextGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Surface(
        color = JomatoTheme.Brand.copy(alpha = 0.1f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, JomatoTheme.Brand.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(JomatoTheme.Brand.copy(alpha = alpha))
            )
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = JomatoTheme.Brand,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun ComingSoonBadge() {
    Surface(
        color = JomatoTheme.SecondaryBg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, JomatoTheme.Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.Lock,
                null,
                tint = JomatoTheme.TextGray,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = "SOON",
                style = MaterialTheme.typography.labelSmall,
                color = JomatoTheme.TextGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}