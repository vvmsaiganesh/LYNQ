package com.flux.relay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.components.DeliveryLine
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.HopTrail
import com.flux.relay.ui.components.KindChip
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.formatClock
import com.flux.relay.ui.theme.Ink
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.SurfaceRaised
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary

@Composable
fun ThreadScreen(vm: RelayViewModel, peerId: String) {
    val messages = vm.activeMessages
    val peer = vm.knownPeers.firstOrNull { it.identity.fingerprint == peerId }
    val title = when {
        peerId == Message.BROADCAST -> "Nearby broadcast"
        peer != null -> peer.identity.displayName
        else -> messages.lastOrNull()?.peerName ?: peerId.take(6)
    }
    val listState = rememberLazyListState()

    var draft by remember { mutableStateOf("") }
    val send: () -> Unit = {
        if (draft.isNotBlank()) {
            vm.sendMessage(draft, peer)
            draft = ""
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (peerId != Message.BROADCAST) {
                        MonoLabel(peerId.take(12), color = TextMuted)
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(11.dp))
                        MonoLabel("E2E", color = MeshGreen)
                    } else {
                        MonoLabel("public · signed, not private", color = TextMuted)
                    }
                }
            }
            if (peer?.verified == true) {
                MonoLabel("VERIFIED", color = MeshGreen)
            }
        }
        HairlineDivider()

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Text("No messages yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Anything you send is queued here until a node is reachable, then relayed hop by hop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    Bubble(message = message, showSender = peerId == Message.BROADCAST && !message.outgoing)
                }
            }
        }

        HairlineDivider()
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message", color = TextMuted) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshGreen.copy(alpha = 0.6f),
                    unfocusedBorderColor = Hairline,
                    cursorColor = MeshGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Ink,
                    unfocusedContainerColor = Ink,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = send,
                enabled = draft.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MeshGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = SurfaceRaised,
                    disabledContentColor = TextMuted,
                ),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, showSender: Boolean) {
    val outgoing = message.outgoing
    val accent = when (message.kind) {
        MessageKind.SOS -> SosRed
        MessageKind.ALERT -> RelayBlue
        MessageKind.CHAT -> if (outgoing) MeshGreen else RelayBlue
    }
    val shape = if (outgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .background(if (outgoing) MeshGreen.copy(alpha = 0.10f) else SurfaceRaised)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (showSender) {
                MonoLabel(message.peerName.ifBlank { message.peerId.take(6) }, color = accent)
                Spacer(Modifier.height(4.dp))
            }
            if (message.kind != MessageKind.CHAT) {
                KindChip(message.kind)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeliveryLine(message)
                MonoLabel(formatClock(message.timestamp))
            }
            if (message.path.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HopTrail(message = message, selfLabel = if (outgoing) "you" else message.peerName)
            }
        }
    }
}
