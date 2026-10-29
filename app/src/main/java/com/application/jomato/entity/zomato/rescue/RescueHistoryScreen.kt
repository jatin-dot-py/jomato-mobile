package com.application.jomato.entity.zomato.rescue

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.ui.theme.JomatoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())

private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}

/** The deal is still fresh enough that nobody can say it was missed yet. */
private val LIVE_GRACE_MS = RescueHistoryStore.MISS_WINDOW_MS

private enum class DealStatus { CLAIMED, OPENED_NOT_CLAIMED, MISSED, LIVE }

private fun statusOf(entry: RescueHistoryEntry, now: Long): DealStatus = when {
    entry.claimed -> DealStatus.CLAIMED
    now - entry.eventTime <= LIVE_GRACE_MS -> DealStatus.LIVE
    entry.opened -> DealStatus.OPENED_NOT_CLAIMED
    else -> DealStatus.MISSED
}

/**
 * On-device ledger of every food rescue deal this device saw, and what happened to it:
 * claimed, opened but not claimed, or never opened at all.
 */
@Composable
fun RescueHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<RescueHistoryEntry>>(emptyList()) }
    var stats by remember { mutableStateOf(RescueHistoryStats()) }
    var showClearDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            RescueHistoryStore.entries(context) to RescueHistoryStore.stats(context)
        }
        entries = loaded.first
        stats = loaded.second
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = JomatoTheme.Background,
            title = { Text("Clear rescue history?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes the local log of deals and savings. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    RescueHistoryStore.clear(context)
                    entries = emptyList()
                    stats = RescueHistoryStats()
                    showClearDialog = false
                }) {
                    Text("Clear", color = JomatoTheme.Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = JomatoTheme.BrandBlack
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rescue history",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = JomatoTheme.BrandBlack
            )
            Spacer(modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear history",
                        tint = JomatoTheme.TextGray,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JomatoTheme.Brand)
            }

            entries.isEmpty() -> EmptyHistory(Modifier.weight(1f))

            else -> {
                StatsRow(stats)
                Spacer(modifier = Modifier.height(16.dp))

                val now = System.currentTimeMillis()
                val grouped = entries.groupBy { DATE_FORMAT.format(Date(it.eventTime)) }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    grouped.forEach { (date, dayEntries) ->
                        item(key = "date-$date") {
                            Text(
                                text = date.uppercase(Locale.getDefault()),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = JomatoTheme.TextGray.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                            )
                        }
                        items(dayEntries, key = { it.id }) { entry ->
                            HistoryRow(entry = entry, status = statusOf(entry, now))
                        }
                    }
                    item(key = "footnote") {
                        Text(
                            text = "Stored on this device only. Missed value only counts deals " +
                                "where Zomato included a price in the event.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = JomatoTheme.TextGray.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(stats: RescueHistoryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            label = "CLAIMED",
            value = stats.claimedCount.toString(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "SAVED",
            value = "₹${moneyFormat.format(stats.savedTotal)}",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "MISSED",
            value = stats.missedCount.toString(),
            hint = if (stats.missedSavings > 0) "₹${moneyFormat.format(stats.missedSavings)} lost" else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(JomatoTheme.SecondaryBg)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = JomatoTheme.TextGray.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = JomatoTheme.BrandBlack,
            maxLines = 1
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 9.sp,
                color = JomatoTheme.Warning,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: RescueHistoryEntry, status: DealStatus) {
    val context = LocalContext.current
    val deeplink = entry.deeplink

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JomatoTheme.SecondaryBg)
            .then(
                if (deeplink != null) {
                    Modifier.clickable { openInZomato(context, deeplink) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.restaurantName ?: "Food rescue deal",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = JomatoTheme.BrandBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (deeplink != null) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Open in Zomato",
                    tint = JomatoTheme.TextGray,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            StatusChip(status)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = priceLabel(entry),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = JomatoTheme.BrandBlack
            )
            Text(
                text = buildString {
                    append("  ·  ").append(TIME_FORMAT.format(Date(entry.eventTime)))
                    if (!entry.notified) append("  ·  no alert sent")
                },
                fontSize = 11.sp,
                color = JomatoTheme.TextGray
            )
        }

        if (entry.items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = itemsLabel(entry.items),
                fontSize = 11.sp,
                color = JomatoTheme.TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusChip(status: DealStatus) {
    val text: String
    val color: androidx.compose.ui.graphics.Color
    when (status) {
        DealStatus.CLAIMED -> {
            text = "CLAIMED"
            color = JomatoTheme.Success
        }
        DealStatus.OPENED_NOT_CLAIMED -> {
            text = "MISSED · OPENED"
            color = JomatoTheme.Warning
        }
        DealStatus.MISSED -> {
            text = "MISSED"
            color = JomatoTheme.Error
        }
        DealStatus.LIVE -> {
            text = "OPEN"
            color = JomatoTheme.TextGray
        }
    }
    Text(
        text = text,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

private fun priceLabel(entry: RescueHistoryEntry): String {
    val catalog = entry.catalogTotal
    val rescue = entry.finalCost
    return when {
        entry.claimed && rescue != null && catalog != null ->
            "Paid ₹${moneyFormat.format(rescue)} · was ₹${moneyFormat.format(catalog)}"
        entry.claimed && rescue != null -> "Paid ₹${moneyFormat.format(rescue)}"
        rescue != null && catalog != null ->
            "₹${moneyFormat.format(rescue)} · was ₹${moneyFormat.format(catalog)}"
        rescue != null -> "₹${moneyFormat.format(rescue)}"
        else -> "Price not recorded"
    }
}

/**
 * Re-opening a logged deal is best-effort: Zomato may have taken the link down, in
 * which case nothing happens rather than a crash.
 */
private fun openInZomato(context: Context, deeplink: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(deeplink))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // No handler / stale link - the row is informational only.
    }
}

private fun itemsLabel(items: List<String>): String {
    val shown = items.take(2).joinToString(", ")
    return if (items.size > 2) "$shown +${items.size - 2} more" else shown
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(JomatoTheme.SecondaryBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = JomatoTheme.TextGray,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No rescue history yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = JomatoTheme.BrandBlack
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Every deal you are alerted about is logged here, " +
                "along with whether you claimed, opened or missed it.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            color = JomatoTheme.TextGray,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
