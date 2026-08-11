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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schortgen.vehiclelogai.navigation.NavGraph
import com.schortgen.vehiclelogai.ui.components.BottomNavBar
import com.schortgen.vehiclelogai.ui.dashboard.DashboardViewModel
import com.schortgen.vehiclelogai.ui.dashboard.DashboardViewModelFactory
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

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(
            vehicleRepository = app.vehicleRepository,
            eventRepository = app.eventRepository,
            reviewItemRepository = app.reviewItemRepository
        )
    )

    VehicleLogTheme {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = {
                BottomNavBar(
                    navController = navController,
                    onDashboardClick = {
                        dashboardViewModel.refreshDashboard()
                    }
                )
            }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                NavGraph(
                    navController = navController,
                    dashboardViewModel = dashboardViewModel
                )
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
