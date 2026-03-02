package com.application.jomato.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.config.UiConfigManager
import com.application.jomato.ui.theme.JomatoTheme

private const val FALLBACK_FEATURE_URL = "https://github.com/jatin-dot-py/jomato-mobile/issues/new?title=%5BFeature%20Request%5D%20Short%20description%20here%20&body=Describe%20the%20feature."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportRow(navController: NavController) {
    val context = LocalContext.current
    val url = UiConfigManager.config?.metadata?.requestFeatureUrl?.takeIf { it.isNotBlank() } ?: FALLBACK_FEATURE_URL
    Card(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        colors = CardDefaults.cardColors(containerColor = JomatoTheme.Background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, JomatoTheme.Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = JomatoTheme.Brand,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Request a Feature",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = JomatoTheme.BrandBlack,
                    fontSize = 14.sp
                )
                Text(
                    text = "Opens a prefilled GitHub issue",
                    style = MaterialTheme.typography.bodySmall,
                    color = JomatoTheme.TextGray,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = JomatoTheme.TextGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyFaqRow(navController: NavController) {
    Card(
        onClick = { navController.navigate("privacy_faqs") },
        colors = CardDefaults.cardColors(containerColor = JomatoTheme.Background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, JomatoTheme.Divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.QuestionAnswer,
                contentDescription = null,
                tint = JomatoTheme.Brand,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Privacy & FAQs",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = JomatoTheme.BrandBlack,
                    fontSize = 14.sp
                )
                Text(
                    text = "Data and privacy questions",
                    style = MaterialTheme.typography.bodySmall,
                    color = JomatoTheme.TextGray,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = JomatoTheme.TextGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
