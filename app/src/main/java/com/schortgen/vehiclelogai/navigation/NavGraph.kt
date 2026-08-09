package com.schortgen.vehiclelogai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.schortgen.vehiclelogai.BuildConfig
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.ui.components.PlaceholderScreen
import com.schortgen.vehiclelogai.ui.dashboard.DashboardScreen
import com.schortgen.vehiclelogai.ui.dashboard.DashboardViewModel
import com.schortgen.vehiclelogai.ui.dashboard.DashboardViewModelFactory
import com.schortgen.vehiclelogai.ui.debug.DebugScreen
import com.schortgen.vehiclelogai.ui.eventdetail.EventDetailScreen
import com.schortgen.vehiclelogai.ui.fuel.FuelEntryScreen
import com.schortgen.vehiclelogai.ui.reviewdetail.ReviewDetailScreen
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueScreen
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModelFactory
import com.schortgen.vehiclelogai.ui.settings.SettingsScreen
import com.schortgen.vehiclelogai.ui.settings.SettingsViewModel
import com.schortgen.vehiclelogai.ui.settings.SettingsViewModelFactory
import com.schortgen.vehiclelogai.ui.vehicle.VehicleDetailScreen
import com.schortgen.vehiclelogai.ui.vehicles.VehiclesScreen
import com.schortgen.vehiclelogai.ui.vehicles.AddVehicleScreen
import com.schortgen.vehiclelogai.ui.vehicles.EditVehicleScreen
import com.schortgen.vehiclelogai.ui.vehicles.VehicleViewModel
import com.schortgen.vehiclelogai.ui.vehicles.VehicleViewModelFactory
import com.schortgen.vehiclelogai.ui.scanphotos.ScanPhotosScreen
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import com.schortgen.vehiclelogai.ui.events.EventViewModelFactory

import com.schortgen.vehiclelogai.service.EventGroupingService

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Vehicles : Screen("vehicles")
    object Timeline : Screen("timeline")
    object ReviewQueue : Screen("review_queue")
    object Settings : Screen("settings")
    object AddVehicle : Screen("add_vehicle")
    object ScanPhotos : Screen("scan_photos")
    object Debug : Screen("debug")
    object VehicleDetail : Screen("vehicle_detail/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "vehicle_detail/$vehicleId"
    }
    object FuelEntry : Screen("fuel_entry/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "fuel_entry/$vehicleId"
    }
    object ReviewDetail : Screen("review_detail/{reviewItemId}") {
        fun createRoute(reviewItemId: Long) = "review_detail/$reviewItemId"
    }
    object ReviewGroupDetail : Screen("review_group_detail/{eventId}") {
        fun createRoute(eventId: Long) = "review_group_detail/$eventId"
    }
    object EventDetail : Screen("event_detail/{eventId}") {
        fun createRoute(eventId: Long) = "event_detail/$eventId"
    }
    object EditVehicle : Screen("edit_vehicle/{vehicleId}") {
        fun createRoute(vehicleId: Long) = "edit_vehicle/$vehicleId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as VehicleLogAIApplication
    val vehicleViewModel: VehicleViewModel = viewModel(
        factory = VehicleViewModelFactory(app.vehicleRepository)
    )
    val eventViewModel: EventViewModel = viewModel(
        factory = EventViewModelFactory(app.eventRepository)
    )
    val eventGroupingService = EventGroupingService(
        eventRepository = app.eventRepository,
        reviewItemRepository = app.reviewItemRepository,
        vehicleRepository = app.vehicleRepository
    )
    val reviewQueueViewModel: ReviewQueueViewModel = viewModel(
        factory = ReviewQueueViewModelFactory(
            repository = app.reviewItemRepository,
            vehicleRepository = app.vehicleRepository,
            eventRepository = app.eventRepository,
            mlKitOcrService = app.mlKitOcrService,
            receiptParserService = app.receiptParserService,
            eventGroupingService = eventGroupingService,
            settingsRepository = app.settingsRepository
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.settingsRepository, app.backupRepository)
    )
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(
            vehicleRepository = app.vehicleRepository,
            eventRepository = app.eventRepository,
            reviewItemRepository = app.reviewItemRepository
        )
    )

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController, dashboardViewModel)
        }

        composable(Screen.Vehicles.route) {
            VehiclesScreen(navController, vehicleViewModel)
        }

        composable(Screen.ReviewQueue.route) {
            ReviewQueueScreen(navController, reviewQueueViewModel)
        }

        composable(Screen.Timeline.route) {
            PlaceholderScreen("Timeline")
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController, settingsViewModel)
        }

        composable(Screen.AddVehicle.route) {
            AddVehicleScreen(navController, vehicleViewModel)
        }

        composable(Screen.ScanPhotos.route) {
            ScanPhotosScreen(navController = navController, reviewQueueViewModel = reviewQueueViewModel)
        }

        // The Debug screen is only registered in debug builds. Release builds
        // never expose the route, so navigation attempts are simply unmatched.
        if (BuildConfig.DEBUG) {
            composable(Screen.Debug.route) {
                DebugScreen(navController)
            }
        }

        composable(
            route = Screen.VehicleDetail.route,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: -1L
            VehicleDetailScreen(navController, vehicleViewModel, eventViewModel, vehicleId)
        }

        composable(
            route = Screen.FuelEntry.route,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: -1L
            FuelEntryScreen(navController, eventViewModel, vehicleId)
        }

        composable(
            route = Screen.ReviewDetail.route,
            arguments = listOf(navArgument("reviewItemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val reviewItemId = backStackEntry.arguments?.getLong("reviewItemId") ?: -1L
            ReviewDetailScreen(navController, reviewQueueViewModel, reviewItemId = reviewItemId)
        }

        composable(
            route = Screen.ReviewGroupDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: -1L
            ReviewDetailScreen(navController, reviewQueueViewModel, reviewItemId = -1L, eventId = eventId)
        }

        composable(
            route = Screen.EditVehicle.route,
            arguments = listOf(navArgument("vehicleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val vehicleId = backStackEntry.arguments?.getLong("vehicleId") ?: -1L
            EditVehicleScreen(navController, vehicleViewModel, eventViewModel, vehicleId)
        }

        composable(
            route = Screen.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getLong("eventId") ?: -1L
            EventDetailScreen(navController, eventViewModel, eventId)
        }
    }
}
