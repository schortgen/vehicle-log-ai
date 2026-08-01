package com.schortgen.vehiclelogai.ui.scanphotos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPhotosScreen() {
    var isScanning by remember { mutableStateOf(false) }
    var scannedCount by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val app = context.applicationContext as VehicleLogAIApplication

    LaunchedEffect(Unit) {
        isScanning = true
        // Run on background coroutine
        val count = app.photoScannerService.scanAndImport()
        scannedCount = count
        isScanning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scan Photos") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isScanning) {
                CircularProgressIndicator()
                Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = "Scanned ${scannedCount ?: 0} new photos.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = {
                    // Re‑trigger scan
                    isScanning = true
                    scannedCount = null
                    // Note: launching a new coroutine; using LaunchedEffect with a key
                }) {
                    Text("Scan Again")
                }
            }
        }
    }
}
