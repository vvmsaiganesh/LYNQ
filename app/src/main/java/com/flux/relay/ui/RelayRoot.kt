package com.flux.relay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.EmergencyShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.screens.AssistantScreen
import com.flux.relay.ui.screens.ChatsScreen
import com.flux.relay.ui.screens.OnboardingScreen
import com.flux.relay.ui.screens.RadarScreen
import com.flux.relay.ui.screens.SettingsScreen
import com.flux.relay.ui.screens.SosScreen
import com.flux.relay.ui.screens.ThreadScreen
import com.flux.relay.ui.theme.Ink
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.TextMuted

/**
 * Top-level shell. Three states:
 *  1. No identity yet          -> onboarding (key generation happens here)
 *  2. Identity, no permission  -> permission gate (radios are useless without consent)
 *  3. Everything ready         -> tabbed console with an SOS action always in reach
 *
 * The bottom bar is hidden on the thread and SOS screens: those are full-attention views.
 */
@Composable
fun RelayRoot(vm: RelayViewModel) {
    if (vm.identity == null) {
        OnboardingScreen(onCreate = vm::createIdentity)
        return
    }

    PermissionGate(onGranted = vm::startMesh) {
        val screen = vm.screen
        BackHandler(enabled = screen !is Screen.Radar) { vm.back() }

        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .safeDrawingPadding(),
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
                modifier = Modifier.weight(1f),
            ) { target ->
                when (target) {
                    is Screen.Radar -> RadarScreen(vm)
                    is Screen.Chats -> ChatsScreen(vm)
                    is Screen.Thread -> ThreadScreen(vm, target.peerId)
                    is Screen.Sos -> SosScreen(vm)
                    is Screen.Assistant -> AssistantScreen(vm)
                    is Screen.Settings -> SettingsScreen(vm)
                }
            }

            if (screen !is Screen.Thread && screen !is Screen.Sos && screen !is Screen.Assistant) {
                BottomBar(
                    current = screen,
                    onSelect = { vm.navigate(it) },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    Column {
        HairlineDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .background(Surface),
        ) {
            TabItem(
                icon = Icons.Rounded.WifiTethering,
                label = "MESH",
                active = current is Screen.Radar,
                accent = MeshGreen,
                onClick = { onSelect(Screen.Radar) },
            )
            TabItem(
                icon = Icons.Rounded.ChatBubbleOutline,
                label = "MESSAGES",
                active = current is Screen.Chats,
                accent = MeshGreen,
                onClick = { onSelect(Screen.Chats) },
            )
            TabItem(
                icon = Icons.Rounded.EmergencyShare,
                label = "SOS",
                active = false,
                accent = SosRed,
                onClick = { onSelect(Screen.Sos) },
            )
            TabItem(
                icon = Icons.Rounded.Settings,
                label = "SETTINGS",
                active = current is Screen.Settings,
                accent = MeshGreen,
                onClick = { onSelect(Screen.Settings) },
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = if (active) accent else TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp)
                .background(
                    if (active) accent.copy(alpha = 0.14f) else Color.Transparent,
                    RoundedCornerShape(50),
                ),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
