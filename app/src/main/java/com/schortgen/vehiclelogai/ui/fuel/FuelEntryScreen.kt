package com.schortgen.vehiclelogai.ui.fuel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.models.toImageModel
import com.schortgen.vehiclelogai.navigation.Screen
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import com.schortgen.vehiclelogai.ui.vehicles.VehicleViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelEntryScreen(
    navController: NavController,
    eventViewModel: EventViewModel,
    vehicleViewModel: VehicleViewModel? = null,
    vehicleId: Long = -1L
) {
    val context = LocalContext.current
    val app = context.applicationContext as? VehicleLogAIApplication

    // Collect vehicles
    val vehicles: List<Vehicle> by if (vehicleViewModel != null) {
        vehicleViewModel.vehicles.collectAsState()
    } else {
        app?.vehicleRepository?.observeAllVehicles()?.collectAsState(initial = emptyList())
            ?: remember { mutableStateOf(emptyList()) }
    }

    var selectedVehicleId by remember(vehicleId, vehicles) {
        mutableLongStateOf(
            if (vehicleId > 0 && vehicles.any { it.id == vehicleId }) {
                vehicleId
            } else {
                vehicles.firstOrNull()?.id ?: (if (vehicleId > 0) vehicleId else 1L)
            }
        )
    }

    var vehicleDropdownExpanded by remember { mutableStateOf(false) }

    // Event Type selection
    var selectedEventType by remember { mutableStateOf(EventType.FUEL) }

    // Date state
    val calendar = remember { Calendar.getInstance() }
    var selectedDateMillis by remember { mutableLongStateOf(calendar.timeInMillis) }

    val showDatePickerDialog = {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (selectedDateMillis > 0) selectedDateMillis else System.currentTimeMillis()
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Common fields
    var odometerText by remember { mutableStateOf("") }
    var tripDistanceText by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf("") }
    var totalCostText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    val attachedPhotoPaths = remember { mutableStateListOf<String>() }

    // Fuel-specific fields
    var gallonsText by remember { mutableStateOf("") }
    var pricePerGallonText by remember { mutableStateOf("") }
    var selectedFuelType by remember { mutableStateOf("Regular 87") }

    // Maintenance / Service specific fields
    var serviceTitleText by remember { mutableStateOf("") }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val appStorageDir = File(context.filesDir, "event_photos").apply { mkdirs() }
            uris.forEach { uri ->
                try {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                    val destFile = File(appStorageDir, "MANUAL_${selectedEventType.name}_${timeStamp}_${(100..999).random()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0) {
                        attachedPhotoPaths.add(destFile.absolutePath)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Edit tracking for auto-calculation (Fuel)
    var userEditedGallons by remember { mutableStateOf(false) }
    var userEditedPricePerGallon by remember { mutableStateOf(false) }
    var userEditedTotalCost by remember { mutableStateOf(false) }
    var userEditedTripDistance by remember { mutableStateOf(false) }
    var missingEventWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedVehicleId, odometerText, selectedEventType) {
        if (selectedVehicleId <= 0) return@LaunchedEffect
        if (selectedEventType == EventType.FUEL) {
            val lastEvent = eventViewModel.getLastFuelEvent(selectedVehicleId)
            val prevOdo = lastEvent?.odometer
            val curOdo = odometerText.toIntOrNull()
            if (curOdo != null) {
                if (prevOdo != null) {
                    val diff = curOdo - prevOdo
                    if (diff >= 0) {
                        if (!userEditedTripDistance) {
                            tripDistanceText = diff.toString()
                        }
                        if (diff > 600) {
                            missingEventWarning = "Odometer difference is over 600 miles ($diff mi). You might be missing a Fueling event."
                        } else {
                            missingEventWarning = null
                        }
                    } else {
                        missingEventWarning = "Current odometer ($curOdo) is less than previous odometer ($prevOdo)."
                    }
                } else {
                    missingEventWarning = null
                }
            } else {
                missingEventWarning = null
            }
        } else {
            missingEventWarning = null
        }
    }

    // Error states
    var odometerError by remember { mutableStateOf<String?>(null) }
    var gallonsError by remember { mutableStateOf<String?>(null) }
    var totalCostError by remember { mutableStateOf<String?>(null) }
    var serviceTitleError by remember { mutableStateOf<String?>(null) }

    // Save state
    var isSaving by remember { mutableStateOf(false) }
    var pendingEventToSave by remember { mutableStateOf<Event?>(null) }
    var warningMessageToDisplay by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Fuel Auto-calculation logic
    fun recalculateFuel() {
        val gallons = gallonsText.toDoubleOrNull()
        val pricePerGallon = pricePerGallonText.toDoubleOrNull()
        val totalCost = totalCostText.toDoubleOrNull()

        if (gallons != null && gallons > 0 && totalCost != null && totalCost > 0 && !userEditedPricePerGallon) {
            pricePerGallonText = String.format(Locale.US, "%.3f", totalCost / gallons)
        } else if (gallons != null && gallons > 0 && pricePerGallon != null && pricePerGallon > 0 && !userEditedTotalCost) {
            totalCostText = String.format(Locale.US, "%.2f", gallons * pricePerGallon)
        }
    }

    fun onGallonsChanged(value: String) {
        gallonsText = value
        userEditedGallons = true
        userEditedPricePerGallon = false
        userEditedTotalCost = false
        recalculateFuel()
    }

    fun onPricePerGallonChanged(value: String) {
        pricePerGallonText = value
        userEditedPricePerGallon = true
        userEditedGallons = gallonsText.isNotBlank()
        userEditedTotalCost = false
        recalculateFuel()
    }

    fun onTotalCostChanged(value: String) {
        totalCostText = value
        userEditedTotalCost = true
        if (selectedEventType == EventType.FUEL) {
            userEditedGallons = gallonsText.isNotBlank()
            userEditedPricePerGallon = false
            recalculateFuel()
        }
    }

    fun validate(): Boolean {
        var isValid = true

        if (selectedVehicleId <= 0) {
            isValid = false
        }

        // Odometer validation: required for FUEL, optional or recommended for others
        if (selectedEventType == EventType.FUEL || selectedEventType == EventType.MILEAGE) {
            if (odometerText.isBlank()) {
                odometerError = "Odometer is required"
                isValid = false
            } else if (odometerText.toIntOrNull() == null || odometerText.toInt() < 0) {
                odometerError = "Enter a valid odometer reading"
                isValid = false
            } else {
                odometerError = null
            }
        } else {
            if (odometerText.isNotBlank() && (odometerText.toIntOrNull() == null || odometerText.toInt() < 0)) {
                odometerError = "Enter a valid odometer reading"
                isValid = false
            } else {
                odometerError = null
            }
        }

        // Fuel specific validation
        if (selectedEventType == EventType.FUEL) {
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
        } else if (selectedEventType == EventType.MAINTENANCE) {
            if (serviceTitleText.isBlank()) {
                serviceTitleError = "Service description or title is required"
                isValid = false
            } else {
                serviceTitleError = null
            }
            if (totalCostText.isNotBlank() && (totalCostText.toDoubleOrNull() == null || totalCostText.toDouble() < 0)) {
                totalCostError = "Enter a valid cost amount"
                isValid = false
            } else {
                totalCostError = null
            }
        } else {
            gallonsError = null
            serviceTitleError = null
            if (totalCostText.isNotBlank() && (totalCostText.toDoubleOrNull() == null || totalCostText.toDouble() < 0)) {
                totalCostError = "Enter a valid cost amount"
                isValid = false
            } else {
                totalCostError = null
            }
        }

        return isValid
    }

    fun performSave(event: Event) {
        isSaving = true
        eventViewModel.addEvent(event)
        navController.popBackStack()
    }

    fun saveEvent() {
        if (!validate()) return

        val photoPathValue = when {
            attachedPhotoPaths.isEmpty() -> null
            attachedPhotoPaths.size == 1 -> attachedPhotoPaths.first()
            else -> "[${attachedPhotoPaths.joinToString(",")}]"
        }

        val compiledNotes = when (selectedEventType) {
            EventType.FUEL -> {
                if (notesText.isNotBlank()) "$selectedFuelType - $notesText" else selectedFuelType
            }
            EventType.MAINTENANCE -> {
                if (notesText.isNotBlank()) "$serviceTitleText\n$notesText" else serviceTitleText
            }
            EventType.TIRE_ROTATION -> {
                if (notesText.isNotBlank()) "Tire Rotation & Balance - $notesText" else "Tire Rotation & Balance"
            }
            EventType.INSPECTION -> {
                if (notesText.isNotBlank()) "${serviceTitleText.ifBlank { "Vehicle Inspection" }} - $notesText" else serviceTitleText.ifBlank { "Vehicle Inspection" }
            }
            EventType.REGISTRATION -> {
                if (notesText.isNotBlank()) "${serviceTitleText.ifBlank { "Registration Renewal" }} - $notesText" else serviceTitleText.ifBlank { "Registration Renewal" }
            }
            EventType.MILEAGE -> {
                if (notesText.isNotBlank()) notesText else "Odometer Log"
            }
        }

        val event = Event(
            vehicleId = selectedVehicleId,
            eventType = selectedEventType,
            eventDate = selectedDateMillis,
            verified = true, // Critical: Mark verified=true so it displays in all queries
            odometer = odometerText.toIntOrNull(),
            tripDistance = tripDistanceText.toDoubleOrNull(),
            gallons = if (selectedEventType == EventType.FUEL) gallonsText.toDoubleOrNull() else null,
            pricePerGallon = if (selectedEventType == EventType.FUEL) pricePerGallonText.toDoubleOrNull() else null,
            totalCost = totalCostText.toDoubleOrNull(),
            location = locationText.ifBlank { null },
            notes = compiledNotes.ifBlank { null },
            photoPath = photoPathValue
        )

        if (selectedEventType == EventType.FUEL) {
            scope.launch {
                val warning = eventViewModel.checkPreviousEventWarnings(event)
                if (warning != null) {
                    pendingEventToSave = event
                    warningMessageToDisplay = warning
                } else {
                    performSave(event)
                }
            }
        } else {
            performSave(event)
        }
    }

    if (warningMessageToDisplay != null && pendingEventToSave != null) {
        AlertDialog(
            onDismissRequest = {
                warningMessageToDisplay = null
                pendingEventToSave = null
            },
            title = { Text("Warning") },
            text = { Text(warningMessageToDisplay!!) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val evt = pendingEventToSave
                        warningMessageToDisplay = null
                        pendingEventToSave = null
                        if (evt != null) {
                            performSave(evt)
                        }
                    }
                ) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        warningMessageToDisplay = null
                        pendingEventToSave = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val screenTitle = when (selectedEventType) {
        EventType.FUEL -> "Log Fuel Purchase"
        EventType.MAINTENANCE -> "Log Maintenance Service"
        EventType.TIRE_ROTATION -> "Log Tire Rotation"
        EventType.INSPECTION -> "Log Inspection"
        EventType.REGISTRATION -> "Log Registration"
        EventType.MILEAGE -> "Log Mileage"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Service / Event Type Selector
            Text(
                text = "Event / Service Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val eventOptions = listOf(
                    Triple(EventType.FUEL, "⛽ Fuel", "Fuel Purchase"),
                    Triple(EventType.MAINTENANCE, "🔧 Maintenance", "Service & Repair"),
                    Triple(EventType.TIRE_ROTATION, "🔄 Tire Rotation", "Rotation"),
                    Triple(EventType.INSPECTION, "🔍 Inspection", "Safety / Emissions"),
                    Triple(EventType.REGISTRATION, "📄 Registration", "Tags / DMV"),
                    Triple(EventType.MILEAGE, "📏 Mileage", "Odometer Check")
                )

                eventOptions.forEach { (type, label, _) ->
                    FilterChip(
                        selected = selectedEventType == type,
                        onClick = {
                            selectedEventType = type
                            // Clear type-specific errors
                            odometerError = null
                            gallonsError = null
                            totalCostError = null
                            serviceTitleError = null
                        },
                        label = { Text(label, fontWeight = if (selectedEventType == type) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Vehicle Selection
            if (vehicles.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No vehicle registered yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please add a vehicle first before logging events.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { navController.navigate(Screen.AddVehicle.route) }
                        ) {
                            Text("Add Vehicle")
                        }
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = vehicleDropdownExpanded,
                    onExpandedChange = { vehicleDropdownExpanded = !vehicleDropdownExpanded }
                ) {
                    val currentVehicle = vehicles.find { it.id == selectedVehicleId } ?: vehicles.first()
                    OutlinedTextField(
                        value = "${currentVehicle.year} ${currentVehicle.make} ${currentVehicle.model}" +
                                (currentVehicle.nickname?.let { " ($it)" } ?: ""),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Vehicle *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = vehicleDropdownExpanded,
                        onDismissRequest = { vehicleDropdownExpanded = false }
                    ) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = {
                                    Text("${vehicle.year} ${vehicle.make} ${vehicle.model}" +
                                            (vehicle.nickname?.let { " ($it)" } ?: ""))
                                },
                                onClick = {
                                    selectedVehicleId = vehicle.id
                                    vehicleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Date picker
            OutlinedCard(
                onClick = { showDatePickerDialog() },
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
                        Text("Date *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFormat.format(Date(selectedDateMillis)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Change", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Maintenance / Inspection / Registration Specific: Service Title / Description
            if (selectedEventType == EventType.MAINTENANCE) {
                OutlinedTextField(
                    value = serviceTitleText,
                    onValueChange = { serviceTitleText = it; serviceTitleError = null },
                    label = { Text("Service Description / Parts Replaced *") },
                    placeholder = { Text("e.g. Synthetic Oil & Filter Change, Front Brake Pads") },
                    isError = serviceTitleError != null,
                    supportingText = serviceTitleError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Maintenance preset chips
                Text("Quick Presets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("Oil & Filter Change", "Brake Pads / Rotors", "Battery Replacement", "Cabin & Engine Filter", "Transmission Fluid", "Spark Plugs", "Coolant Flush")
                    presets.forEach { preset ->
                        AssistChip(
                            onClick = { serviceTitleText = preset; serviceTitleError = null },
                            label = { Text(preset) }
                        )
                    }
                }
            } else if (selectedEventType == EventType.INSPECTION) {
                OutlinedTextField(
                    value = serviceTitleText,
                    onValueChange = { serviceTitleText = it },
                    label = { Text("Inspection Title") },
                    placeholder = { Text("e.g. Annual State Safety & Emissions Inspection") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Inspection preset chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("State Safety Inspection", "Emissions Test", "Multi-Point Inspection", "Pre-Trip Inspection")
                    presets.forEach { preset ->
                        AssistChip(
                            onClick = { serviceTitleText = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            } else if (selectedEventType == EventType.REGISTRATION) {
                OutlinedTextField(
                    value = serviceTitleText,
                    onValueChange = { serviceTitleText = it },
                    label = { Text("Registration Details") },
                    placeholder = { Text("e.g. Annual License Plate & Tag Renewal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Odometer & Trip Distance
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isOdoRequired = selectedEventType == EventType.FUEL || selectedEventType == EventType.MILEAGE
                OutlinedTextField(
                    value = odometerText,
                    onValueChange = { odometerText = it; odometerError = null },
                    label = { Text("Odometer" + if (isOdoRequired) " *" else " (optional)") },
                    placeholder = { Text("e.g. 45200") },
                    isError = odometerError != null,
                    supportingText = odometerError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                if (selectedEventType == EventType.FUEL || selectedEventType == EventType.MILEAGE) {
                    OutlinedTextField(
                        value = tripDistanceText,
                        onValueChange = {
                            tripDistanceText = it
                            userEditedTripDistance = true
                        },
                        label = { Text("Trip Distance (mi)") },
                        placeholder = { Text("e.g. 340.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            missingEventWarning?.let { warning ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Gallons & Price per Gallon (FUEL only)
            if (selectedEventType == EventType.FUEL) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = gallonsText,
                        onValueChange = { onGallonsChanged(it); gallonsError = null },
                        label = { Text("Gallons *") },
                        placeholder = { Text("e.g. 12.500") },
                        isError = gallonsError != null,
                        supportingText = gallonsError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = pricePerGallonText,
                        onValueChange = { onPricePerGallonChanged(it) },
                        label = { Text("Price / Gal") },
                        placeholder = { Text("e.g. 3.499") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Total Cost (Fuel, Maintenance, Tire Rotation, Inspection, Registration)
            if (selectedEventType != EventType.MILEAGE) {
                val isCostRequired = selectedEventType == EventType.FUEL
                OutlinedTextField(
                    value = totalCostText,
                    onValueChange = { onTotalCostChanged(it); totalCostError = null },
                    label = { Text("Total Cost ($)" + if (isCostRequired) " *" else " (optional)") },
                    placeholder = { Text("e.g. 43.74") },
                    isError = totalCostError != null,
                    supportingText = totalCostError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Location / Service Shop / Gas Station
            val locationLabel = when (selectedEventType) {
                EventType.FUEL -> "Gas Station / Location (optional)"
                EventType.MAINTENANCE -> "Service Shop / Garage Name (optional)"
                EventType.TIRE_ROTATION -> "Tire Center / Shop Name (optional)"
                EventType.INSPECTION -> "Inspection Facility / Station (optional)"
                EventType.REGISTRATION -> "DMV Office / County (optional)"
                EventType.MILEAGE -> "Location / Route (optional)"
            }

            val locationPlaceholder = when (selectedEventType) {
                EventType.FUEL -> "e.g. Shell, Chevron, Costco"
                EventType.MAINTENANCE -> "e.g. Jiffy Lube, Valvoline, Dealership, DIY"
                EventType.TIRE_ROTATION -> "e.g. Discount Tire, Costco, Les Schwab"
                EventType.INSPECTION -> "e.g. State Inspection Station #12"
                EventType.REGISTRATION -> "e.g. Department of Motor Vehicles"
                EventType.MILEAGE -> "e.g. Austin to Houston, Commute"
            }

            OutlinedTextField(
                value = locationText,
                onValueChange = { locationText = it },
                label = { Text(locationLabel) },
                placeholder = { Text(locationPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Fuel Grade / Type Chips (FUEL only)
            if (selectedEventType == EventType.FUEL) {
                Text("Fuel Grade / Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val fuelTypes = listOf("Regular 87", "Midgrade 89", "Premium 93", "Diesel", "E85")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fuelTypes) { fType ->
                        FilterChip(
                            selected = selectedFuelType == fType,
                            onClick = { selectedFuelType = fType },
                            label = { Text(fType) }
                        )
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Additional Notes (optional)") },
                placeholder = {
                    Text(
                        when (selectedEventType) {
                            EventType.FUEL -> "e.g. Full tank refill, tire pressure checked"
                            EventType.MAINTENANCE -> "e.g. Replaced oil filter with OEM part, 5W-30 synthetic"
                            EventType.TIRE_ROTATION -> "e.g. Checked tread depth, front 7/32 rear 8/32"
                            EventType.INSPECTION -> "e.g. Passed all safety items, wiper blades recommended"
                            EventType.REGISTRATION -> "e.g. 2-year registration sticker received"
                            EventType.MILEAGE -> "e.g. End of month odometer checkpoint"
                        }
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Photos / Receipts Attachment Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Receipts & Photos (${attachedPhotoPaths.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { photoPickerLauncher.launch("image/*") }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Attach Photo")
                        }
                    }

                    if (attachedPhotoPaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(attachedPhotoPaths) { path ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                ) {
                                    val imageModel = path.toImageModel(context)
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Attached photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { attachedPhotoPaths.remove(path) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save button
            val saveButtonText = when (selectedEventType) {
                EventType.FUEL -> "Save Fuel Entry"
                EventType.MAINTENANCE -> "Save Maintenance Record"
                EventType.TIRE_ROTATION -> "Save Tire Rotation"
                EventType.INSPECTION -> "Save Inspection Record"
                EventType.REGISTRATION -> "Save Registration Record"
                EventType.MILEAGE -> "Save Mileage Entry"
            }

            Button(
                onClick = { saveEvent() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isSaving && vehicles.isNotEmpty()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(saveButtonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
