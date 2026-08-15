package com.schortgen.vehiclelogai.ui.settings

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.schortgen.vehiclelogai.util.BackupFileItem
import com.schortgen.vehiclelogai.util.BackupFileScanner
import com.schortgen.vehiclelogai.util.FolderNode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupPickerDialog(
    backupFiles: List<BackupFileItem> = emptyList(),
    isScanning: Boolean = false,
    onSelectFile: (Uri) -> Unit,
    onSelectFolderUri: (Uri) -> Unit,
    onBrowseSystemPicker: () -> Unit,
    onRescan: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Discovered Backups, 1 = Browse Folders
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyJson by remember { mutableStateOf(false) }

    val rootDir = remember {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) ext else Environment.getRootDirectory()
    }
    var currentFolder by remember { mutableStateOf(rootDir) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onSelectFolderUri(uri)
        }
    }

    // Quick access shortcut directories
    val shortcutDirs = remember {
        val list = mutableListOf<File>()
        listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES
        ).forEach { dirType ->
            runCatching {
                val d = Environment.getExternalStoragePublicDirectory(dirType)
                if (d != null && d.exists()) list.add(d)
            }
        }
        list
    }

    // Filtered discovered backup files
    val filteredDiscoveredFiles = remember(backupFiles, searchQuery, showOnlyJson) {
        backupFiles.filter { item ->
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.path.contains(searchQuery, ignoreCase = true)
            val matchesType = !showOnlyJson || item.isJson
            matchesSearch && matchesType
        }
    }

    val folderContents = remember(currentFolder, searchQuery) {
        val files = currentFolder.listFiles() ?: emptyArray()
        val nodes = mutableListOf<FolderNode>()
        for (f in files) {
            if (f.name.startsWith(".") || f.name.equals("Android", ignoreCase = true)) continue
            if (searchQuery.isNotBlank() && !f.name.contains(searchQuery, ignoreCase = true)) continue

            if (f.isDirectory) {
                val subFiles = f.listFiles() ?: emptyArray()
                val jsonCount = subFiles.count { it.name.endsWith(".json", ignoreCase = true) }
                nodes.add(
                    FolderNode(
                        name = f.name,
                        file = f,
                        isDirectory = true,
                        containsBackupFilesCount = jsonCount
                    )
                )
            } else {
                val item = BackupFileItem(
                    name = f.name,
                    uri = Uri.fromFile(f),
                    sizeBytes = f.length(),
                    dateModifiedMs = f.lastModified(),
                    path = f.absolutePath,
                    formattedSize = BackupFileScanner.formatFileSize(f.length()),
                    formattedDate = BackupFileScanner.formatDate(f.lastModified()),
                    isJson = f.name.endsWith(".json", ignoreCase = true)
                )
                nodes.add(
                    FolderNode(
                        name = f.name,
                        file = f,
                        isDirectory = false,
                        backupItem = item
                    )
                )
            }
        }
        nodes.sortedWith(
            compareByDescending<FolderNode> { it.isDirectory }
                .thenByDescending { it.backupItem?.isJson == true }
                .thenBy { it.name.lowercase() }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Restore VehicleLogAI Backup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a backup JSON file to restore your vehicles and events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scanning device storage for backups...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fast Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onBrowseSystemPicker,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("System File Picker", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Folder (SAF)", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Row: Discovered Backups vs Folder Explorer
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Discovered Files (${backupFiles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Folder Explorer",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (selectedTab == 0) "Search backup files..." else "Filter current folder...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !showOnlyJson,
                            onClick = { showOnlyJson = false },
                            label = { Text("All Files (${backupFiles.size})", style = MaterialTheme.typography.labelSmall) }
                        )
                        val jsonCount = remember(backupFiles) { backupFiles.count { it.isJson } }
                        FilterChip(
                            selected = showOnlyJson,
                            onClick = { showOnlyJson = true },
                            label = { Text("JSON Backups (${jsonCount})", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                        if (onRescan != null) {
                            FilledTonalIconButton(
                                onClick = onRescan,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rescan", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Discovered Backups List
                    if (filteredDiscoveredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No backup files found on device storage.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Use the System File Picker to locate your JSON backup file anywhere on your device or Google Drive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onBrowseSystemPicker,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open System File Picker")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredDiscoveredFiles) { item ->
                                BackupFileCard(
                                    item = item,
                                    onClick = { onSelectFile(item.uri) }
                                )
                            }
                        }
                    }
                } else {
                    // Folder Explorer Tab
                    // Quick Folder Jump Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isRootSelected = currentFolder == rootDir
                        FilterChip(
                            selected = isRootSelected,
                            onClick = { currentFolder = rootDir },
                            label = { Text("Device Storage", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )

                        shortcutDirs.forEach { dir ->
                            val isSelected = currentFolder == dir
                            FilterChip(
                                selected = isSelected,
                                onClick = { currentFolder = dir },
                                label = { Text(dir.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Current Path Breadcrumb
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentFolder.parentFile != null && currentFolder != rootDir) {
                            IconButton(
                                onClick = { currentFolder.parentFile?.let { currentFolder = it } },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Up", modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (currentFolder == rootDir) "Device Storage" else currentFolder.absolutePath.replace(rootDir.absolutePath, "Storage"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (folderContents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No accessible files or subfolders here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Scoped storage may restrict direct filesystem browsing. Use the System File Picker to choose any file directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onBrowseSystemPicker,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open System File Picker")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(folderContents) { node ->
                                if (node.isDirectory) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = ""
                                                currentFolder = node.file
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = node.name,
                                                    fontWeight = FontWeight.Medium,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (node.containsBackupFilesCount > 0) {
                                                    Text(
                                                        text = "Contains ${node.containsBackupFilesCount} JSON backup file${if (node.containsBackupFilesCount > 1) "s" else ""}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                } else if (node.backupItem != null) {
                                    BackupFileCard(
                                        item = node.backupItem,
                                        onClick = { onSelectFile(node.backupItem.uri) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupFileCard(
    item: BackupFileItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isJson) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isJson) Icons.Default.Description else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isJson) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        fontWeight = if (item.isJson) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isJson) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "JSON BACKUP",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.formattedSize} • ${item.formattedDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isJson) {
                Icon(
                    imageVector = Icons.Default.CheckCircleOutline,
                    contentDescription = "Selectable Backup",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
