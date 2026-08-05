package com.schortgen.vehiclelogai.ui.reviewdetail

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel

private fun isNonVehiclePhoto(item: com.schortgen.vehiclelogai.data.models.ReviewItem): Boolean {
    val nonVehicle = setOf("flower", "flowers", "plant", "garden", "nature", "pet", "dog", "cat", "food", "dish", "meal", "selfie", "portrait", "family", "vacation", "beach", "party", "sunset", "sky")
    val path = item.photoPath?.lowercase() ?: ""
    val ocr = item.ocrText?.lowercase() ?: ""
    val reason = item.reason?.lowercase() ?: ""

    val matchesNonVehicle = nonVehicle.any { path.contains(it) || ocr.contains(it) || reason.contains(it) }
    val matchesVehicle = setOf("fuel", "gallons", "receipt", "odometer", "mileage", "station", "pump", "gas", "total", "cost", "service", "oil", "tire").any {
        path.contains(it) || ocr.contains(it) || reason.contains(it)
    }

    return matchesNonVehicle && !matchesVehicle
}

private fun getPhotoBadgeLabel(item: com.schortgen.vehiclelogai.data.models.ReviewItem): String {
    val text = ((item.ocrText ?: "") + " " + (item.photoPath ?: "") + " " + (item.reason ?: "")).lowercase()
    return when {
        text.contains("odometer") || text.contains("mileage") -> "Odometer"
        text.contains("receipt") || text.contains("gallons") || text.contains("total") -> "Receipt"
        text.contains("pump") || text.contains("station") || text.contains("gas") -> "Gas Station / Pump"
        else -> "Event Photo"
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

    // Observe the current list of review items so we can find photos for this item or event
    val reviewItems by reviewQueueViewModel.reviewItems.collectAsState()

    // Build list of image URIs & descriptions to display for this screen
    val groupedPhotos by remember(reviewItems, reviewItemId, eventId) {
        mutableStateOf(
            if (eventId >= 0) {
                // Show all vehicle photos associated with this event
                reviewItems.filter { it.eventId == eventId }
            } else {
                val currentItem = reviewItems.find { it.id == reviewItemId }
                if (currentItem != null) {
                    // Group photos from the same date (same calendar day) for this vehicle event
                    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = currentItem.captureDate }
                    val year1 = cal1.get(java.util.Calendar.YEAR)
                    val day1 = cal1.get(java.util.Calendar.DAY_OF_YEAR)

                    val sameDateItems = reviewItems.filter { other ->
                        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = other.captureDate }
                        val sameDay = cal2.get(java.util.Calendar.YEAR) == year1 && cal2.get(java.util.Calendar.DAY_OF_YEAR) == day1
                        sameDay && !isNonVehiclePhoto(other)
                    }
                    if (sameDateItems.isNotEmpty()) sameDateItems else listOf(currentItem)
                } else emptyList()
            }
        )
    }

    var showImageDialog by remember { mutableStateOf(false) }
    var dialogImageUri by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review details") },
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
                .padding(16.dp)
        ) {
            Text(text = "Review item id: $reviewItemId", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Grouped Event Photos (${groupedPhotos.size})",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (groupedPhotos.isEmpty()) {
                // No images available for this review item — show placeholder text
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No photo available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(groupedPhotos) { item ->
                        val uriString = item.photoPath ?: ""
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .height(210.dp)
                                .clickable {
                                    dialogImageUri = uriString
                                    showImageDialog = true
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val data = if (uriString.startsWith("content://") || uriString.startsWith("file://")) Uri.parse(uriString) else uriString
                                val model = ImageRequest.Builder(context)
                                    .data(data)
                                    .listener(onError = { _, _ -> DiagnosticLogger.e("ImageLoad", "Failed to load photo: $uriString") })
                                    .build()

                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    AsyncImage(
                                        model = model,
                                        contentDescription = "Event Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = getPhotoBadgeLabel(item),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Event photos from the same date are automatically grouped for your review. Photos unrelated to the vehicle event (such as nature or flower photos) are excluded.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // Full-screen image dialog with pinch-to-zoom & pan
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
                val uriString = dialogImageUri!!
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
