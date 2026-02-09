package com.application.jomato.ui.rescue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.api.UserLocation
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun RescueLocationItem(
    location: UserLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) JomatoTheme.Brand else Color.Transparent
    val bgColor = if (isSelected) JomatoTheme.SecondaryBg else Color.Transparent
    val iconTint = if (isSelected) JomatoTheme.Brand else JomatoTheme.TextGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = JomatoTheme.BrandBlack
            )
            Text(
                text = location.fullAddress,
                fontSize = 13.sp,
                color = JomatoTheme.TextGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = JomatoTheme.Brand,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}