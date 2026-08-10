package com.schortgen.vehiclelogai.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    navController: NavController,
    viewModel: VehicleViewModel
) {
    val vehicles by viewModel.vehicles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Vehicles") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddVehicle.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Vehicle")
            }
        }
    ) { innerPadding ->
        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(), 
                contentAlignment = Alignment.Center
            ) {
                Text("No vehicles found. Add one!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vehicles) { vehicle ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { navController.navigate(Screen.VehicleDetail.createRoute(vehicle.id)) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val details = listOfNotNull(vehicle.year?.toString(), vehicle.make, vehicle.model).joinToString(" ")
                            val titleText = if (!vehicle.nickname.isNullOrBlank()) {
                                vehicle.nickname
                            } else if (details.isNotBlank()) {
                                details
                            } else {
                                "Unnamed Vehicle"
                            }
                            
                            Text(
                                text = titleText, 
                                style = MaterialTheme.typography.titleLarge
                            )
                            
                            if (!vehicle.nickname.isNullOrBlank() && details.isNotBlank()) {
                                Text(text = details, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (vehicle.currentMileage != null) {
                                Text(
                                    text = "Mileage: ${vehicle.currentMileage}", 
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
