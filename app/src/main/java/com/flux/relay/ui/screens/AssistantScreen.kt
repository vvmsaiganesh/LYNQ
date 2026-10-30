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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flux.relay.ai.AiAssistant
import com.flux.relay.ai.OfflineTriage
import com.flux.relay.data.Message
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.KindChip
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.formatWhen
import com.flux.relay.ui.theme.AlertAmber
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.RelayBlue
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.SurfaceRaised
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary

private val LANGUAGES = listOf("English", "Hindi", "Telugu", "Tamil", "Spanish", "French", "Arabic")

/**
 * Optional AI dashboard. Ranks incoming traffic by urgency and offers summaries and
 * translation. Everything is advisory: the on-device classifier is always shown, and the
 * hosted model only ever adds to it.
 */
@Composable
fun AssistantScreen(vm: RelayViewModel) {
    val queue = vm.triageQueue()

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = RelayBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text("AI TRIAGE", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                MonoLabel(
                    when {
                        !vm.aiConfigured -> "on-device · no key"
                        vm.aiOnline -> "Featherless online"
                        else -> "offline · on-device"
                    },
                    color = if (vm.aiOnline && vm.aiConfigured) MeshGreen else AlertAmber,
                )
            }
            Box(
                Modifier
                    .background(
                        (if (vm.aiOnline && vm.aiConfigured) MeshGreen else AlertAmber).copy(alpha = 0.14f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (vm.aiOnline && vm.aiConfigured) MeshGreen else AlertAmber, CircleShape),
                    )
                    MonoLabel(if (vm.aiOnline && vm.aiConfigured) "ONLINE" else "LOCAL", color = TextSecondary)
                }
            }
        }

        HairlineDivider()

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                "Ranked by urgency",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Urgency is computed on this phone from the message text; it stays available with the radio " +
                    "off and no internet. Online, Featherless can rewrite the summary and translate it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            MonoLabel("TRANSLATE INTO")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LANGUAGES) { language ->
                    val active = vm.aiLanguage == language
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Color.Black else TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) RelayBlue else SurfaceRaised)
                            .clickable { vm.chooseAiLanguage(language) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            if (!vm.aiConfigured) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Add FEATHERLESS_API_KEY to local.properties to enable online summaries and translation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }

        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("Nothing to triage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Incoming messages appear here the moment they arrive over the mesh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                TextButton(
                    onClick = { queue.take(6).forEach { vm.analyze(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (vm.aiOnline && vm.aiConfigured) "Analyze + translate the top messages" else "Run on-device triage",
                        style = MaterialTheme.typography.labelSmall,
                        color = RelayBlue,
                    )
                }
            }
            items(queue, key = { it.id }) { message ->
                TriageCard(vm, message)
            }
            item {
                Text(
                    "AI output assists a responder; it never replaces emergency judgment. " +
                        "Nothing is uploaded unless you tap Analyze while online.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TriageCard(vm: RelayViewModel, message: Message) {
    val offline = remember(message.body, message.kind) {
        OfflineTriage.analyze(message.body, message.kind)
    }
    val outcome = vm.aiOutcomeFor(message.id)
    val urgency = outcome?.urgency ?: message.aiUrgency ?: offline.urgency
    val accent = urgencyColor(urgency)
    val summary = outcome?.summary ?: message.aiSummary ?: offline.summary
    val busy = message.id in vm.aiBusy

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$urgency", style = MaterialTheme.typography.titleMedium, color = accent)
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = message.peerName.ifBlank { "Unknown node" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    KindChip(message.kind)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoLabel("${offline.category.name} · UR${urgency}", color = accent)
                    MonoLabel(formatWhen(message.timestamp), color = TextMuted)
                }
            }
            if (outcome != null) {
                MonoLabel(
                    if (outcome.source == AiAssistant.Source.FEATHERLESS) "AI" else "LOCAL",
                    color = if (outcome.source == AiAssistant.Source.FEATHERLESS) RelayBlue else TextMuted,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(message.body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        outcome?.translation?.let { translated ->
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(RelayBlue.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                MonoLabel("${outcome.language.uppercase()} · TRANSLATED", color = RelayBlue)
                Spacer(Modifier.height(3.dp))
                Text(translated, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        outcome?.onlineNote?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.analyze(message) }, enabled = !busy) {
                Text(
                    if (busy) "Working…" else if (outcome == null) "Analyze" else "Re-analyze",
                    style = MaterialTheme.typography.labelSmall,
                    color = RelayBlue,
                )
            }
            TextButton(onClick = { vm.analyze(message, vm.aiLanguage) }, enabled = !busy) {
                Text(
                    "Translate → ${vm.aiLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun urgencyColor(urgency: Int): Color = when {
    urgency >= 5 -> SosRed
    urgency == 4 -> AlertAmber
    urgency == 3 -> RelayBlue
    urgency == 2 -> TextSecondary
    else -> MeshGreen
}
