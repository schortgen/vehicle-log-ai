package com.schortgen.vehiclelogai.ui.vehicles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.data.models.Vehicle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import java.util.*
import com.schortgen.vehiclelogai.ui.events.EventViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVehicleScreen(
    navController: NavController,
    vehicleViewModel: VehicleViewModel,
    eventViewModel: EventViewModel,
    vehicleId: Long
) {
    var vehicle by remember { mutableStateOf<Vehicle?>(null) }
    // Load vehicle
    LaunchedEffect(vehicleId) {
        vehicle = vehicleViewModel.getVehicleById(vehicleId)
    }
    // Observe events to determine max odometer
    val events by eventViewModel.observeEventsForVehicle(vehicleId).collectAsState(initial = emptyList())
    val maxEventOdometer = events.maxOfOrNull { it.odometer ?: 0 } ?: 0

    // Form fields
    var nickname by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }

    // Validation state
    var showMileageWarning by remember { mutableStateOf(false) }
    var showVinError by remember { mutableStateOf(false) }
    var showRequiredError by remember { mutableStateOf(false) }

    // Populate fields when vehicle is loaded
    LaunchedEffect(vehicle) {
        vehicle?.let {
            nickname = it.nickname ?: ""
            year = it.year?.toString() ?: ""
            make = it.make ?: ""
            model = it.model ?: ""
            vin = it.vin ?: ""
            mileage = it.currentMileage?.toString() ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Vehicle") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname (e.g. My Truck)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = make,
                    onValueChange = { make = it },
                    label = { Text("Make") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vin,
                onValueChange = { vin = it },
                label = { Text("VIN (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mileage,
                onValueChange = { mileage = it },
                label = { Text("Current Mileage") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    // Basic required validation
                    if (nickname.isBlank() || year.isBlank() || make.isBlank() || model.isBlank()) {
                        showRequiredError = true
                        return@Button
                    }
                    val trimmedVin = vin.trim()
                    if (trimmedVin.isNotEmpty() && trimmedVin.length != 17) {
                        showVinError = true
                        return@Button
                    }
                    val mileageInt = mileage.toIntOrNull() ?: -1
                    if (mileageInt < 0) {
                        // Invalid mileage, treat as required error
                        showRequiredError = true
                        return@Button
                    }
                    // Mileage warning if less than max event odometer
                    if (mileageInt < maxEventOdometer) {
                        showMileageWarning = true
                        return@Button
                    }
                    // All good, save
                    val updatedVehicle = Vehicle(
                        id = vehicleId,
                        nickname = nickname.takeIf { it.isNotBlank() },
                        year = year.toIntOrNull(),
                        make = make.takeIf { it.isNotBlank() },
                        model = model.takeIf { it.isNotBlank() },
                        vin = trimmedVin.ifEmpty { null },
                        currentMileage = mileageInt
                    )
                    vehicleViewModel.updateVehicle(updatedVehicle)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        }
    }

    // VIN format error dialog
    if (showVinError) {
        AlertDialog(
            onDismissRequest = { showVinError = false },
            title = { Text("Invalid VIN") },
            text = { Text("VIN must be exactly 17 characters when provided.") },
            confirmButton = {
                TextButton(onClick = { showVinError = false }) { Text("OK") }
            }
        )
    }
    // Required fields error dialog
    if (showRequiredError) {
        AlertDialog(
            onDismissRequest = { showRequiredError = false },
            title = { Text("Missing Information") },
            text = { Text("Please fill out all required fields (Nickname, Year, Make, Model, and a valid mileage.") },
            confirmButton = {
                TextButton(onClick = { showRequiredError = false }) { Text("OK") }
            }
        )
    }
    // Mileage warning dialog
    if (showMileageWarning) {
        AlertDialog(
            onDismissRequest = { showMileageWarning = false },
            title = { Text("Mileage Warning") },
            text = { Text("The entered mileage is lower than the highest recorded event odometer ($maxEventOdometer). Are you sure you want to save?") },
            confirmButton = {
                TextButton(onClick = {
                    showMileageWarning = false
                    // Save after confirmation
                    val trimmedVin = vin.trim()
                    val updatedVehicle = Vehicle(
                        id = vehicleId,
                        nickname = nickname.takeIf { it.isNotBlank() },
                        year = year.toIntOrNull(),
                        make = make.takeIf { it.isNotBlank() },
                        model = model.takeIf { it.isNotBlank() },
                        vin = trimmedVin.ifEmpty { null },
                        currentMileage = mileage.toIntOrNull() ?: 0
                    )
                    vehicleViewModel.updateVehicle(updatedVehicle)
                    navController.popBackStack()
                }) { Text("Save Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showMileageWarning = false }) { Text("Cancel") }
            }
        )
    }
}
