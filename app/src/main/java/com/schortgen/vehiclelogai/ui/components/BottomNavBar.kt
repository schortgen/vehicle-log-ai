package com.schortgen.vehiclelogai.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.schortgen.vehiclelogai.navigation.Screen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        Screen.Dashboard,
        Screen.Vehicles,
        Screen.Timeline,
        Screen.ReviewQueue,
        Screen.Settings,
        Screen.AddVehicle,
        Screen.ScanPhotos
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    NavigationBar {
        items.forEach { screen ->

            val icon: ImageVector = when (screen) {
                Screen.Dashboard -> Icons.Filled.Home
                Screen.Vehicles -> Icons.Filled.List
                Screen.Timeline -> Icons.Filled.List
                Screen.ReviewQueue -> Icons.Filled.List
                Screen.Settings -> Icons.Filled.Settings
                Screen.AddVehicle -> Icons.Filled.Add
                Screen.ScanPhotos -> Icons.Filled.Add
                Screen.VehicleDetail -> Icons.Filled.List
                Screen.FuelEntry -> Icons.Filled.Add
                Screen.ReviewDetail -> Icons.Filled.List
                Screen.ReviewGroupDetail -> Icons.Filled.List
                Screen.EventDetail -> Icons.Filled.List
                Screen.Debug -> Icons.Filled.Settings
                Screen.EditVehicle -> Icons.Filled.List
            }

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = screen.route
                    )
                },
                label = {
                    Text(
                        screen.route
                            .replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                    )
                },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}