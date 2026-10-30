package com.flux.relay.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.EmergencyShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.Screen
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.MeshGraph
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.components.RadioPill
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.SurfaceRaised
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun RadarScreen(vm: RelayViewModel) {
    val banner = vm.banner
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(3200)
            vm.clearBanner()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RELAY", style = MaterialTheme.typography.displayMedium, color = MeshGreen)
                MonoLabel(
                    "node ${vm.identity?.fingerprint ?: "—"}  ·  ${vm.identity?.displayName.orEmpty().take(18)}",
                    color = TextMuted,
                )
            }
            RadioPill(vm.radio)
            IconButton(onClick = { vm.navigate(Screen.Assistant) }) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI triage", tint = RelayBlue)
            }
            IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary)
            }
        }

        AnimatedVisibility(visible = banner != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RelayBlue.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(banner.orEmpty(), style = MaterialTheme.typography.labelSmall, color = RelayBlue)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            MeshGraph(
                topology = vm.topology,
                radio = vm.radio,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("NODES", "${(vm.topology.nodes.size - 1).coerceAtLeast(0)}")
            Stat("HOPS", "${vm.topology.nodes.maxOfOrNull { it.hops } ?: 0}")
            Stat("QUEUED", "${vm.stats.queued}")
            Stat("HEARD", "${vm.stats.received}")
        }

        Spacer(Modifier.height(16.dp))
        HairlineDivider(color = SurfaceRaised)
        Spacer(Modifier.height(14.dp))

        SosButton(onClick = { vm.navigate(Screen.Sos) })

        Spacer(Modifier.height(14.dp))

        if (vm.online.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .padding(14.dp),
            ) {
                Column {
                    Text("No devices in range", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bring another phone within a few metres running Relay with its radios on. " +
                            "Messages will queue on this device until it appears.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else {
            MonoLabel("IN RANGE · tap to message")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.online, key = { it.fingerprint }) { peer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceRaised)
                            .clickable { vm.openThread(peer.fingerprint) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(MeshGreen, CircleShape),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = peer.name.take(12),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.size(6.dp))
                        MonoLabel(peer.fingerprint.take(4), color = TextMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        MonoLabel(label)
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(SosRed, Color(0xFFB3261E)),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Rounded.EmergencyShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text("BROADCAST SOS", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "reaches every node, hop by hop",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}
