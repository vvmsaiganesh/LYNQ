package com.flux.relay.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flux.relay.ui.theme.MeshGreen
import com.flux.relay.ui.theme.TextMuted
import com.flux.relay.ui.theme.TextSecondary

/** Radios need explicit consent on modern Android; without it Nearby silently fails. */
fun requiredNearbyPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}

/** Nice to have: without it the foreground-service badge is hidden, but relaying still works. */
fun optionalPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun PermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { requiredNearbyPermissions() }
    val optional = remember { optionalPermissions() }

    fun allGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var deniedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Only radio permissions gate the app; a denied notification prompt must not block an emergency.
        granted = permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) deniedOnce = true
    }

    if (granted) {
        LaunchedEffect(Unit) { onGranted() }
        content()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("RADIOS OFF", style = MaterialTheme.typography.labelLarge, color = MeshGreen)
        Spacer(Modifier.height(12.dp))
        Text(
            "Relay needs to see and be seen by nearby devices.",
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Bluetooth and Wi-Fi Direct permissions let this phone find other Relay nodes " +
                "without any internet connection. Nothing is uploaded, ever.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { launcher.launch((permissions + optional).toTypedArray()) },
            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen, contentColor = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enable nearby radios")
        }
        if (deniedOnce) {
            Spacer(Modifier.height(8.dp))
            Text(
                "If the system no longer shows a prompt, enable Bluetooth and Nearby devices for Relay in app settings.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) {
                    Text("Open settings", color = MeshGreen)
                }
            }
        }
    }
}
