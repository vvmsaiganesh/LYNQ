package com.flux.relay.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flux.relay.ui.LocationUtil
import com.flux.relay.ui.RelayViewModel
import com.flux.relay.ui.components.HairlineDivider
import com.flux.relay.ui.components.MonoLabel
import com.flux.relay.ui.theme.Ink
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.SosRed
import com.flux.relay.ui.theme.SosRedDeep
import com.flux.relay.ui.theme.Surface
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextPrimary
import com.flux.relay.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SosScreen(vm: RelayViewModel) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("I need help. Please respond if you can hear this.") }
    var attachLocation by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf<Pair<Double, Double>?>(LocationUtil.lastKnown(context)) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        attachLocation = granted
        if (granted) location = LocationUtil.lastKnown(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SosRedDeep.copy(alpha = 0.55f), Ink, Ink),
                ),
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text("EMERGENCY BROADCAST", style = MaterialTheme.typography.labelLarge, color = SosRed)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Text(
                "Someone will hear you.",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This floods to every Relay node in range and keeps hopping — up to 12 hops — " +
                    "with no network involved. It is signed so nobody can fake it, and it stays queued " +
                    "until it finds a path.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it.take(280) },
                label = { Text("What is happening?") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SosRed,
                    unfocusedBorderColor = Hairline,
                    focusedLabelColor = SosRed,
                    cursorColor = SosRed,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Surface.copy(alpha = 0.6f),
                    unfocusedContainerColor = Surface.copy(alpha = 0.6f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Attach my location", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    MonoLabel(
                        when {
                            location != null -> "±${"%.4f".format(location!!.first)}, ${"%.4f".format(location!!.second)}"
                            attachLocation -> "no fix available yet"
                            else -> "off"
                        },
                        color = TextMuted,
                    )
                }
                Switch(
                    checked = attachLocation,
                    onCheckedChange = { wanted ->
                        if (wanted && !LocationUtil.permissionGranted(context)) {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            attachLocation = wanted
                            if (wanted) location = LocationUtil.lastKnown(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = SosRed,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Surface,
                    ),
                )
            }

            Spacer(Modifier.height(28.dp))

            if (!sent) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HoldToSendButton {
                        sent = true
                        vm.sendSos(message, if (attachLocation) location else null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Hold for three seconds. Release early to cancel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                BroadcastSent(vm = vm, message = message, hasLocation = attachLocation && location != null)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HoldToSendButton(onTriggered: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val animated by animateFloatAsState(progress, label = "hold")

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        progress = 0f
        while (holding && progress < 1f) {
            delay(40)
            progress = (progress + 0.04f).coerceAtMost(1f)
        }
        if (holding && progress >= 1f) {
            holding = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onTriggered()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(196.dp)
            .background(SosRed.copy(alpha = 0.10f), RoundedCornerShape(50))
            .then(
                Modifier.pointerPress { pressed -> holding = pressed },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = SosRed.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = SosRed,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS", style = MaterialTheme.typography.displayLarge, color = SosRed)
            MonoLabel(if (holding) "HOLD…" else "HOLD TO SEND", color = if (holding) SosRed else TextSecondary)
        }
    }
}

@Composable
private fun BroadcastSent(vm: RelayViewModel, message: String, hasLocation: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MeshGreen.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WifiTethering, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Broadcast queued", style = MaterialTheme.typography.titleMedium, color = MeshGreen)
        }
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        MonoLabel(
            "${vm.online.size} node${if (vm.online.size == 1) "" else "s"} in range · " +
                if (hasLocation) "location attached" else "no location",
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        HairlineDivider(color = MeshGreen.copy(alpha = 0.2f))
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { vm.returnToRadar() },
            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back to the radar")
        }
    }
}

/** Small helper so the hold gesture is one line above. */
private fun Modifier.pointerPress(onPressChange: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                onPressChange(true)
                tryAwaitRelease()
                onPressChange(false)
            },
        )
    }
