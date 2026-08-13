package com.schortgen.vehiclelogai.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFileItem(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
    val path: String,
    val formattedSize: String,
    val formattedDate: String
)

data class FolderBrowserNode(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val backupItem: BackupFileItem? = null,
    val containsBackupFilesCount: Int = 0,
    val latestBackupDateMs: Long = 0L
)

object BackupFileScanner {

    private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun formatDate(timestampMs: Long): String {
        return if (timestampMs > 0) DATE_FORMAT.format(Date(timestampMs)) else "Unknown Date"
    }

    /**
     * Lists contents of a directory for the in-app backup browser.
     * - Filters out photo and media files completely.
     * - Sorts JSON backup files first at the very top.
     * - Sorts folders that contain JSON backup files immediately below JSON files, at the top of the folder list.
     * - Other folders follow alphabetically.
     */
    fun listFolderContents(context: Context, directory: File): List<FolderBrowserNode> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val files = directory.listFiles() ?: return emptyList()
        val nodes = mutableListOf<FolderBrowserNode>()

        val ignoredExtensions = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "gif", "bmp",
            "mp4", "mkv", "avi", "mov", "mp3", "wav", "apk", "tmp"
        )

        for (file in files) {
            if (file.name.startsWith(".")) continue

            if (file.isDirectory) {
                // Check if this directory contains any JSON backup files
                val childFiles = file.listFiles() ?: emptyArray()
                val jsonChildren = childFiles.filter { child ->
                    !child.isDirectory && !child.name.startsWith(".") && child.name.endsWith(".json", ignoreCase = true)
                }
                val count = jsonChildren.size
                val latestDate = jsonChildren.maxOfOrNull { it.lastModified() } ?: 0L

                nodes.add(
                    FolderBrowserNode(
                        file = file,
                        name = file.name,
                        isDirectory = true,
                        containsBackupFilesCount = count,
                        latestBackupDateMs = latestDate
                    )
                )
            } else {
                val extension = file.extension.lowercase(Locale.ROOT)
                if (extension in ignoredExtensions) continue

                // Only include JSON files in the browser
                if (extension == "json" || file.name.contains("vehicle_log_backup", ignoreCase = true)) {
                    val uri = Uri.fromFile(file)
                    val item = BackupFileItem(
                        name = file.name,
                        uri = uri,
                        sizeBytes = file.length(),
                        dateModifiedMs = file.lastModified(),
                        path = file.absolutePath,
                        formattedSize = formatFileSize(file.length()),
                        formattedDate = formatDate(file.lastModified())
                    )
                    nodes.add(
                        FolderBrowserNode(
                            file = file,
                            name = file.name,
                            isDirectory = false,
                            backupItem = item
                        )
                    )
                }
            }
        }

        // Sorting rule:
        // 1. JSON files at the very top (sorted newest first)
        // 2. Folders containing JSON backup files (sorted newest backup date first)
        // 3. Other subfolders (sorted alphabetically)
        return nodes.sortedWith { a, b ->
            when {
                // Both are JSON files: newest first
                !a.isDirectory && !b.isDirectory -> {
                    val dateA = a.backupItem?.dateModifiedMs ?: 0L
                    val dateB = b.backupItem?.dateModifiedMs ?: 0L
                    dateB.compareTo(dateA)
                }
                // a is JSON file, b is folder: JSON file comes first
                !a.isDirectory && b.isDirectory -> -1
                // a is folder, b is JSON file: JSON file comes first
                a.isDirectory && !b.isDirectory -> 1
                // Both are folders:
                else -> {
                    val aHasBackups = a.containsBackupFilesCount > 0
                    val bHasBackups = b.containsBackupFilesCount > 0
                    when {
                        // Both folders contain backups: sort by latest backup date inside
                        aHasBackups && bHasBackups -> b.latestBackupDateMs.compareTo(a.latestBackupDateMs)
                        // Folder A has backups, Folder B does not: Folder A comes first
                        aHasBackups && !bHasBackups -> -1
                        // Folder B has backups, Folder A does not: Folder B comes first
                        !aHasBackups && bHasBackups -> 1
                        // Neither has backups: sort alphabetically
                        else -> a.name.compareTo(b.name, ignoreCase = true)
                    }
                }
            }
        }
    }

    /**
     * Auto-scans standard storage locations on device for JSON backup files.
     */
    fun scanDeviceForBackups(context: Context): List<BackupFileItem> {
        val foundItems = mutableMapOf<String, BackupFileItem>()

        // 1. Scan Downloads Directory
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            scanDirectoryRecursively(downloadsDir, foundItems, maxDepth = 2)
        }

        // 2. Scan Documents Directory
        runCatching {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            scanDirectoryRecursively(documentsDir, foundItems, maxDepth = 2)
        }

        // 3. Scan Root External Storage
        runCatching {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir != null && externalDir.exists()) {
                val topLevelFolders = externalDir.listFiles() ?: emptyArray()
                for (folder in topLevelFolders) {
                    if (folder.isDirectory && !folder.name.startsWith(".") && folder.name != "Android") {
                        scanDirectoryRecursively(folder, foundItems, maxDepth = 1)
                    } else if (folder.isFile && folder.name.endsWith(".json", ignoreCase = true)) {
                        val item = BackupFileItem(
                            name = folder.name,
                            uri = Uri.fromFile(folder),
                            sizeBytes = folder.length(),
                            dateModifiedMs = folder.lastModified(),
                            path = folder.absolutePath,
                            formattedSize = formatFileSize(folder.length()),
                            formattedDate = formatDate(folder.lastModified())
                        )
                        foundItems[folder.absolutePath] = item
                    }
                }
            }
        }

        // 4. Scan MediaStore Downloads collection (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED
                )
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE '%.json'"
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "backup.json"
                        val size = cursor.getLong(sizeCol)
                        val dateSec = cursor.getLong(dateCol)
                        val dateMs = dateSec * 1000L
                        val contentUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

                        if (!foundItems.containsKey(name)) {
                            foundItems[name] = BackupFileItem(
                                name = name,
                                uri = contentUri,
                                sizeBytes = size,
                                dateModifiedMs = dateMs,
                                path = "Downloads/$name",
                                formattedSize = formatFileSize(size),
                                formattedDate = formatDate(dateMs)
                            )
                        }
                    }
                }
            }
        }

        return foundItems.values.sortedByDescending { it.dateModifiedMs }
    }

    /**
     * Scans a specific DocumentFile tree chosen by user.
     */
    fun scanDocumentTree(context: Context, treeUri: Uri): List<BackupFileItem> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<BackupFileItem>()
        scanDocumentFileRecursively(rootDoc, results, maxDepth = 3)
        return results.sortedByDescending { it.dateModifiedMs }
    }

    private fun scanDirectoryRecursively(
        directory: File?,
        results: MutableMap<String, BackupFileItem>,
        maxDepth: Int
    ) {
        if (directory == null || !directory.exists() || !directory.isDirectory || maxDepth < 0) return
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.name.startsWith(".") || file.name.equals("Android", ignoreCase = true)) continue

            if (file.isDirectory) {
                scanDirectoryRecursively(file, results, maxDepth - 1)
            } else if (file.name.endsWith(".json", ignoreCase = true)) {
                val item = BackupFileItem(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    sizeBytes = file.length(),
                    dateModifiedMs = file.lastModified(),
                    path = file.absolutePath,
                    formattedSize = formatFileSize(file.length()),
                    formattedDate = formatDate(file.lastModified())
                )
                results[file.absolutePath] = item
            }
        }
    }

    private fun scanDocumentFileRecursively(
        docFile: DocumentFile,
        results: MutableList<BackupFileItem>,
        maxDepth: Int
    ) {
        if (maxDepth < 0) return
        val children = docFile.listFiles()

        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            if (child.isDirectory) {
                scanDocumentFileRecursively(child, results, maxDepth - 1)
            } else if (name.endsWith(".json", ignoreCase = true) || child.type == "application/json") {
                val size = child.length()
                val dateMs = child.lastModified()
                results.add(
                    BackupFileItem(
                        name = name,
                        uri = child.uri,
                        sizeBytes = size,
                        dateModifiedMs = dateMs,
                        path = name,
                        formattedSize = formatFileSize(size),
                        formattedDate = formatDate(dateMs)
                    )
                )
            }
        }
    }
}
