package com.flux.relay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.formatRelative
import com.flux.relay.ui.theme.AlertAmber
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary

@Composable
fun SettingsScreen(vm: RelayViewModel) {
    val me = vm.identity
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(me?.displayName ?: "—", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(4.dp))
            MonoLabel("node ${me?.fingerprint ?: "—"}", color = MeshGreen)
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                MonoLabel("SAFETY CODE · compare out loud")
                Spacer(Modifier.height(6.dp))
                Text(me?.safetyCode ?: "—", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "If this matches on both phones, nobody is sitting in the middle. " +
                        "Keys are pinned the first time a device is seen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Low power mode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Scans in 8-second bursts every 50 seconds. Slower to find new nodes, " +
                            "far lighter on the battery. Messages still queue and still relay.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                Switch(
                    checked = vm.lowPower,
                    onCheckedChange = vm::setLowPowerMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AlertAmber,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Surface,
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))

            MonoLabel("THIS SESSION")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("${vm.stats.seen}", "heard")
                Metric("${vm.stats.forwarded}", "relayed")
                Metric("${vm.stats.received}", "delivered")
                Metric("${vm.stats.dropped}", "dropped")
            }

            Spacer(Modifier.height(24.dp))
            HairlineDivider(color = Hairline)
            Spacer(Modifier.height(18.dp))

            MonoLabel("KNOWN DEVICES · ${vm.knownPeers.size}")
            Spacer(Modifier.height(8.dp))
            if (vm.knownPeers.isEmpty()) {
                Text(
                    "No devices pinned yet. Once a peer is seen, its keys are stored here and " +
                        "cannot be silently replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                vm.knownPeers.forEach { peer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                peer.identity.displayName.ifBlank { "unnamed" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MonoLabel(peer.identity.fingerprint.take(10), color = TextMuted)
                                MonoLabel("seen ${formatRelative(peer.lastSeen)}", color = TextMuted)
                            }
                        }
                        if (peer.verified) {
                            TextButton(onClick = { vm.verifyPeer(peer.identity.fingerprint, false) }) {
                                Text("VERIFIED", color = MeshGreen, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            TextButton(onClick = { vm.verifyPeer(peer.identity.fingerprint, true) }) {
                                Text("VERIFY", color = RelayBlue, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = { vm.blockPeer(peer.identity.fingerprint, !peer.blocked) }) {
                            Text(
                                if (peer.blocked) "UNBLOCK" else "BLOCK",
                                color = if (peer.blocked) TextMuted else SosRed,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    HairlineDivider(color = Hairline.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(24.dp))
            HairlineDivider(color = Hairline)
            Spacer(Modifier.height(18.dp))

            MonoLabel("HOW THIS WORKS")
            Spacer(Modifier.height(10.dp))
            Fact("Transport", "Nearby Connections P2P cluster: Bluetooth LE/Classic and Wi-Fi Direct, no internet.")
            Fact("Routing", "Signed packets with a hop budget, per-node de-duplication and presence hints. Flooding is the fallback, never the default.")
            Fact("Delivery", "Store-and-forward. Every undelivered message is persisted with its exact wire bytes and retried when a node appears.")
            Fact("Crypto", "ECDSA P-256 signatures, ECDH P-256 + HKDF + AES-256-GCM bodies. Relays forward ciphertext they cannot read.")
            Fact("SOS", "Broadcasts are signed but deliberately readable by anyone in range: the audience is unknown and help matters more than secrecy.")

            Spacer(Modifier.height(28.dp))
            Text(
                "Relay · built at CBIT Hackathon for Team FLUX. No accounts, no telemetry, no server.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        MonoLabel(label)
    }
}

@Composable
private fun Fact(title: String, body: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
