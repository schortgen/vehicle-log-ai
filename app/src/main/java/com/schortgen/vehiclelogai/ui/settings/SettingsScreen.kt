package com.schortgen.vehiclelogai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import com.schortgen.vehiclelogai.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val preferredTripMeter by viewModel.preferredTripMeter.collectAsState()
    val movePhotosOnComplete by viewModel.movePhotosOnComplete.collectAsState()
    val completedPhotosFolderUri by viewModel.completedPhotosFolderUri.collectAsState()
    val completedPhotosFolderName by viewModel.completedPhotosFolderName.collectAsState()
    val isBackupInProgress by viewModel.isBackupInProgress.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val discoveredBackupFiles by viewModel.discoveredBackupFiles.collectAsState()
    val isScanningBackups by viewModel.isScanningBackups.collectAsState()
    val isMigratingPhotos by viewModel.isMigratingPhotos.collectAsState()
    val migrationStatusMessage by viewModel.migrationStatusMessage.collectAsState()
    val isRelinkingPhotos by viewModel.isRelinkingPhotos.collectAsState()
    val relinkReport by viewModel.relinkReport.collectAsState()

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePickerDialog by remember { mutableStateOf(false) }
    var pendingMoveFolderUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMoveFolderName by remember { mutableStateOf<String?>(null) }
    var showMoveConfirmDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: uri.lastPathSegment ?: "Custom Folder"
            viewModel.setCompletedPhotosFolder(uri.toString(), folderName)
            viewModel.updateMovePhotosOnComplete(true)
        }
    }

    val moveFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: uri.lastPathSegment ?: "Selected Folder"
            pendingMoveFolderUri = uri
            pendingMoveFolderName = folderName
            showMoveConfirmDialog = true
        }
    }

    val relinkFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            viewModel.relinkPhotos(context, customFolderTreeUri = uri)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri)
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportZipBackup(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Preferred Trip Meter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Car Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Trip Meter Preference",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Choose which trip meter should be selected by default when both Trip A and Trip B are detected on dashboard photos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferredTripMeter == PreferredTripMeter.TRIP_A,
                            onClick = { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_A) },
                            label = { Text("Trip A (Default)") },
                            leadingIcon = if (preferredTripMeter == PreferredTripMeter.TRIP_A) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = preferredTripMeter == PreferredTripMeter.TRIP_B,
                            onClick = { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_B) },
                            label = { Text("Trip B") },
                            leadingIcon = if (preferredTripMeter == PreferredTripMeter.TRIP_B) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Photo Management & Storage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Storage Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Photo Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Organize where verified receipts and dashboard photos are saved after events are accepted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Move completed photos",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (movePhotosOnComplete) "Active - photos are moved on approval" else "Disabled - photos stay in inbox",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = movePhotosOnComplete,
                            onCheckedChange = { checked ->
                                viewModel.updateMovePhotosOnComplete(checked)
                                if (checked && completedPhotosFolderUri == null) {
                                    folderPickerLauncher.launch(null)
                                }
                            }
                        )
                    }

                    if (movePhotosOnComplete) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Target Folder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = completedPhotosFolderName ?: "Pictures/ProcessedVehiclePhotos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Folder")
                            }

                            Button(
                                onClick = { moveFolderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                enabled = !isMigratingPhotos
                            ) {
                                if (isMigratingPhotos) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Moving...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DriveFileMove,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Move All Existing")
                                }
                            }
                        }
                    }
                }
            }

            // Locate & Relink Photos Card (Repair Missing/Moved Photos)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Relink Photos Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Locate & Relink Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "If photos are not displaying after a backup restore, phone transfer, or folder relocation, use this tool to scan storage or choose a folder to automatically reconnect all photo links.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isRelinkingPhotos) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Scanning and relinking photo paths...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.relinkPhotos(context) },
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Relink All")
                            }

                            OutlinedButton(
                                onClick = { relinkFolderPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick Folder to Relink")
                            }
                        }
                    }
                }
            }

            // Backup & Restore Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup & Restore Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Export a full standalone archive (data + physical photo files as .ZIP) or a lightweight database file (.JSON), and restore anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isBackupInProgress) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Processing backup / restore...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Full ZIP Export Button
                            Button(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    exportZipLauncher.launch("VehicleLogAI_CompleteBackup_$dateStr.zip")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Complete Backup (.ZIP with Photos)")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        exportJsonLauncher.launch("VehicleLogAI_Backup_$dateStr.json")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export JSON")
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.scanForBackupFiles(context)
                                        showRestorePickerDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore Backup")
                                }
                            }
                        }
                    }
                }
            }

            // Diagnostics & Debug Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Diagnostics & System Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "View app metrics, database statistics, OCR success rates, uncaught crash reports, and diagnostic logs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Debug.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Diagnostics & Debug Screen")
                    }
                }
            }

            // About & Usage Tips Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "About Trip Meters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "• Trip A is commonly used for individual trips or fuel-fill intervals.\n" +
                                "• Trip B is commonly used for oil-change or maintenance intervals.\n" +
                                "• When both are detected in a dashboard photo, your preferred meter is selected automatically, but you can always switch it on the review screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Confirmation dialog before executing restore
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup?") },
            text = {
                Text("Restoring will replace all existing vehicles, timeline events, and review queue items with the contents of this backup.\n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = uri
                        pendingRestoreUri = null
                        viewModel.importBackup(context, toRestore)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Result dialog showing backup/restore outcome
    backupStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatusMessage() },
            title = { Text("Backup Status") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.clearStatusMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // Relink Report Dialog
    relinkReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.clearRelinkReport() },
            icon = {
                Icon(
                    imageVector = if (report.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (report.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (report.isSuccess) "Photo Relink Complete" else "Relink Encountered Issues",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Records checked: ${report.totalChecked}\n" +
                                "• Successfully relinked: ${report.relinkedCount}\n" +
                                "• Already valid: ${report.alreadyValidCount}\n" +
                                "• Unresolved: ${report.unresolvedCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (report.errorMessage != null) {
                        Text(
                            text = "Error: ${report.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (report.relinkedCount > 0) {
                        Text(
                            text = "All newly relinked photos are now visible in your vehicle timelines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearRelinkReport() }) {
                    Text("OK")
                }
            }
        )
    }

    // Confirmation dialog before moving all photos to a new folder
    if (showMoveConfirmDialog && pendingMoveFolderUri != null && pendingMoveFolderName != null) {
        AlertDialog(
            onDismissRequest = {
                showMoveConfirmDialog = false
                pendingMoveFolderUri = null
                pendingMoveFolderName = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Move All Photos?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Move all existing vehicle photos to \"${pendingMoveFolderName}\"?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will physically move all photos from the current folder to the new location and update all timeline photo links in your vehicle records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingMoveFolderUri!!
                        val name = pendingMoveFolderName!!
                        showMoveConfirmDialog = false
                        pendingMoveFolderUri = null
                        pendingMoveFolderName = null
                        viewModel.moveAllPhotosToNewFolder(uri, name)
                    }
                ) {
                    Text("Move Photos")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMoveConfirmDialog = false
                        pendingMoveFolderUri = null
                        pendingMoveFolderName = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Result dialog for photo migration
    migrationStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearMigrationStatusMessage() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Photo Relocation", fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.clearMigrationStatusMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // In-App Backup File Picker Dialog
    if (showRestorePickerDialog) {
        RestoreBackupPickerDialog(
            backupFiles = discoveredBackupFiles,
            isScanning = isScanningBackups,
            onSelectFile = { uri ->
                showRestorePickerDialog = false
                pendingRestoreUri = uri
            },
            onSelectFolderUri = { folderUri ->
                viewModel.scanFolderTreeUri(context, folderUri)
            },
            onBrowseSystemPicker = {
                showRestorePickerDialog = false
                restoreLauncher.launch(
                    arrayOf(
                        "*/*",
                        "application/json",
                        "application/zip",
                        "application/x-zip-compressed",
                        "text/json",
                        "text/plain",
                        "application/octet-stream"
                    )
                )
            },
            onRescan = {
                viewModel.scanForBackupFiles(context)
            },
            onDismiss = { showRestorePickerDialog = false }
        )
    }
}
