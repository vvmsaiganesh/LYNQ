package com.flux.relay.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flux.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RelayTheme {
                // Activity-scoped: survives configuration changes, owns the mesh subscriptions.
                val vm: RelayViewModel = viewModel()
                RelayRoot(vm)
            }
        }
    }
}
