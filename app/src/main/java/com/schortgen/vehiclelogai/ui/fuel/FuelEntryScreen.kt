package com.schortgen.vehiclelogai.ui.fuel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEntryScreen(
    navController: NavController,
    eventViewModel: EventViewModel,
    vehicleId: Long
) {
    // Date state
    val calendar = remember { Calendar.getInstance() }
    var selectedDateMillis by remember { mutableLongStateOf(calendar.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Field states
    var odometerText by remember { mutableStateOf("") }
    var gallonsText by remember { mutableStateOf("") }
    var pricePerGallonText by remember { mutableStateOf("") }
    var totalCostText by remember { mutableStateOf("") }
    var gasStationText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    // Edit tracking for auto-calculation
    var userEditedGallons by remember { mutableStateOf(false) }
    var userEditedPricePerGallon by remember { mutableStateOf(false) }
    var userEditedTotalCost by remember { mutableStateOf(false) }

    // Error states
    var odometerError by remember { mutableStateOf<String?>(null) }
    var gallonsError by remember { mutableStateOf<String?>(null) }
    var totalCostError by remember { mutableStateOf<String?>(null) }

    // Save state
    var isSaving by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Auto-calculation logic
    fun recalculate() {
        val gallons = gallonsText.toDoubleOrNull()
        val pricePerGallon = pricePerGallonText.toDoubleOrNull()
        val totalCost = totalCostText.toDoubleOrNull()

        if (gallons != null && gallons > 0 && totalCost != null && totalCost > 0 && !userEditedPricePerGallon) {
            // Calculate Price Per Gallon from Gallons and Total Cost
            pricePerGallonText = String.format("%.3f", totalCost / gallons)
        } else if (gallons != null && gallons > 0 && pricePerGallon != null && pricePerGallon > 0 && !userEditedTotalCost) {
            // Calculate Total Cost from Gallons and Price Per Gallon
            totalCostText = String.format("%.2f", gallons * pricePerGallon)
        }
    }

    fun onGallonsChanged(value: String) {
        gallonsText = value
        userEditedGallons = true
        userEditedPricePerGallon = false
        userEditedTotalCost = false
        recalculate()
    }

    fun onPricePerGallonChanged(value: String) {
        pricePerGallonText = value
        userEditedPricePerGallon = true
        userEditedGallons = !gallonsText.isNullOrBlank()
        userEditedTotalCost = false
        recalculate()
    }

    fun onTotalCostChanged(value: String) {
        totalCostText = value
        userEditedTotalCost = true
        userEditedGallons = !gallonsText.isNullOrBlank()
        userEditedPricePerGallon = false
        recalculate()
    }

    fun validate(): Boolean {
        var isValid = true

        if (odometerText.isBlank()) {
            odometerError = "Odometer is required"
            isValid = false
        } else if (odometerText.toIntOrNull() == null || odometerText.toInt() < 0) {
            odometerError = "Enter a valid odometer reading"
            isValid = false
        } else {
            odometerError = null
        }

        if (gallonsText.isBlank()) {
            gallonsError = "Gallons is required"
            isValid = false
        } else if (gallonsText.toDoubleOrNull() == null || gallonsText.toDouble() <= 0) {
            gallonsError = "Enter a valid number of gallons"
            isValid = false
        } else {
            gallonsError = null
        }

        if (totalCostText.isBlank()) {
            totalCostError = "Total Cost is required"
            isValid = false
        } else if (totalCostText.toDoubleOrNull() == null || totalCostText.toDouble() <= 0) {
            totalCostError = "Enter a valid total cost"
            isValid = false
        } else {
            totalCostError = null
        }

        return isValid
    }

    fun saveFuelEntry() {
        if (!validate()) return

        isSaving = true
        val event = Event(
            vehicleId = vehicleId,
            eventType = EventType.FUEL,
            eventDate = selectedDateMillis,
            odometer = odometerText.toIntOrNull(),
            gallons = gallonsText.toDoubleOrNull(),
            pricePerGallon = pricePerGallonText.toDoubleOrNull(),
            totalCost = totalCostText.toDoubleOrNull(),
            location = gasStationText.ifBlank { null },
            notes = notesText.ifBlank { null }
        )
        eventViewModel.addEvent(event)
        navController.popBackStack()
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Entry") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // Date picker
            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormat.format(Date(selectedDateMillis)), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("Pick", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Odometer
            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it; odometerError = null },
                label = { Text("Odometer") },
                placeholder = { Text("e.g. 45000") },
                isError = odometerError != null,
                supportingText = odometerError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Gallons
            OutlinedTextField(
                value = gallonsText,
                onValueChange = { onGallonsChanged(it); gallonsError = null },
                label = { Text("Gallons") },
                placeholder = { Text("e.g. 12.5") },
                isError = gallonsError != null,
                supportingText = gallonsError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Price Per Gallon
            OutlinedTextField(
                value = pricePerGallonText,
                onValueChange = { onPricePerGallonChanged(it) },
                label = { Text("Price Per Gallon") },
                placeholder = { Text("e.g. 3.499") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Total Cost
            OutlinedTextField(
                value = totalCostText,
                onValueChange = { onTotalCostChanged(it); totalCostError = null },
                label = { Text("Total Cost") },
                placeholder = { Text("e.g. 43.74") },
                isError = totalCostError != null,
                supportingText = totalCostError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Gas Station (optional)
            OutlinedTextField(
                value = gasStationText,
                onValueChange = { gasStationText = it },
                label = { Text("Gas Station (optional)") },
                placeholder = { Text("e.g. Shell") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Notes (optional)
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Any additional notes...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { saveFuelEntry() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Fuel Entry", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}