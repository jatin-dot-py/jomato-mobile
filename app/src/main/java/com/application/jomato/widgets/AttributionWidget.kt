package com.application.jomato.widgets
import androidx.compose.ui.graphics.ColorFilter
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.decode.SvgDecoder
import com.application.jomato.config.AssetResolver
import com.application.jomato.ui.theme.JomatoTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class AttributionWidget : BaseWidget() {

    override val type: String = "jomato_attribution"

    @Composable
    override fun Display(payload: JsonElement) {
        val data = runCatching {
            Json.decodeFromJsonElement<AttributionPayload>(payload)
        }.getOrNull() ?: return
        if (data.messages.isEmpty()) return

        val context = LocalContext.current
        var currentIndex by remember { mutableIntStateOf(0) }
        val message = data.messages[currentIndex % data.messages.size]
        val duration = data.message_duration.coerceAtLeast(1000)

        LaunchedEffect(message, duration) {
            delay(duration)
            currentIndex++
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(message.onclick_action_link))
                    )
                },
            color = JomatoTheme.SecondaryBg,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(AssetResolver.urlPrimary(message.icon_link))
                        .decoderFactory(SvgDecoder.Factory())
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(JomatoTheme.BrandBlack),
                    error = {
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = JomatoTheme.Brand
                        )
                    }
                )
                                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = JomatoTheme.BrandBlack,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Serializable
private data class AttributionPayload(
    val messages: List<AttributionMessage> = emptyList(),
    val message_duration: Long = 5000
)

@Serializable
private data class AttributionMessage(
    val text: String = "",
    val onclick_action_link: String = "",
    val icon_link: String = ""
)
