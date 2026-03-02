package com.application.jomato.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.application.jomato.sessions.BaseSession
import com.application.jomato.sessions.Entity
import com.application.jomato.ui.theme.JomatoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AccountManagerDialog(
    entity: Entity,
    onDismiss: () -> Unit,
    onSessionSelected: ((String) -> Unit)? = null,
    onAddAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(entity.sessionManager.listSessions(context)) }
    val isPicker = onSessionSelected != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = JomatoTheme.Background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPicker) "Choose an account" else "Your accounts",
                        style = MaterialTheme.typography.titleMedium,
                        color = JomatoTheme.BrandBlack,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = JomatoTheme.TextGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No accounts connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = JomatoTheme.TextGray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        sessions.forEachIndexed { index, session ->
                            SessionRow(
                                session = session,
                                selectable = isPicker,
                                onSelect = { onSessionSelected?.invoke(session.sessionId) },
                                onDelete = {
                                    val name = session.displayName
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            entity.logoutHandler.logout(context, session)
                                        }
                                        sessions = entity.sessionManager.listSessions(context)
                                        Toast.makeText(context, "$name removed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            if (index < sessions.lastIndex) {
                                Divider(
                                    color = JomatoTheme.Divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 72.dp, end = 20.dp)
                                )
                            }
                        }
                    }
                }

                val canAdd = sessions.size < entity.maxAccounts && entity.isEnabled
                if (canAdd) {
                    Divider(
                        color = JomatoTheme.Divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    AddAccountRow(onClick = onAddAccount)
                }
            }
        }
    }
}

// ── Avatar ───────────────────────────────────────────────────────────────────

@Composable
private fun AccountAvatar(name: String, size: Int = 40) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(JomatoTheme.Brand),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size / 2.2).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

// ── Session row ──────────────────────────────────────────────────────────────

@Composable
private fun SessionRow(
    session: BaseSession,
    selectable: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(session.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(session.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selectable) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AccountAvatar(name = session.displayName)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = JomatoTheme.BrandBlack,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = "Added $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = JomatoTheme.TextGray,
                fontSize = 12.sp
            )
        }

        Text(
            text = "Remove",
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = JomatoTheme.TextGray,
            fontSize = 12.sp
        )
    }
}

// ── Add account row ──────────────────────────────────────────────────────────

@Composable
private fun AddAccountRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(JomatoTheme.Divider),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = JomatoTheme.TextGray,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = "Add another account",
            style = MaterialTheme.typography.bodyMedium,
            color = JomatoTheme.Brand,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
