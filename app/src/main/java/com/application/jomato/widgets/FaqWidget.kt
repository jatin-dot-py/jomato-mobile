package com.application.jomato.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.ui.theme.JomatoTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class FaqWidget : BaseWidget() {

    override val type: String = "jomato_privacy_faqs"

    @Composable
    override fun Display(payload: JsonElement) {
        val content = runCatching {
            Json.decodeFromJsonElement<FaqPayload>(payload).content
        }.getOrNull().orEmpty()
        if (content.isEmpty()) return
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            content.forEach { item ->
                Text(
                    text = item.question,
                    style = MaterialTheme.typography.titleSmall,
                    color = JomatoTheme.BrandBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = item.answer.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = JomatoTheme.TextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Serializable
private data class FaqPayload(val content: List<FaqItem> = emptyList())

@Serializable
private data class FaqItem(val question: String = "", val answer: String = "")
