package com.application.jomato.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.application.jomato.config.AssetResolver
import com.application.jomato.ui.theme.JomatoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(feature: JomatoFeature, onClick: () -> Unit, onEntityClick: (() -> Unit)? = null) {
    val warning = JomatoTheme.Warning
    val borderColor = when {
        !feature.healthy -> warning.copy(alpha = 0.3f)
        else -> JomatoTheme.Divider
    }

    Card(
        onClick = { if (!feature.isDimmed) onClick() },
        colors = CardDefaults.cardColors(containerColor = JomatoTheme.Background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureIcon(feature)
                Spacer(modifier = Modifier.width(12.dp))
                FeatureContent(feature, onEntityClick = onEntityClick, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TrailingIndicator(feature, warning)
            }

            if (feature.message.isNotBlank() && (feature.disabled || !feature.healthy)) {
                MaintenanceStrip(
                    message = feature.message,
                    warningColor = if (feature.disabled) JomatoTheme.TextGray else warning
                )
            }
        }
    }
}

// ── Icon ─────────────────────────────────────────────────────────────────────

@Composable
private fun FeatureIcon(feature: JomatoFeature) {
    val context = LocalContext.current
    val tint = if (feature.isDimmed) JomatoTheme.TextGray else JomatoTheme.Brand
    val bg = if (feature.isDimmed) JomatoTheme.TextGray.copy(alpha = 0.06f) else JomatoTheme.BrandLight

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        val fallback: @Composable () -> Unit = {
            Icon(Icons.Filled.Info, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }

        if (!feature.iconLink.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AssetResolver.urlPrimary(feature.iconLink))
                    .decoderFactory(SvgDecoder.Factory())
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(tint),
                loading = { fallback() },
                error = { fallback() }
            )
        } else {
            fallback()
        }
    }
}

// ── Title + description + badges ─────────────────────────────────────────────

@Composable
private fun FeatureContent(feature: JomatoFeature, onEntityClick: (() -> Unit)?, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (feature.isDimmed) JomatoTheme.TextGray else JomatoTheme.BrandBlack,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            EntityPill(feature, onEntityClick)
            if (feature.isNew && !feature.isDimmed) NewBadge()
        }
        Text(
            text = feature.description,
            style = MaterialTheme.typography.bodySmall,
            color = JomatoTheme.TextGray,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EntityPill(feature: JomatoFeature, onEntityClick: (() -> Unit)?) {
    val entity = feature.entity ?: return
    val info = entity.info
    val tappable = onEntityClick != null
    val pillModifier = if (tappable) {
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onEntityClick!!)
    } else {
        Modifier
    }
    if (info != null) {
        Surface(color = info.brandColor, shape = RoundedCornerShape(4.dp), modifier = pillModifier) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = if (tappable) 2.dp else 4.dp, top = 1.dp, bottom = 1.dp)
            ) {
                Text(
                    text = info.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp
                )
                if (tappable) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = pillModifier.then(Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
        ) {
            Text(
                text = entity.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = JomatoTheme.TextGray,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
            if (tappable) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = JomatoTheme.TextGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

// ── Trailing indicator ───────────────────────────────────────────────────────

@Composable
private fun TrailingIndicator(feature: JomatoFeature, warningColor: Color) {
    when {
        feature.disabled -> { /* no trailing — dimmed card with no arrow makes it clear */ }
        feature.comingSoon -> ComingSoonBadge()
        !feature.healthy -> Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(16.dp)
        )
        else -> Icon(
            Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = JomatoTheme.TextGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── Maintenance strip (only case the card expands vertically) ────────────────

@Composable
private fun MaintenanceStrip(message: String, warningColor: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(JomatoTheme.Divider)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = warningColor.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Badges ───────────────────────────────────────────────────────────────────

@Composable
fun NewBadge() {
    Surface(
        color = JomatoTheme.Brand.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = "NEW",
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = JomatoTheme.Brand,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ComingSoonBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = JomatoTheme.TextGray,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "SOON",
            style = MaterialTheme.typography.labelSmall,
            color = JomatoTheme.TextGray,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}
