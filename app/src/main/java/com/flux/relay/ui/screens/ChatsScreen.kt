package com.flux.relay.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flux.relay.data.MessageStatus
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.ThreadSummary
import com.flux.relay.ui.components.Avatar
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.formatWhen
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary

@Composable
fun ChatsScreen(vm: RelayViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("MESSAGES", style = MaterialTheme.typography.displayMedium)
        MonoLabel("${vm.threads.size} conversation${if (vm.threads.size == 1) "" else "s"} · stored on this device")
        Spacer(Modifier.height(16.dp))

        if (vm.threads.isEmpty()) {
            EmptyChats()
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.threads, key = { it.peerId }) { thread ->
                ThreadRow(thread) { vm.openThread(thread.peerId) }
                HairlineDivider()
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onClick: () -> Unit) {
    val accent = when {
        thread.isBroadcast -> SosRed
        thread.unread > 0 -> MeshGreen
        else -> RelayBlue
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Avatar(
            name = if (thread.isBroadcast) "!" else thread.title,
            color = accent,
            size = 44.dp,
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (thread.unread > 0) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier
                            .background(MeshGreen, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${thread.unread}",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.Black,
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = thread.last.body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val status = thread.last
            Text(
                text = when {
                    !status.outgoing -> "received · ${status.hops} hop${if (status.hops == 1) "" else "s"}"
                    status.status == MessageStatus.DELIVERED -> "delivered · ${status.hops} hops"
                    status.status == MessageStatus.RELAYED -> "relayed · ${status.hops} hops"
                    status.status == MessageStatus.SENT -> "sent"
                    else -> "queued"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (status.status == MessageStatus.DELIVERED) MeshGreen else TextMuted,
            )
        }
        Spacer(Modifier.size(10.dp))
        MonoLabel(formatWhen(thread.last.timestamp))
    }
}

@Composable
private fun EmptyChats() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a device from the radar, or send an SOS broadcast. " +
                "Everything is kept on this phone until it is delivered.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
