package com.schortgen.vehiclelogai.ui.reviewdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import android.net.Uri
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.data.models.FuelPurchasesCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel,
    reviewItemId: Long,
    eventId: Long = -1L
) {
    val context = LocalContext.current

    // ... existing code ...

    // Example AsyncImage replacement — wherever AsyncImage was used, we now build an ImageRequest
    val sampleUri = "content://media/external/images/media/12345"
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(Uri.parse(sampleUri))
            .listener(onError = { request, throwable ->
                DiagnosticLogger.e("ImageLoad", "Failed to load photo: $sampleUri", throwable)
            })
            .build(),
        contentDescription = "Sample photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(200.dp)
            .clip(MaterialTheme.shapes.medium)
    )
}
