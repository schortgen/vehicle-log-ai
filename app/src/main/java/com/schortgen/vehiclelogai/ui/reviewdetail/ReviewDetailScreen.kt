package com.schortgen.vehiclelogai.ui.reviewdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.schortgen.vehiclelogai.data.models.ReviewItem
import java.util.Locale
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.navigation.Screen
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

private fun copyUriToInternalStorage(context: android.content.Context, uri: Uri): String {
    return try {
        val photosDir = File(context.filesDir, "photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        val file = File(photosDir, "manual_photo_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        DiagnosticLogger.e("PhotoCopy", "Failed to copy uri $uri", e)
        uri.toString()
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "N/A"
    }
}

private fun formatStatus(status: ProcessingStatus): String {
    return when (status) {
        ProcessingStatus.PENDING -> "Pending"
        ProcessingStatus.PROCESSING -> "Processing"
        ProcessingStatus.NEEDS_REVIEW -> "Needs Review"
        ProcessingStatus.COMPLETE -> "Complete"
        ProcessingStatus.FAILED -> "Failed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel,
    reviewItemId: Long,
    eventId: Long = -1L
) {
    val context = LocalContext.current
    val reviewItems by reviewQueueViewModel.reviewItems.collectAsState()
    val vehiclesState by (reviewQueueViewModel.vehicles?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val vehicles = vehiclesState ?: emptyList()
    val saveError by reviewQueueViewModel.saveErrors.collectAsState()
    val ocrProcessingIds by reviewQueueViewModel.ocrProcessingIds.collectAsState()

    val currentItem = remember(reviewItems, reviewItemId) {
        reviewItems.find { it.id == reviewItemId }
    }

    val targetEventId = remember(eventId, reviewItems, reviewItemId) {
        if (eventId != -1L && eventId > 0L) {
            eventId
        } else {
            reviewItems.find { it.id == reviewItemId }?.eventId
        }
    }

    val groupItems = remember(reviewItems, targetEventId, currentItem) {
        if (targetEventId != null && targetEventId > 0L) {
            reviewItems.filter { it.eventId == targetEventId }
        } else if (currentItem != null) {
            listOf(currentItem)
        } else emptyList()
    }

    var selectedPhotoIndex by remember(groupItems) { mutableStateOf(0) }
    val activeItem = groupItems.getOrNull(selectedPhotoIndex.coerceIn(0, (groupItems.size - 1).coerceAtLeast(0))) ?: currentItem

    var userSelectedVehicleId by rememberSaveable(key = "userSelectedVehicleId_$reviewItemId") { mutableStateOf<Long?>(null) }
    var initialVehicleListLoaded by rememberSaveable(key = "initialVehicleListLoaded_$reviewItemId") { mutableStateOf(false) }
    var prevVehicleCount by rememberSaveable(key = "prevVehicleCount_$reviewItemId") { mutableStateOf(vehicles.size) }

    LaunchedEffect(vehicles) {
        if (!initialVehicleListLoaded) {
            if (vehicles.isNotEmpty()) {
                initialVehicleListLoaded = true
                prevVehicleCount = vehicles.size
            }
        } else {
            if (vehicles.size > prevVehicleCount) {
                userSelectedVehicleId = vehicles.lastOrNull()?.id
            }
            prevVehicleCount = vehicles.size
        }
    }

    val selectedVehicleId = userSelectedVehicleId
        ?: activeItem?.vehicleId
        ?: vehicles.firstOrNull()?.id

    val parsedCandidate = remember(groupItems, activeItem) {
        if (groupItems.size > 1) {
            reviewQueueViewModel.mergeCandidatesFromItems(groupItems)
        } else {
            activeItem?.let { reviewQueueViewModel.parseCandidateFromItem(it) }
        }
    }

    val initialStationName = parsedCandidate?.stationName ?: ""
    val initialPurchaseDate = parsedCandidate?.purchaseDate ?: ""
    val initialGallons = parsedCandidate?.gallons?.toString() ?: ""
    val initialPricePerGallon = parsedCandidate?.pricePerGallon?.toString() ?: ""
    val initialTotalCost = parsedCandidate?.totalCost?.toString() ?: ""
    val initialOdometer = parsedCandidate?.odometer?.toString() ?: ""
    val initialTripDistance = parsedCandidate?.tripDistance?.toString() ?: ""

    var stationName by rememberSaveable(key = "stationName_$reviewItemId") { mutableStateOf(initialStationName) }
    var purchaseDate by rememberSaveable(key = "purchaseDate_$reviewItemId") { mutableStateOf(initialPurchaseDate) }
    var gallons by rememberSaveable(key = "gallons_$reviewItemId") { mutableStateOf(initialGallons) }
    var pricePerGallon by rememberSaveable(key = "pricePerGallon_$reviewItemId") { mutableStateOf(initialPricePerGallon) }
    var totalCost by rememberSaveable(key = "totalCost_$reviewItemId") { mutableStateOf(initialTotalCost) }
    var odometer by rememberSaveable(key = "odometer_$reviewItemId") { mutableStateOf(initialOdometer) }
    var tripDistance by rememberSaveable(key = "tripDistance_$reviewItemId") { mutableStateOf(initialTripDistance) }

    var isStationNameEdited by rememberSaveable(key = "isStationNameEdited_$reviewItemId") { mutableStateOf(false) }
    var isPurchaseDateEdited by rememberSaveable(key = "isPurchaseDateEdited_$reviewItemId") { mutableStateOf(false) }
    var isGallonsEdited by rememberSaveable(key = "isGallonsEdited_$reviewItemId") { mutableStateOf(false) }
    var isPricePerGallonEdited by rememberSaveable(key = "isPricePerGallonEdited_$reviewItemId") { mutableStateOf(false) }
    var isTotalCostEdited by rememberSaveable(key = "isTotalCostEdited_$reviewItemId") { mutableStateOf(false) }
    var isOdometerEdited by rememberSaveable(key = "isOdometerEdited_$reviewItemId") { mutableStateOf(false) }
    var isTripDistanceEdited by rememberSaveable(key = "isTripDistanceEdited_$reviewItemId") { mutableStateOf(false) }

    val calculatedMpg = remember(tripDistance, gallons) {
        val trip = tripDistance.replace(",", "").trim().toDoubleOrNull()
        val gal = gallons.replace(",", "").trim().toDoubleOrNull()
        if (trip != null && gal != null && gal > 0) {
            String.format(Locale.US, "%.2f", trip / gal)
        } else null
    }

    val preferredTripMeter by reviewQueueViewModel.preferredTripMeter.collectAsState()
    var missingEventWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedVehicleId, odometer, preferredTripMeter) {
        if (preferredTripMeter == PreferredTripMeter.ANY) {
            val vehId = selectedVehicleId
            val curOdo = odometer.toIntOrNull()
            if (vehId != null && vehId > 0) {
                val prevOdo = reviewQueueViewModel.getPreviousOdometerForVehicle(vehId, targetEventId)
                if (curOdo != null) {
                    if (prevOdo != null) {
                        val diff = curOdo - prevOdo
                        if (diff >= 0) {
                            if (!isTripDistanceEdited) {
                                tripDistance = diff.toString()
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
                        missingEventWarning = "No previous odometer recorded for this vehicle. You might be missing a Fueling event."
                    }
                } else {
                    missingEventWarning = parsedCandidate?.warningMessage
                }
            } else {
                missingEventWarning = parsedCandidate?.warningMessage ?: "Select a vehicle to calculate trip distance from odometer."
            }
        } else {
            missingEventWarning = null
        }
    }

    LaunchedEffect(parsedCandidate) {
        if (parsedCandidate != null) {
            if (!isStationNameEdited && !parsedCandidate.stationName.isNullOrEmpty()) {
                stationName = parsedCandidate.stationName
            }
            if (!isPurchaseDateEdited && !parsedCandidate.purchaseDate.isNullOrEmpty()) {
                purchaseDate = parsedCandidate.purchaseDate
            }
            if (!isGallonsEdited && parsedCandidate.gallons != null) {
                gallons = parsedCandidate.gallons.toString()
            }
            if (!isPricePerGallonEdited && parsedCandidate.pricePerGallon != null) {
                pricePerGallon = parsedCandidate.pricePerGallon.toString()
            }
            if (!isTotalCostEdited && parsedCandidate.totalCost != null) {
                totalCost = parsedCandidate.totalCost.toString()
            }
            if (!isOdometerEdited && parsedCandidate.odometer != null) {
                odometer = parsedCandidate.odometer.toString()
            }
            if (!isTripDistanceEdited && parsedCandidate.tripDistance != null) {
                tripDistance = parsedCandidate.tripDistance.toString()
            }
        }
    }

    var showImageDialog by remember { mutableStateOf(false) }
    var dialogImageUri by remember { mutableStateOf<String?>(null) }
    var showAddPhotoDialog by remember { mutableStateOf(false) }

    val selectedGalleryPaths = remember { mutableStateListOf<String>() }
    val selectedUngroupedItems = remember { mutableStateListOf<ReviewItem>() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val localPath = copyUriToInternalStorage(context, uri)
            if (localPath !in selectedGalleryPaths) {
                selectedGalleryPaths.add(localPath)
            }
        }
    }

    val cardBackgroundColor = Color(0xFFEFE8F4)
    val cardShape = RoundedCornerShape(16.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Detail", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            saveError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Photo Card Header with Carousel and Group Management
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Main Photo Display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                            .clickable(enabled = !activeItem?.photoPath.isNullOrEmpty()) {
                                dialogImageUri = activeItem?.photoPath
                                showImageDialog = true
                            }
                    ) {
                        val photoPath = activeItem?.photoPath
                        if (!photoPath.isNullOrBlank()) {
                            val data = if (photoPath.startsWith("content://") || photoPath.startsWith("file://")) Uri.parse(photoPath) else photoPath
                            val model = ImageRequest.Builder(context)
                                .data(data)
                                .listener(onError = { _, _ -> DiagnosticLogger.e("ImageLoad", "Failed to load photo: $photoPath") })
                                .build()

                            AsyncImage(
                                model = model,
                                contentDescription = "Review Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "No photo",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF757575)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group Photo Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photos in Group (${groupItems.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = { showAddPhotoDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Photo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photo", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Group Photo Strip
                    if (groupItems.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(groupItems) { idx, item ->
                                val isSelected = idx == selectedPhotoIndex
                                Box(
                                    modifier = Modifier
                                        .size(108.dp)
                                        .clickable { selectedPhotoIndex = idx }
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        val path = item.photoPath
                                        if (!path.isNullOrBlank()) {
                                            val data = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else path
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(data).build(),
                                                contentDescription = "Thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("📷", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }

                                    // Remove button on thumbnail
                                    IconButton(
                                        onClick = {
                                            reviewQueueViewModel.removeFromGroup(item)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .padding(2.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xCC000000)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove photo from group",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .padding(2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DetailRow(label = "Reason", value = activeItem?.reason ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Capture Date", value = activeItem?.let { formatDate(it.captureDate) } ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Status", value = activeItem?.let { formatStatus(it.status) } ?: "N/A")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(
                        label = "Confidence",
                        value = activeItem?.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Created", value = activeItem?.let { formatDate(it.createdDate) } ?: "N/A")
                }
            }

            // Vehicle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vehicle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var dropdownExpanded by remember { mutableStateOf(false) }
                    val selectedVehicle = vehicles.find { it.id == selectedVehicleId }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedVehicle?.let { "${it.year} ${it.make} ${it.model} (${it.licensePlate ?: "No Plate"})" }
                                        ?: "Select a vehicle",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select vehicle")
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            if (vehicles.isNotEmpty()) {
                                vehicles.forEach { vehicle ->
                                    DropdownMenuItem(
                                        text = { Text("${vehicle.year} ${vehicle.make} ${vehicle.model} (${vehicle.licensePlate ?: "No Plate"})") },
                                        onClick = {
                                            userSelectedVehicleId = vehicle.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "+ Add New Vehicle",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    navController.navigate(Screen.AddVehicle.route)
                                }
                            )
                        }
                    }
                }
            }

            // Suggested Values Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggested Values",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (activeItem != null) {
                            val isProcessing = ocrProcessingIds.contains(activeItem.id)
                            TextButton(
                                onClick = { reviewQueueViewModel.processOcr(activeItem.id) },
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Extracting...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("⚡ Re-extract Data", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    SuggestedValueField(
                        label = "Station Name",
                        value = stationName,
                        onValueChange = {
                            stationName = it
                            isStationNameEdited = (it != initialStationName)
                        },
                        detected = parsedCandidate?.stationName != null,
                        isEdited = isStationNameEdited
                    )

                    SuggestedValueField(
                        label = "Purchase Date",
                        value = purchaseDate,
                        onValueChange = {
                            purchaseDate = it
                            isPurchaseDateEdited = (it != initialPurchaseDate)
                        },
                        detected = parsedCandidate?.purchaseDate != null,
                        isEdited = isPurchaseDateEdited
                    )

                    SuggestedValueField(
                        label = "Gallons",
                        value = gallons,
                        onValueChange = {
                            gallons = it
                            isGallonsEdited = (it != initialGallons)
                        },
                        detected = parsedCandidate?.gallons != null,
                        isEdited = isGallonsEdited
                    )

                    SuggestedValueField(
                        label = "Price Per Gallon",
                        value = pricePerGallon,
                        onValueChange = {
                            pricePerGallon = it
                            isPricePerGallonEdited = (it != initialPricePerGallon)
                        },
                        detected = parsedCandidate?.pricePerGallon != null,
                        isEdited = isPricePerGallonEdited,
                        onCalculateClick = {
                            val total = totalCost.replace("$", "").replace(",", "").trim().toDoubleOrNull()
                            val gal = gallons.replace(",", "").trim().toDoubleOrNull()
                            if (total != null && gal != null && gal > 0) {
                                val calculated = total / gal
                                pricePerGallon = String.format(Locale.US, "%.3f", calculated)
                                isPricePerGallonEdited = (pricePerGallon != initialPricePerGallon)
                            }
                        },
                        calculateLabel = "Calc ($/gal)"
                    )

                    SuggestedValueField(
                        label = "Total Cost",
                        value = totalCost,
                        onValueChange = {
                            totalCost = it
                            isTotalCostEdited = (it != initialTotalCost)
                        },
                        detected = parsedCandidate?.totalCost != null,
                        isEdited = isTotalCostEdited,
                        onCalculateClick = {
                            val ppg = pricePerGallon.replace("$", "").replace(",", "").trim().toDoubleOrNull()
                            val gal = gallons.replace(",", "").trim().toDoubleOrNull()
                            if (ppg != null && gal != null && gal > 0) {
                                val calculated = gal * ppg
                                totalCost = String.format(Locale.US, "%.2f", calculated)
                                isTotalCostEdited = (totalCost != initialTotalCost)
                            }
                        },
                        calculateLabel = "Calc (gal × $/gal)"
                    )

                    SuggestedValueField(
                        label = "Odometer",
                        value = odometer,
                        onValueChange = {
                            odometer = it
                            isOdometerEdited = (it != initialOdometer)
                        },
                        detected = parsedCandidate?.odometer != null,
                        isEdited = isOdometerEdited
                    )

                    SuggestedValueField(
                        label = "Trip Distance",
                        value = tripDistance,
                        onValueChange = {
                            tripDistance = it
                            isTripDistanceEdited = (it != initialTripDistance)
                        },
                        detected = parsedCandidate?.tripDistance != null,
                        isEdited = isTripDistanceEdited
                    )

                    calculatedMpg?.let { mpg ->
                        Text(
                            text = "MPG: $mpg",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    missingEventWarning?.let { warning ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeItem?.let {
                                    reviewQueueViewModel.updateStatus(it, ProcessingStatus.PENDING)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = if (activeItem?.status == ProcessingStatus.PENDING) {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("Pending")
                        }

                        OutlinedButton(
                            onClick = {
                                activeItem?.let {
                                    reviewQueueViewModel.updateStatus(it, ProcessingStatus.COMPLETE)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = if (activeItem?.status == ProcessingStatus.COMPLETE) {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("Complete")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val canSave = selectedVehicleId != null && selectedVehicleId!! > 0
                    Button(
                        onClick = {
                            val vehId = selectedVehicleId
                            if (activeItem != null && vehId != null && vehId > 0) {
                                val candidate = FuelPurchaseCandidate(
                                    stationName = stationName.ifBlank { null },
                                    purchaseDate = purchaseDate.ifBlank { null },
                                    gallons = gallons.toDoubleOrNull(),
                                    pricePerGallon = pricePerGallon.toDoubleOrNull(),
                                    totalCost = totalCost.toDoubleOrNull(),
                                    odometer = odometer.toIntOrNull(),
                                    tripDistance = tripDistance.toDoubleOrNull()
                                )

                                if (isStationNameEdited) DiagnosticLogger.i("AILearning", "User edited stationName: AI detected='${parsedCandidate?.stationName}' -> User edited='$stationName'")
                                if (isPurchaseDateEdited) DiagnosticLogger.i("AILearning", "User edited purchaseDate: AI detected='${parsedCandidate?.purchaseDate}' -> User edited='$purchaseDate'")
                                if (isGallonsEdited) DiagnosticLogger.i("AILearning", "User edited gallons: AI detected='${parsedCandidate?.gallons}' -> User edited='$gallons'")
                                if (isPricePerGallonEdited) DiagnosticLogger.i("AILearning", "User edited pricePerGallon: AI detected='${parsedCandidate?.pricePerGallon}' -> User edited='$pricePerGallon'")
                                if (isTotalCostEdited) DiagnosticLogger.i("AILearning", "User edited totalCost: AI detected='${parsedCandidate?.totalCost}' -> User edited='$totalCost'")
                                if (isOdometerEdited) DiagnosticLogger.i("AILearning", "User edited odometer: AI detected='${parsedCandidate?.odometer}' -> User edited='$odometer'")
                                if (isTripDistanceEdited) DiagnosticLogger.i("AILearning", "User edited tripDistance: AI detected='${parsedCandidate?.tripDistance}' -> User edited='$tripDistance'")

                                val currentTargetEventId = targetEventId?.takeIf { it > 0L }
                                    ?: activeItem.eventId?.takeIf { it > 0L }
                                    ?: groupItems.firstOrNull { it.eventId != null && it.eventId!! > 0L }?.eventId

                                reviewQueueViewModel.saveGroupItemsAsFuelEvent(
                                    targetEventId = currentTargetEventId,
                                    groupItems = if (groupItems.isNotEmpty()) groupItems else listOf(activeItem),
                                    vehicleId = vehId,
                                    editedCandidate = candidate
                                )
                                navController.navigateUp()
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD1C4E9),
                            disabledContainerColor = Color(0xFFE0E0E0)
                        )
                    ) {
                        Text("Save as Fuel Event", color = if (canSave) Color(0xFF311B92) else Color(0xFF757575))
                    }

                    if (!canSave) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select a vehicle to enable saving",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (activeItem != null) {
                                reviewQueueViewModel.deleteItem(activeItem)
                                navController.navigateUp()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Remove from Review Queue", color = Color.White)
                    }
                }
            }

            // Note Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "The original photo remains on your phone.\n" +
                               "Vehicle Log AI only stores a reference to it until you create a fuel record.\n\n" +
                               "OCR text and parser results are only working copies used to create a Fuel Event.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Raw OCR Text Card
            val ocrDisplayItems = remember(groupItems, activeItem, currentItem) {
                if (groupItems.isNotEmpty()) groupItems else listOfNotNull(activeItem ?: currentItem)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Raw OCR Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val isProcessingAny = ocrDisplayItems.any { ocrProcessingIds.contains(it.id) }
                        if (ocrDisplayItems.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    ocrDisplayItems.forEach { reviewQueueViewModel.processOcr(it.id) }
                                },
                                enabled = !isProcessingAny,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (isProcessingAny) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Extracting...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("Re-extract Data", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (ocrDisplayItems.isEmpty()) {
                        Text(
                            text = "No OCR data yet. Tap \"Re-extract Data\" to process this photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        ocrDisplayItems.forEachIndexed { index, item ->
                            val fileName = item.reason?.takeIf { it.startsWith("Imported: ") }
                                ?.removePrefix("Imported: ")
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }
                                ?: item.photoPath?.let { path ->
                                    val decoded = try { Uri.decode(path) } catch (_: Exception) { path }
                                    val candidate = decoded.substringAfterLast('/')
                                        .substringAfterLast('\\')
                                        .trim()
                                    if (candidate.isNotBlank() && !candidate.startsWith("content:", ignoreCase = true)) {
                                        candidate
                                    } else null
                                }

                            val photoTitle = if (!fileName.isNullOrBlank()) "Photo ${index + 1}: $fileName" else "Photo ${index + 1}"

                            Text(
                                text = photoTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (!item.ocrText.isNullOrBlank()) {
                                SelectionContainer {
                                    Text(
                                        text = item.ocrText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Text(
                                    text = "No OCR data available for this photo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (index < ocrDisplayItems.size - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Photo to Group Dialog
    if (showAddPhotoDialog) {
        val ungroupedList = remember(reviewItems, groupItems, selectedUngroupedItems.toList()) {
            val baseItems = if (groupItems.isNotEmpty()) groupItems else listOfNotNull(activeItem ?: currentItem)
            val available = reviewItems.filter { it !in groupItems && it !in selectedUngroupedItems }
            if (baseItems.isEmpty()) {
                emptyList()
            } else {
                val combinedItems = baseItems + selectedUngroupedItems
                val minDate = combinedItems.minOf { it.captureDate }
                val maxDate = combinedItems.maxOf { it.captureDate }

                val photoBefore = available.filter { it.captureDate <= minDate }.maxByOrNull { it.captureDate }
                val photoAfter = available.filter { it.captureDate >= maxDate }.minByOrNull { it.captureDate }

                listOfNotNull(photoBefore, photoAfter).distinctBy { it.id }.sortedBy { it.captureDate }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddPhotoDialog = false
                selectedGalleryPaths.clear()
                selectedUngroupedItems.clear()
            },
            title = { Text("Add Photos to Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            try {
                                photoPickerLauncher.launch("image/*")
                            } catch (e: Exception) {
                                DiagnosticLogger.e("PhotoPicker", "Failed to launch gallery picker", e)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📁 Pick Photos from Gallery")
                    }

                    if (ungroupedList.isNotEmpty()) {
                        Text(
                            text = "Or select adjacent photos:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ungroupedList) { uItem ->
                                val baseItems = if (groupItems.isNotEmpty()) groupItems else listOfNotNull(activeItem ?: currentItem)
                                val combinedItems = baseItems + selectedUngroupedItems
                                val baseMinDate = combinedItems.minOfOrNull { it.captureDate } ?: 0L
                                val baseMaxDate = combinedItems.maxOfOrNull { it.captureDate } ?: 0L
                                val isBefore = if (baseMinDate > 0L && baseMaxDate > 0L) {
                                    if (uItem.captureDate <= baseMinDate) true
                                    else if (uItem.captureDate >= baseMaxDate) false
                                    else uItem.captureDate < (baseMinDate + baseMaxDate) / 2
                                } else {
                                    true
                                }
                                val targetRefDate = if (isBefore) baseMinDate else baseMaxDate
                                val diffMinutes = if (targetRefDate > 0L) kotlin.math.abs(uItem.captureDate - targetRefDate) / 60000L else 0L
                                val formattedDiff = if (diffMinutes < 60) "${diffMinutes}m" else "${diffMinutes / 60}h ${diffMinutes % 60}m"
                                val labelText = if (isBefore) "Earlier (-$formattedDiff)" else "Later (+$formattedDiff)"

                                Surface(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clickable {
                                            selectedUngroupedItems.add(uItem)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    val uPath = uItem.photoPath
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (!uPath.isNullOrBlank()) {
                                            val uData = if (uPath.startsWith("content://") || uPath.startsWith("file://")) Uri.parse(uPath) else uPath
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(uData).build(),
                                                contentDescription = "Ungrouped Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("📷")
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth(),
                                            color = Color.Black.copy(alpha = 0.65f)
                                        ) {
                                            Text(
                                                text = labelText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val totalSelected = selectedGalleryPaths.size + selectedUngroupedItems.size
                    if (totalSelected > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Selected Files ($totalSelected):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedGalleryPaths) { gPath ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(gPath).build(),
                                        contentDescription = "Selected Gallery Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clickable { selectedGalleryPaths.remove(gPath) },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "Gallery",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(1.dp)
                                        )
                                    }
                                }
                            }

                            items(selectedUngroupedItems) { uItem ->
                                val baseItems = if (groupItems.isNotEmpty()) groupItems else listOfNotNull(activeItem ?: currentItem)
                                val baseMinDate = baseItems.minOfOrNull { it.captureDate } ?: 0L
                                val baseMaxDate = baseItems.maxOfOrNull { it.captureDate } ?: 0L
                                val isBefore = if (baseMinDate > 0L && baseMaxDate > 0L) {
                                    if (uItem.captureDate <= baseMinDate) true
                                    else if (uItem.captureDate >= baseMaxDate) false
                                    else uItem.captureDate < (baseMinDate + baseMaxDate) / 2
                                } else {
                                    true
                                }
                                val targetRefDate = if (isBefore) baseMinDate else baseMaxDate
                                val diffMinutes = if (targetRefDate > 0L) kotlin.math.abs(uItem.captureDate - targetRefDate) / 60000L else 0L
                                val formattedDiff = if (diffMinutes < 60) "${diffMinutes}m" else "${diffMinutes / 60}h ${diffMinutes % 60}m"
                                val labelText = if (isBefore) "Earlier (-$formattedDiff)" else "Later (+$formattedDiff)"

                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    val uPath = uItem.photoPath
                                    if (!uPath.isNullOrBlank()) {
                                        val uData = if (uPath.startsWith("content://") || uPath.startsWith("file://")) Uri.parse(uPath) else uPath
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(uData).build(),
                                            contentDescription = "Selected Adjacent Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("📷")
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clickable { selectedUngroupedItems.remove(uItem) },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        color = Color.Black.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val totalSelected = selectedGalleryPaths.size + selectedUngroupedItems.size
                Button(
                    onClick = {
                        val galleryToAdd = selectedGalleryPaths.toList()
                        val itemsToAdd = selectedUngroupedItems.toList()

                        showAddPhotoDialog = false
                        selectedGalleryPaths.clear()
                        selectedUngroupedItems.clear()

                        reviewQueueViewModel.addPhotosBatchToGroup(
                            targetEventId = targetEventId,
                            activeItem = activeItem,
                            galleryUris = galleryToAdd,
                            reviewItemsToAdd = itemsToAdd
                        )
                    },
                    enabled = totalSelected > 0
                ) {
                    Text(if (totalSelected > 0) "Done ($totalSelected)" else "Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddPhotoDialog = false
                        selectedGalleryPaths.clear()
                        selectedUngroupedItems.clear()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen image zoom dialog
    if (showImageDialog && dialogImageUri != null) {
        Dialog(
            onDismissRequest = {
                showImageDialog = false
                dialogImageUri = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val uriString = dialogImageUri
                if (!uriString.isNullOrBlank()) {
                    val model = ImageRequest.Builder(context)
                        .data(if (uriString.startsWith("content://") || uriString.startsWith("file://")) Uri.parse(uriString) else uriString)
                        .listener(onError = { _, _ -> DiagnosticLogger.e("ImageLoad", "Failed to load dialog photo: $dialogImageUri") })
                        .build()

                    AsyncImage(
                        model = model,
                        contentDescription = "Full screen photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun SuggestedValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    detected: Boolean,
    isEdited: Boolean = false,
    onCalculateClick: (() -> Unit)? = null,
    calculateLabel: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = if (onCalculateClick != null) {
                            Modifier.clickable { onCalculateClick() }
                        } else Modifier
                    ) {
                        Text(label)
                        if (onCalculateClick != null) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = calculateLabel ?: "Calculate",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeText = when {
                    isEdited -> "User edit"
                    detected -> "Detected"
                    else -> "Not found"
                }

                val badgeBgColor = when {
                    isEdited -> Color(0xFFE3F2FD)
                    detected -> Color(0xFFE8F5E9)
                    else -> Color(0xFFFCE4EC)
                }

                val badgeTextColor = when {
                    isEdited -> Color(0xFF1565C0)
                    detected -> Color(0xFF2E7D32)
                    else -> Color(0xFFC62828)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (!detected && !isEdited) {
            Text(
                text = "Not detected",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFC62828),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}
