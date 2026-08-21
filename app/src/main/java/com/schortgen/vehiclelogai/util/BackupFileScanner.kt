package com.schortgen.vehiclelogai.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
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
    val formattedDate: String,
    val isJson: Boolean = false
)

data class FolderNode(
    val name: String,
    val file: File,
    val isDirectory: Boolean,
    val backupItem: BackupFileItem? = null,
    val containsBackupFilesCount: Int = 0
)

object BackupFileScanner {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun checkIfJson(name: String, mimeType: String? = null): Boolean {
        return name.endsWith(".json", ignoreCase = true) ||
               name.endsWith(".zip", ignoreCase = true) ||
               name.endsWith(".json.jpg", ignoreCase = true) ||
               name.contains(".json.", ignoreCase = true) ||
               name.contains("VehicleLogAI", ignoreCase = true) ||
               mimeType == "application/json" ||
               mimeType == "application/zip" ||
               mimeType == "application/x-zip-compressed"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDate(timestampMs: Long): String {
        if (timestampMs <= 0) return "Unknown date"
        return dateFormat.format(Date(timestampMs))
    }

    /**
     * Auto-scans standard storage locations, MediaStore, Downloads, Documents, DCIM,
     * Pictures, and storage volumes for all files without blocking or filtering out any files.
     */
    fun scanDeviceForBackups(context: Context): List<BackupFileItem> {
        val foundItems = mutableMapOf<String, BackupFileItem>()

        // 1. Universal MediaStore.Files query (scans all indexed external files)
        runCatching {
            val externalUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            context.contentResolver.query(
                externalUri,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateSec = cursor.getLong(dateCol)
                    val dateMs = dateSec * 1000L
                    val contentUri = ContentUris.withAppendedId(externalUri, id)
                    val isJson = checkIfJson(name)
                    val key = contentUri.toString()
                    if (!foundItems.containsKey(key)) {
                        foundItems[key] = BackupFileItem(
                            name = name,
                            uri = contentUri,
                            sizeBytes = size,
                            dateModifiedMs = dateMs,
                            path = name,
                            formattedSize = formatFileSize(size),
                            formattedDate = formatDate(dateMs),
                            isJson = isJson
                        )
                        count++
                    }
                }
            }
        }

        // 2. MediaStore Downloads collection (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED
                )
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "download_$id"
                        val size = cursor.getLong(sizeCol)
                        val dateSec = cursor.getLong(dateCol)
                        val dateMs = dateSec * 1000L
                        val contentUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        val isJson = checkIfJson(name)
                        val key = contentUri.toString()
                        if (!foundItems.containsKey(key)) {
                            foundItems[key] = BackupFileItem(
                                name = name,
                                uri = contentUri,
                                sizeBytes = size,
                                dateModifiedMs = dateMs,
                                path = "Downloads/$name",
                                formattedSize = formatFileSize(size),
                                formattedDate = formatDate(dateMs),
                                isJson = isJson
                            )
                        }
                    }
                }
            }
        }

        // 3. Scan Public Directories (DCIM, DCIM/Camera, Pictures, Downloads, Documents)
        val standardDirs = listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC
        )

        for (dirType in standardDirs) {
            runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(dirType)
                scanDirectoryRecursively(dir, foundItems, maxDepth = 4)
            }
        }

        // 4. Scan Common external storage mount points
        val commonPaths = listOf(
            "/sdcard/DCIM",
            "/sdcard/DCIM/Camera",
            "/sdcard/Download",
            "/sdcard/Downloads",
            "/sdcard/Documents",
            "/sdcard/Pictures",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/DCIM/Camera",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Pictures"
        )
        for (path in commonPaths) {
            runCatching {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryRecursively(dir, foundItems, maxDepth = 3)
                }
            }
        }

        // 5. Scan App-specific directories
        runCatching {
            context.getExternalFilesDir(null)?.let { scanDirectoryRecursively(it, foundItems, maxDepth = 3) }
            context.filesDir?.let { scanDirectoryRecursively(it, foundItems, maxDepth = 3) }
        }

        // 6. Scan Root External Storage
        runCatching {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir != null && externalDir.exists()) {
                val topLevelFolders = externalDir.listFiles() ?: emptyArray()
                for (folder in topLevelFolders) {
                    if (folder.isDirectory && !folder.name.startsWith(".") && !folder.name.equals("Android", ignoreCase = true)) {
                        scanDirectoryRecursively(folder, foundItems, maxDepth = 3)
                    } else if (folder.isFile) {
                        val item = BackupFileItem(
                            name = folder.name,
                            uri = Uri.fromFile(folder),
                            sizeBytes = folder.length(),
                            dateModifiedMs = folder.lastModified(),
                            path = folder.absolutePath,
                            formattedSize = formatFileSize(folder.length()),
                            formattedDate = formatDate(folder.lastModified()),
                            isJson = checkIfJson(folder.name)
                        )
                        foundItems[folder.absolutePath] = item
                    }
                }
            }
        }

        // Sort so JSON files are prioritized at the top, then by newest date
        return foundItems.values.sortedWith(
            compareByDescending<BackupFileItem> { it.isJson }
                .thenByDescending { it.dateModifiedMs }
        )
    }

    /**
     * Scans a specific DocumentFile tree chosen by the user (SAF).
     * Lists all files without blocking or filtering.
     */
    fun scanDocumentTree(context: Context, treeUri: Uri): List<BackupFileItem> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<BackupFileItem>()
        scanDocumentFileRecursively(rootDoc, results, maxDepth = 5)
        return results.sortedWith(
            compareByDescending<BackupFileItem> { it.isJson }
                .thenByDescending { it.dateModifiedMs }
        )
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
            } else {
                val item = BackupFileItem(
                    name = file.name,
                    uri = Uri.fromFile(file),
                    sizeBytes = file.length(),
                    dateModifiedMs = file.lastModified(),
                    path = file.absolutePath,
                    formattedSize = formatFileSize(file.length()),
                    formattedDate = formatDate(file.lastModified()),
                    isJson = checkIfJson(file.name)
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
            } else {
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
                        formattedDate = formatDate(dateMs),
                        isJson = checkIfJson(name, child.type)
                    )
                )
            }
        }
    }
}
