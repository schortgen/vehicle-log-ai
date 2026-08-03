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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel,
    reviewItemId: Long,
    eventId: Long = -1L
) {
    val context = LocalContext.current

    // Replace these sample URIs with your real data source
    val sampleImageUris = remember {
        listOf(
            "content://media/external/images/media/12345",
            "content://media/external/images/media/67890",
            "content://media/external/images/media/11121"
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

            Text(text = "Photos", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sampleImageUris) { uriString ->
                    val uri = Uri.parse(uriString)
                    Card(
                        modifier = Modifier
                            .size(160.dp)
                            .clickable {
                                dialogImageUri = uriString
                                showImageDialog = true
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .listener(onError = { _, _ ->
                                    // Avoid passing ErrorResult where a Throwable was expected.
                                    DiagnosticLogger.e("ImageLoad", "Failed to load photo: $uriString")
                                })
                                .build(),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Details and actions go here. Replace placeholders with your actual UI.",
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
                        // Explicit parameter types so the compiler can resolve the correct overload
                        detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val uri = Uri.parse(dialogImageUri!!)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .listener(onError = { _, _ ->
                            DiagnosticLogger.e("ImageLoad", "Failed to load dialog photo: $dialogImageUri")
                        })
                        .build(),
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