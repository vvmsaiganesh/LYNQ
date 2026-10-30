package com.flux.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flux.relay.ai.AiChat
import com.flux.relay.ui.theme.GlassBlue
import com.flux.relay.ui.theme.GlassEdge
import com.flux.relay.ui.theme.GlassFill
import com.flux.relay.ui.theme.GlassStroke
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.TextMuted

/**
 * The floating glass assistant entry point. A small blue frosted circle pinned
 * bottom-right on every screen; tapping it expands this Gemini-style chat sheet.
 * The assistant answers on-device when offline (summary, urgency, translation,
 * alert priority) and through the hosted model when the internet is available.
 */
@Composable
fun GlassAssistant(
    expanded: Boolean,
    bubbles: List<AiChat.Bubble>,
    busy: Boolean,
    onlineAnswer: Boolean,
    onOpen: () -> Unit,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (expanded) {
            ChatSheet(
                bubbles = bubbles,
                busy = busy,
                onlineAnswer = onlineAnswer,
                onSend = onSend,
                onDismiss = onClose,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f),
            )
        } else {
            GlassFab(
                onOpen = onOpen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun GlassFab(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(58.dp)
            .background(
                Brush.linearGradient(listOf(Color(0x660FA3E0), Color(0x662962A8))),
                RoundedCornerShape(50),
            )
            .border(1.dp, GlassEdge, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onOpen),
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = "Ask LYNQ",
            tint = Color(0xFFD6ECFF),
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun ChatSheet(
    bubbles: List<AiChat.Bubble>,
    busy: Boolean,
    onlineAnswer: Boolean,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    LaunchedEffect(bubbles.size, busy) {
        if (bubbles.isNotEmpty()) list.animateScrollToItem(bubbles.size - 1)
    }

    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xF2172A4A), Color(0xF20D1B33))),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            )
            .border(1.dp, GlassStroke, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .background(GlassBlue.copy(alpha = 0.35f), RoundedCornerShape(50)),
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFD6ECFF),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("LYNQ Assistant", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    when {
                        busy -> "thinking…"
                        onlineAnswer -> "hosted model · online"
                        else -> "on-device · works offline"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        LazyColumn(
            state = list,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (bubbles.isEmpty()) {
                item { EmptyState() }
                item {
                    Suggestions { s ->
                        onSend(s)
                    }
                }
            }
            items(bubbles, key = { it.id }) { bubble ->
                BubbleRow(bubble)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message LYNQ…", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GlassBlue,
                    unfocusedBorderColor = GlassStroke,
                    focusedContainerColor = GlassFill,
                    unfocusedContainerColor = GlassFill,
                    cursorColor = GlassBlue,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (draft.isNotBlank() && !busy) GlassBlue else GlassBlue.copy(alpha = 0.35f),
                        RoundedCornerShape(50),
                    )
                    .clickable(enabled = draft.isNotBlank() && !busy) {
                        onSend(draft)
                        draft = ""
                    },
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .background(GlassBlue.copy(alpha = 0.25f), RoundedCornerShape(50)),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFD6ECFF),
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            "Hi, how can I help you?",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Text(
            "Summaries · urgency · translation · alert priority",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
    }
}

@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        listOf(
            "Summarise recent messages",
            "Who needs help most?",
            "What is urgent right now?",
            "How is the battery doing?",
        ).forEach { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassFill, RoundedCornerShape(14.dp))
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                    .clickable { onPick(text) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GlassBlue,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6ECFF))
            }
        }
    }
}

@Composable
private fun BubbleRow(bubble: AiChat.Bubble) {
    val mine = bubble.author == AiChat.Author.USER
    Row(
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) GlassBlue.copy(alpha = 0.45f) else GlassFill,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                )
                .border(1.dp, if (mine) GlassEdge else GlassStroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (bubble.pending) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        color = TextMuted,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("thinking…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            } else {
                Column {
                    Text(bubble.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    if (bubble.sourceLabel != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            bubble.sourceLabel ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bubble.sourceLabel == "Featherless") MeshGreen else TextMuted,
                        )
                    }
                }
            }
        }
    }
}