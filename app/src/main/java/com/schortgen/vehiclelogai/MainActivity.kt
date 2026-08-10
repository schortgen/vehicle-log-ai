package com.schortgen.vehiclelogai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.schortgen.vehiclelogai.navigation.NavGraph
import com.schortgen.vehiclelogai.ui.components.BottomNavBar
import com.schortgen.vehiclelogai.ui.settings.PhotoDestinationPromptDialog
import com.schortgen.vehiclelogai.ui.theme.VehicleLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VehicleLogApp()
        }
    }
}

@Composable
fun VehicleLogApp() {
    val context = LocalContext.current
    val app = context.applicationContext as VehicleLogAIApplication
    val hasPrompted by app.settingsRepository.hasPromptedPhotoDestination.collectAsState()

    VehicleLogTheme {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                NavGraph(navController)
            }
        }

        if (!hasPrompted) {
            PhotoDestinationPromptDialog(
                settingsRepository = app.settingsRepository,
                onDismiss = {
                    app.settingsRepository.setHasPromptedPhotoDestination(true)
                }
            )
        }
    }
}
