package com.flux.relay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.data.MessageStatus
import com.flux.relay.transport.RadioState
import com.flux.relay.ui.theme.AlertAmber
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.SurfaceRaised
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextMuted,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun RadioPill(state: RadioState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        RadioState.CONNECTED -> "LINKED" to MeshGreen
        RadioState.DISCOVERING -> "SCANNING" to RelayBlue
        RadioState.ADVERTISING -> "ADVERTISING" to RelayBlue
        RadioState.SLEEPING -> "LOW POWER" to AlertAmber
        RadioState.UNAVAILABLE -> "RADIO OFF" to SosRed
        RadioState.OFF -> "IDLE" to TextMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        MonoLabel(label, color = color)
    }
}

@Composable
fun KindChip(kind: MessageKind, modifier: Modifier = Modifier) {
    val (label, color) = when (kind) {
        MessageKind.SOS -> "SOS" to SosRed
        MessageKind.ALERT -> "ALERT" to AlertAmber
        MessageKind.CHAT -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Delivery state in words. Never a bare checkmark: in a blackout, "where is it" matters. */
@Composable
fun DeliveryLine(message: Message, modifier: Modifier = Modifier) {
    val (label, color) = when (message.status) {
        MessageStatus.PENDING -> "QUEUED · no node yet" to TextMuted
        MessageStatus.SENT -> "SENT · handed to 1 node" to RelayBlue
        MessageStatus.RELAYED -> "RELAYED · ${message.hops} hop${plural(message.hops)}" to RelayBlue
        MessageStatus.DELIVERED -> "DELIVERED · ${message.hops} hop${plural(message.hops)}" to MeshGreen
        MessageStatus.FAILED -> "FAILED" to SosRed
    }
    Text(
        text = if (message.outgoing) label else "RECEIVED · ${message.hops} hop${plural(message.hops)}",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

private fun plural(n: Int) = if (n == 1) "" else "s"

@Composable
fun Avatar(name: String, color: Color, size: Dp = 40.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(color.copy(alpha = 0.16f), CircleShape),
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        MonoLabel(label)
    }
}

/**
 * The hop trail: You -> relay -> recipient. Built from the path the ACK came back on,
 * so it is evidence from the network, not decoration.
 */
@Composable
fun HopTrail(
    message: Message,
    selfLabel: String,
    modifier: Modifier = Modifier,
) {
    if (message.path.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        val stops = buildList {
            add(selfLabel.take(8).ifBlank { "you" })
            message.path.forEach { add(it.take(6)) }
        }
        stops.forEachIndexed { index, stop ->
            if (index > 0) {
                Text("›", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Text(
                text = stop,
                style = MaterialTheme.typography.labelSmall,
                color = if (index == stops.lastIndex) MeshGreen else TextSecondary,
                modifier = Modifier
                    .background(SurfaceRaised, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
