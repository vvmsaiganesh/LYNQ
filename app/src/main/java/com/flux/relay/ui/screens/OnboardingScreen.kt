package com.flux.relay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.flux.relay.ui.theme.Ink
import com.flux.relay.ui.theme.Hairline
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("RELAY", style = MaterialTheme.typography.displayLarge, color = MeshGreen)
        Spacer(Modifier.height(6.dp))
        Text(
            "OFFLINE EMERGENCY MESH",
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted,
        )

        Spacer(Modifier.height(48.dp))

        Text(
            "When the network dies,\nthis keeps talking.",
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Messages hop phone to phone over Bluetooth and Wi-Fi Direct. " +
                "No internet. No SIM. No server. Range is whatever the crowd in the room can carry.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 24) name = it },
            label = { Text("Your name, as others will see it") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeshGreen,
                unfocusedBorderColor = Hairline,
                focusedLabelColor = MeshGreen,
                cursorColor = MeshGreen,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Ink,
                unfocusedContainerColor = Ink,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onCreate(name.trim()) },
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeshGreen,
                contentColor = Color.Black,
                disabledContainerColor = MeshGreen.copy(alpha = 0.25f),
                disabledContentColor = Color.Black.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text("Create my node", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .background(TextMuted.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(14.dp),
        ) {
            Text(
                "Your encryption keys are generated on this device and wrapped by the Android Keystore. " +
                    "They are never uploaded and never leave the phone.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}
