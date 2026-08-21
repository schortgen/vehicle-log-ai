package com.schortgen.vehiclelogai.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.models.clearImageModelCache
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

data class RelinkReport(
    val totalChecked: Int = 0,
    val relinkedCount: Int = 0,
    val alreadyValidCount: Int = 0,
    val unresolvedCount: Int = 0,
    val details: List<String> = emptyList(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

object PhotoPathRelinker {

    private val gson = Gson()

    /**
     * Checks all events, review items, and scanned photos in the database.
     * For any broken/missing photo references, searches device storage, user folders,
     * and MediaStore, and updates the database with live valid paths/URIs.
     */
    suspend fun relinkAllPhotos(
        context: Context,
        database: VehicleLogDatabase,
        settingsRepository: SettingsRepository? = null,
        customFolderTreeUri: Uri? = null,
        customFolderPath: String? = null
    ): RelinkReport = withContext(Dispatchers.IO) {
        val details = mutableListOf<String>()
        var relinked = 0
        var alreadyValid = 0
        var unresolved = 0
        var total = 0

        try {
            DiagnosticLogger.i("PhotoPathRelinker", "Starting photo path relink scan...")

            // 1. Build an index of available photo files from all accessible storage locations
            val photoIndex = buildStoragePhotoIndex(
                context = context,
                settingsRepository = settingsRepository,
                customFolderTreeUri = customFolderTreeUri,
                customFolderPath = customFolderPath
            )
            DiagnosticLogger.i("PhotoPathRelinker", "Indexed ${photoIndex.size} photo files across device storage.")

            // 2. Scan and relink Events
            val events = database.eventDao().getAllEvents()
            val eventsToUpdate = mutableListOf<Event>()

            for (event in events) {
                val rawPath = event.photoPath
                if (rawPath.isNullOrBlank()) continue
                total++

                val (newPath, wasFixed, isValid) = resolveAndRepairPath(context, rawPath, photoIndex)
                if (wasFixed) {
                    relinked++
                    eventsToUpdate.add(event.copy(photoPath = newPath))
                    details.add("Event #${event.id} (${event.eventType}): Relinked photo -> $newPath")
                } else if (isValid) {
                    alreadyValid++
                } else {
                    unresolved++
                    details.add("Event #${event.id} (${event.eventType}): Photo not found in scanned directories ($rawPath)")
                }
            }

            if (eventsToUpdate.isNotEmpty()) {
                database.eventDao().updateAll(eventsToUpdate)
                DiagnosticLogger.i("PhotoPathRelinker", "Updated ${eventsToUpdate.size} events in database.")
            }

            // 3. Scan and relink Review Items
            val reviewItems = database.reviewItemDao().getAll()
            val reviewItemsToUpdate = mutableListOf<ReviewItem>()

            for (item in reviewItems) {
                val rawPath = item.photoPath
                if (rawPath.isNullOrBlank()) continue
                total++

                val (newPath, wasFixed, isValid) = resolveAndRepairPath(context, rawPath, photoIndex)
                if (wasFixed) {
                    relinked++
                    reviewItemsToUpdate.add(item.copy(photoPath = newPath))
                    details.add("ReviewItem #${item.id}: Relinked photo -> $newPath")
                } else if (isValid) {
                    alreadyValid++
                } else {
                    unresolved++
                }
            }

            if (reviewItemsToUpdate.isNotEmpty()) {
                database.reviewItemDao().updateAll(reviewItemsToUpdate)
                DiagnosticLogger.i("PhotoPathRelinker", "Updated ${reviewItemsToUpdate.size} review items in database.")
            }

            // 4. Scan and relink Scanned Photos
            val scannedPhotos = database.scannedPhotoDao().getAllScannedPhotos()
            val scannedPhotosToUpdate = mutableListOf<ScannedPhoto>()

            for (sp in scannedPhotos) {
                val rawPath = sp.uri
                if (rawPath.isBlank()) continue
                total++

                val (newPath, wasFixed, isValid) = resolveAndRepairPath(context, rawPath, photoIndex)
                if (wasFixed) {
                    relinked++
                    scannedPhotosToUpdate.add(sp.copy(uri = newPath))
                } else if (isValid) {
                    alreadyValid++
                } else {
                    unresolved++
                }
            }

            if (scannedPhotosToUpdate.isNotEmpty()) {
                database.scannedPhotoDao().updateAll(scannedPhotosToUpdate)
                DiagnosticLogger.i("PhotoPathRelinker", "Updated ${scannedPhotosToUpdate.size} scanned photos in database.")
            }

            // Clear in-memory UI image cache so new paths display immediately
            clearImageModelCache()

            DiagnosticLogger.i(
                "PhotoPathRelinker",
                "Relink finished. Total: $total, Relinked: $relinked, Valid: $alreadyValid, Unresolved: $unresolved"
            )

            RelinkReport(
                totalChecked = total,
                relinkedCount = relinked,
                alreadyValidCount = alreadyValid,
                unresolvedCount = unresolved,
                details = details,
                isSuccess = true
            )
        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoPathRelinker", "Error relinking photos", e)
            RelinkReport(
                totalChecked = total,
                relinkedCount = relinked,
                alreadyValidCount = alreadyValid,
                unresolvedCount = unresolved,
                details = details,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.message
            )
        }
    }

    /**
     * Checks if a path or JSON array of paths is accessible, and if not, finds matching files from the index.
     * Returns Triple(repairedPathString, wasRepaired, isValid).
     */
    private fun resolveAndRepairPath(
        context: Context,
        rawPath: String,
        photoIndex: Map<String, String>
    ): Triple<String, Boolean, Boolean> {
        val trimmed = rawPath.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // Multi-photo JSON array
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(trimmed, type)
                var anyFixed = false
                var allValid = true
                val fixedList = list.map { singlePath ->
                    val (resPath, fixed, valid) = resolveSinglePath(context, singlePath, photoIndex)
                    if (fixed) anyFixed = true
                    if (!valid) allValid = false
                    resPath
                }
                Triple(gson.toJson(fixedList), anyFixed, allValid)
            } catch (_: Exception) {
                resolveSinglePath(context, trimmed, photoIndex)
            }
        } else {
            return resolveSinglePath(context, trimmed, photoIndex)
        }
    }

    private fun resolveSinglePath(
        context: Context,
        singlePath: String,
        photoIndex: Map<String, String>
    ): Triple<String, Boolean, Boolean> {
        val trimmed = singlePath.trim()
        if (trimmed.isEmpty()) return Triple(singlePath, false, false)

        // Check if current path is already accessible
        if (isPathAccessible(context, trimmed)) {
            return Triple(trimmed, false, true)
        }

        // Extract filename variations
        val candidateNames = extractCandidateFileNames(trimmed)
        for (candidate in candidateNames) {
            val key = candidate.lowercase()
            val match = photoIndex[key]
            if (match != null) {
                return Triple(match, true, true)
            }
        }

        return Triple(singlePath, false, false)
    }

    fun isPathAccessible(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        val clean = path.trim()
        if (clean.startsWith("content://")) {
            return try {
                val uri = Uri.parse(clean)
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
        val file = if (clean.startsWith("file://")) File(clean.removePrefix("file://")) else File(clean)
        return file.exists() && file.canRead() && file.length() > 0
    }

    fun extractCandidateFileNames(path: String): List<String> {
        val results = mutableListOf<String>()
        val clean = path.trim().removePrefix("file://")
        try {
            val decoded = URLDecoder.decode(clean, "UTF-8")
            val name1 = File(decoded).name.trim()
            if (name1.isNotEmpty()) results.add(name1)
        } catch (_: Exception) {}

        val name2 = File(clean).name.trim()
        if (name2.isNotEmpty() && !results.contains(name2)) results.add(name2)

        if (clean.startsWith("content://")) {
            try {
                val uri = Uri.parse(clean)
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank()) {
                    val leaf = if (lastSegment.contains("/")) lastSegment.substringAfterLast("/") else lastSegment
                    if (leaf.contains(":")) {
                        val subLeaf = leaf.substringAfterLast(":")
                        if (subLeaf.isNotEmpty() && !results.contains(subLeaf)) results.add(subLeaf)
                    }
                    if (leaf.isNotEmpty() && !results.contains(leaf)) results.add(leaf)
                }
            } catch (_: Exception) {}
        }
        return results
    }

    /**
     * Builds a comprehensive dictionary of fileName (lowercase) -> absolutePathOrUri
     */
    fun buildStoragePhotoIndex(
        context: Context,
        settingsRepository: SettingsRepository? = null,
        customFolderTreeUri: Uri? = null,
        customFolderPath: String? = null
    ): Map<String, String> {
        val index = mutableMapOf<String, String>()

        fun addFileToIndex(name: String, pathOrUri: String) {
            val key = name.trim().lowercase()
            if (key.isNotEmpty() && !index.containsKey(key)) {
                index[key] = pathOrUri
            }
        }

        // 1. Index Custom Folder SAF Tree URI if provided
        if (customFolderTreeUri != null) {
            indexDocumentTree(context, customFolderTreeUri, ::addFileToIndex)
        }

        // 2. Index Configured Completed Photos SAF Tree
        val configuredUriStr = settingsRepository?.getCompletedPhotosFolderUri()
        if (!configuredUriStr.isNullOrBlank() && configuredUriStr.startsWith("content://")) {
            try {
                indexDocumentTree(context, Uri.parse(configuredUriStr), ::addFileToIndex)
            } catch (_: Exception) {}
        }

        // 3. Index all Persisted SAF Tree permissions
        try {
            context.contentResolver.persistedUriPermissions.forEach { perm ->
                val u = perm.uri
                if (u.toString().startsWith("content://")) {
                    indexDocumentTree(context, u, ::addFileToIndex)
                }
            }
        } catch (_: Exception) {}

        // 4. Index Custom Folder Path if provided
        if (!customFolderPath.isNullOrBlank()) {
            indexLocalDirectory(File(customFolderPath), ::addFileToIndex)
        }

        // 5. Index User Folder Name from settings
        val configuredFolderName = settingsRepository?.getCompletedPhotosFolderName()
        if (!configuredFolderName.isNullOrBlank()) {
            if (configuredFolderName.startsWith("/")) {
                indexLocalDirectory(File(configuredFolderName), ::addFileToIndex)
            } else {
                indexLocalDirectory(File(Environment.getExternalStorageDirectory(), configuredFolderName), ::addFileToIndex)
                indexLocalDirectory(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), configuredFolderName), ::addFileToIndex)
                indexLocalDirectory(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), configuredFolderName), ::addFileToIndex)
                indexLocalDirectory(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), configuredFolderName), ::addFileToIndex)
                indexLocalDirectory(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), configuredFolderName), ::addFileToIndex)
            }
        }

        // 6. Index Standard Device Photo Directories
        val standardDirs = listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ProcessedVehiclePhotos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ProcessedVehiclePhotos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Camera"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir("ProcessedVehiclePhotos"),
            File(context.filesDir, "photos")
        )

        for (dir in standardDirs) {
            indexLocalDirectory(dir, ::addFileToIndex)
        }

        // 7. Universal MediaStore query for all accessible device photos
        runCatching {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    if (!name.isNullOrBlank()) {
                        // Prefer absolute file path if file exists, else use content URI
                        if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                            addFileToIndex(name, dataPath)
                        } else {
                            addFileToIndex(name, uri.toString())
                        }
                    }
                }
            }
        }

        return index
    }

    private fun indexDocumentTree(context: Context, treeUri: Uri, addCallback: (String, String) -> Unit) {
        try {
            val docDir = DocumentFile.fromTreeUri(context, treeUri) ?: return
            if (!docDir.exists()) return
            scanDocumentFileRecursive(docDir, addCallback, 0)
        } catch (_: Exception) {}
    }

    private fun scanDocumentFileRecursive(
        dir: DocumentFile,
        addCallback: (String, String) -> Unit,
        depth: Int
    ) {
        if (depth > 4) return
        try {
            val files = dir.listFiles()
            for (f in files) {
                if (f.isDirectory) {
                    scanDocumentFileRecursive(f, addCallback, depth + 1)
                } else {
                    val name = f.name
                    if (!name.isNullOrBlank()) {
                        addCallback(name, f.uri.toString())
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun indexLocalDirectory(dir: File?, addCallback: (String, String) -> Unit) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        try {
            dir.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.length() > 0 }
                .forEach { file ->
                    addCallback(file.name, file.absolutePath)
                }
        } catch (_: Exception) {}
    }
}
