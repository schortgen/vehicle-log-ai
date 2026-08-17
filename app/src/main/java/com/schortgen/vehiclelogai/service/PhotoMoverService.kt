package com.schortgen.vehiclelogai.service

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PhotoMoveResult(
    val newPath: String,
    val pendingDeleteUri: Uri? = null
)

data class BulkFolderMigrationResult(
    val movedCount: Int,
    val totalFound: Int,
    val newFolderName: String,
    val success: Boolean,
    val errorMessage: String? = null
)

class PhotoMoverService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Moves a photo at [photoPath] to the user's configured destination folder
     * if photo moving is enabled in settings.
     * Returns a [PhotoMoveResult] containing the new path and any URI requiring system delete permission.
     */
    fun movePhotoIfEnabled(photoPath: String?): PhotoMoveResult {
        if (photoPath.isNullOrBlank()) return PhotoMoveResult(photoPath ?: "")

        val moveEnabled = settingsRepository.getMovePhotosOnComplete()
        if (!moveEnabled) return PhotoMoveResult(photoPath)

        val targetFolderUri = settingsRepository.getCompletedPhotosFolderUri()
        return movePhoto(photoPath, targetFolderUri)
    }

    fun movePhoto(photoPath: String, targetFolderUri: String?): PhotoMoveResult {
        try {
            val fileName = extractFileName(photoPath)

            if (!originalExists(photoPath)) {
                DiagnosticLogger.w("PhotoMover", "Original photo does not exist or already moved: $photoPath")
                return PhotoMoveResult(photoPath)
            }

            // 1. If targetFolderUri is a SAF tree URI
            if (!targetFolderUri.isNullOrBlank() && targetFolderUri.startsWith("content://")) {
                val treeUri = Uri.parse(targetFolderUri)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.exists() && targetDir.canWrite()) {
                    var destFile = targetDir.findFile(fileName)
                    if (destFile == null) {
                        val mimeType = getMimeType(fileName, photoPath)
                        destFile = targetDir.createFile(mimeType, fileName)
                    }
                    if (destFile != null) {
                        if (destFile.uri.toString() == photoPath) {
                            DiagnosticLogger.i("PhotoMover", "Photo $photoPath is already in target directory")
                            return PhotoMoveResult(photoPath)
                        }

                        val copySuccess = try {
                            context.contentResolver.openOutputStream(destFile.uri)?.use { outStream ->
                                openInputStream(photoPath)?.use { inStream ->
                                    inStream.copyTo(outStream)
                                    true
                                } ?: false
                            } ?: false
                        } catch (e: Exception) {
                            DiagnosticLogger.e("PhotoMover", "Failed writing to destination SAF file ${destFile.uri}", e)
                            false
                        }

                        if (copySuccess) {
                            val deleted = deleteOriginal(photoPath)
                            val pendingDeleteUri = if (!deleted && photoPath.startsWith("content://")) {
                                try { Uri.parse(photoPath) } catch (_: Exception) { null }
                            } else null
                            DiagnosticLogger.i("PhotoMover", "Copied photo $photoPath to SAF tree ${destFile.uri}, deletedOriginal=$deleted")
                            return PhotoMoveResult(destFile.uri.toString(), pendingDeleteUri)
                        }
                    }
                }
            }

            // 2. Fallback / Default public Pictures directory (e.g., Pictures/ProcessedVehiclePhotos)
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val destDir = File(baseDir, "ProcessedVehiclePhotos")
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            var destFile = File(destDir, fileName)
            val cleanPhotoPath = if (photoPath.startsWith("file://")) photoPath.removePrefix("file://") else photoPath

            if (destFile.absolutePath == cleanPhotoPath) {
                DiagnosticLogger.i("PhotoMover", "Photo $photoPath is already at destination ${destFile.absolutePath}")
                return PhotoMoveResult(destFile.absolutePath)
            }

            if (destFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast('.')
                val ext = if (fileName.contains('.')) fileName.substringAfterLast('.') else "jpg"
                destFile = File(destDir, "${nameWithoutExt}_${System.currentTimeMillis()}.$ext")
            }

            val copySuccess = try {
                FileOutputStream(destFile).use { outStream ->
                    openInputStream(photoPath)?.use { inStream ->
                        inStream.copyTo(outStream)
                        true
                    } ?: false
                }
            } catch (e: Exception) {
                DiagnosticLogger.e("PhotoMover", "Failed writing to destination file ${destFile.absolutePath}", e)
                false
            }

            if (copySuccess) {
                val deleted = deleteOriginal(photoPath)
                val pendingDeleteUri = if (!deleted && photoPath.startsWith("content://")) {
                    try { Uri.parse(photoPath) } catch (_: Exception) { null }
                } else null
                DiagnosticLogger.i("PhotoMover", "Copied photo $photoPath to local path ${destFile.absolutePath}, deletedOriginal=$deleted")
                return PhotoMoveResult(destFile.absolutePath, pendingDeleteUri)
            }

            return PhotoMoveResult(photoPath)

        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoMover", "Failed to move photo $photoPath", e)
            return PhotoMoveResult(photoPath)
        }
    }

    /**
     * Creates an [IntentSender] for system confirmation dialog to delete URIs that could not be deleted directly.
     */
    fun createDeleteRequestIntentSender(uris: List<Uri>): IntentSender? {
        val validUris = uris.filter { it.toString().startsWith("content://") }
        if (validUris.isEmpty()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, validUris)
                pendingIntent.intentSender
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (uri in validUris) {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (rse: RecoverableSecurityException) {
                        return rse.userAction.actionIntent.intentSender
                    }
                }
                null
            } else {
                null
            }
        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoMover", "Failed to create delete request intent sender", e)
            null
        }
    }

    private fun originalExists(photoPath: String): Boolean {
        return try {
            if (photoPath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(photoPath))?.use { true } ?: false
            } else {
                val cleanPath = if (photoPath.startsWith("file://")) photoPath.removePrefix("file://") else photoPath
                File(cleanPath).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openInputStream(photoPath: String) = try {
        if (photoPath.startsWith("content://") || photoPath.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(photoPath))
        } else {
            FileInputStream(File(photoPath))
        }
    } catch (e: Exception) {
        DiagnosticLogger.e("PhotoMover", "Could not open input stream for $photoPath", e)
        null
    }

    private fun deleteOriginal(photoPath: String): Boolean {
        var deleted = false
        val uri = try { Uri.parse(photoPath) } catch (_: Exception) { null }

        if (photoPath.startsWith("content://") && uri != null) {
            // 1. Try DocumentFile single URI delete
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists()) {
                    deleted = doc.delete()
                }
            } catch (e: Exception) {
                DiagnosticLogger.d("PhotoMover", "DocumentFile.delete failed for $photoPath: ${e.message}")
            }

            // 2. Try MediaStore DATA column path delete
            if (!deleted) {
                try {
                    val projection = arrayOf(MediaStore.Images.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                            if (dataIdx != -1) {
                                val filePath = cursor.getString(dataIdx)
                                if (!filePath.isNullOrBlank()) {
                                    val f = File(filePath)
                                    if (f.exists()) {
                                        deleted = f.delete()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.d("PhotoMover", "MediaStore DATA path delete failed: ${e.message}")
                }
            }

            // 3. Try ContentResolver.delete
            if (!deleted) {
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    deleted = rows > 0
                } catch (e: Exception) {
                    DiagnosticLogger.w("PhotoMover", "ContentResolver.delete failed for $photoPath: ${e.message}")
                }
            }
        } else {
            val cleanPath = if (photoPath.startsWith("file://")) photoPath.removePrefix("file://") else photoPath
            try {
                val f = File(cleanPath)
                if (f.exists()) {
                    deleted = f.delete()
                }
            } catch (e: Exception) {
                DiagnosticLogger.w("PhotoMover", "File.delete failed for $cleanPath: ${e.message}")
            }
        }

        if (deleted) {
            DiagnosticLogger.i("PhotoMover", "Successfully deleted original photo at $photoPath")
        } else {
            DiagnosticLogger.w("PhotoMover", "Could not delete original photo at $photoPath")
        }
        return deleted
    }

    private fun extractFileName(photoPath: String): String {
        return try {
            if (photoPath.startsWith("content://")) {
                val uri = Uri.parse(photoPath)
                var displayName: String? = null
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                displayName = cursor.getString(nameIndex)
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (!displayName.isNullOrBlank()) {
                    return displayName!!.trim()
                }
            }
            val decoded = Uri.decode(photoPath)
            val name = decoded.substringAfterLast('/').substringAfterLast('\\').trim()
            if (name.isNotBlank()) {
                if (name.contains('.')) name else "photo_${System.currentTimeMillis()}.jpg"
            } else {
                "photo_${System.currentTimeMillis()}.jpg"
            }
        } catch (_: Exception) {
            "photo_${System.currentTimeMillis()}.jpg"
        }
    }

    private fun getMimeType(fileName: String, uriString: String? = null): String {
        val cleanName = fileName.trim()
        val ext = if (cleanName.contains('.')) cleanName.substringAfterLast('.').lowercase() else ""

        // Explicitly map extensions to avoid Android SAF auto-appending unwanted extensions
        return when (ext) {
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            else -> {
                if (!uriString.isNullOrBlank() && uriString.startsWith("content://")) {
                    try {
                        val type = context.contentResolver.getType(Uri.parse(uriString))
                        if (!type.isNullOrBlank() && type != "application/octet-stream") {
                            return type
                        }
                    } catch (_: Exception) {}
                }
                val mapType = if (ext.isNotBlank()) {
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                } else null
                mapType ?: "application/octet-stream"
            }
        }
    }

    /**
     * Moves all photos located in the currently configured folder and referenced in the database
     * to a newly selected destination folder (SAF tree URI), and updates all timeline photo paths in Room DB.
     */
    suspend fun migrateAllPhotosToNewFolder(
        newFolderTreeUri: Uri,
        newFolderName: String,
        database: VehicleLogDatabase
    ): BulkFolderMigrationResult = withContext(Dispatchers.IO) {
        try {
            val targetDir = DocumentFile.fromTreeUri(context, newFolderTreeUri)
            if (targetDir == null || !targetDir.exists() || !targetDir.canWrite()) {
                return@withContext BulkFolderMigrationResult(
                    movedCount = 0,
                    totalFound = 0,
                    newFolderName = newFolderName,
                    success = false,
                    errorMessage = "Cannot write to selected destination folder."
                )
            }

            // 1. Gather current folder path / URI
            val currentFolderUri = settingsRepository.getCompletedPhotosFolderUri()

            // 2. Query all database records with photos
            val allEvents = database.eventDao().getAllEvents()
            val allReviewItems = database.reviewItemDao().getAll()
            val allScannedPhotos = database.scannedPhotoDao().getAllScannedPhotos()

            val dbPhotoPaths = mutableSetOf<String>()
            allEvents.forEach { event ->
                event.photoPath?.let { pathStr ->
                    pathStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { dbPhotoPaths.add(it) }
                }
            }
            allReviewItems.forEach { item ->
                item.photoPath?.let { p -> if (p.isNotBlank()) dbPhotoPaths.add(p) }
            }
            allScannedPhotos.forEach { photo ->
                if (photo.uri.isNotBlank()) dbPhotoPaths.add(photo.uri)
            }

            // 3. Gather all files from current source folder
            val currentFolderFiles = mutableListOf<String>()
            if (!currentFolderUri.isNullOrBlank() && currentFolderUri.startsWith("content://")) {
                try {
                    val sourceDir = DocumentFile.fromTreeUri(context, Uri.parse(currentFolderUri))
                    sourceDir?.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            currentFolderFiles.add(file.uri.toString())
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.w("PhotoMover", "Error listing files from current SAF tree: ${e.message}")
                }
            }

            // Also check default local storage folder
            val basePicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val defaultLocalDir = File(basePicturesDir, "ProcessedVehiclePhotos")
            if (defaultLocalDir.exists() && defaultLocalDir.isDirectory) {
                defaultLocalDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        currentFolderFiles.add(f.absolutePath)
                    }
                }
            }

            val appPicturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (appPicturesDir != null && appPicturesDir.exists()) {
                appPicturesDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        currentFolderFiles.add(f.absolutePath)
                    }
                }
            }

            val allSourcesToProcess = (currentFolderFiles + dbPhotoPaths).distinct()
            var movedCount = 0
            val pathMap = mutableMapOf<String, String>()

            for (sourcePath in allSourcesToProcess) {
                val fileName = extractFileName(sourcePath)
                if (!originalExists(sourcePath)) {
                    DiagnosticLogger.d("PhotoMover", "Source photo not accessible on disk: $sourcePath")
                    continue
                }

                try {
                    val existingInTarget = targetDir.findFile(fileName)
                    if (existingInTarget != null && existingInTarget.uri.toString() == sourcePath) {
                        pathMap[sourcePath] = sourcePath
                        continue
                    }

                    var destFile = existingInTarget
                    if (destFile == null) {
                        val mimeType = getMimeType(fileName, sourcePath)
                        destFile = targetDir.createFile(mimeType, fileName)
                    }

                    if (destFile != null) {
                        val copySuccess = context.contentResolver.openOutputStream(destFile.uri)?.use { outStream ->
                            openInputStream(sourcePath)?.use { inStream ->
                                inStream.copyTo(outStream)
                                true
                            } ?: false
                        } ?: false

                        if (copySuccess) {
                            val newUriString = destFile.uri.toString()
                            pathMap[sourcePath] = newUriString
                            val cleanPath = if (sourcePath.startsWith("file://")) sourcePath.removePrefix("file://") else sourcePath
                            pathMap[cleanPath] = newUriString
                            pathMap["file://$cleanPath"] = newUriString

                            deleteOriginal(sourcePath)
                            movedCount++
                            DiagnosticLogger.i("PhotoMover", "Migrated $sourcePath -> $newUriString")
                        }
                    }
                } catch (e: Exception) {
                    DiagnosticLogger.e("PhotoMover", "Failed migrating $sourcePath", e)
                }
            }

            // 4. Update all Event records in DB
            val updatedEvents = mutableListOf<Event>()
            for (event in allEvents) {
                val rawPath = event.photoPath
                if (!rawPath.isNullOrBlank()) {
                    val parts = rawPath.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val updatedParts = parts.map { part ->
                        pathMap[part]
                            ?: pathMap[if (part.startsWith("file://")) part.removePrefix("file://") else "file://$part"]
                            ?: pathMap.entries.firstOrNull { extractFileName(it.key) == extractFileName(part) }?.value
                            ?: part
                    }
                    val newPhotoPath = updatedParts.joinToString(",")
                    if (newPhotoPath != rawPath) {
                        updatedEvents.add(event.copy(photoPath = newPhotoPath))
                    }
                }
            }
            if (updatedEvents.isNotEmpty()) {
                database.eventDao().updateAll(updatedEvents)
                DiagnosticLogger.i("PhotoMover", "Updated ${updatedEvents.size} Event records with new photo paths")
            }

            // 5. Update all ReviewItem records in DB
            val updatedReviewItems = mutableListOf<ReviewItem>()
            for (item in allReviewItems) {
                val rawPath = item.photoPath
                if (!rawPath.isNullOrBlank()) {
                    val newP = pathMap[rawPath]
                        ?: pathMap[if (rawPath.startsWith("file://")) rawPath.removePrefix("file://") else "file://$rawPath"]
                        ?: pathMap.entries.firstOrNull { extractFileName(it.key) == extractFileName(rawPath) }?.value
                    if (newP != null && newP != rawPath) {
                        updatedReviewItems.add(item.copy(photoPath = newP))
                    }
                }
            }
            if (updatedReviewItems.isNotEmpty()) {
                database.reviewItemDao().updateAll(updatedReviewItems)
                DiagnosticLogger.i("PhotoMover", "Updated ${updatedReviewItems.size} ReviewItem records with new photo paths")
            }

            // 6. Update all ScannedPhoto records in DB
            val updatedScannedPhotos = mutableListOf<ScannedPhoto>()
            for (scanned in allScannedPhotos) {
                val rawUri = scanned.uri
                if (rawUri.isNotBlank()) {
                    val newU = pathMap[rawUri]
                        ?: pathMap.entries.firstOrNull { extractFileName(it.key) == extractFileName(rawUri) }?.value
                    if (newU != null && newU != rawUri) {
                        updatedScannedPhotos.add(scanned.copy(uri = newU))
                    }
                }
            }
            if (updatedScannedPhotos.isNotEmpty()) {
                database.scannedPhotoDao().updateAll(updatedScannedPhotos)
                DiagnosticLogger.i("PhotoMover", "Updated ${updatedScannedPhotos.size} ScannedPhoto records with new photo paths")
            }

            // 7. Update Settings
            settingsRepository.setCompletedPhotosFolder(newFolderTreeUri.toString(), newFolderName)
            settingsRepository.setMovePhotosOnComplete(true)

            BulkFolderMigrationResult(
                movedCount = movedCount,
                totalFound = allSourcesToProcess.size,
                newFolderName = newFolderName,
                success = true
            )
        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoMover", "Bulk migration error", e)
            BulkFolderMigrationResult(
                movedCount = 0,
                totalFound = 0,
                newFolderName = newFolderName,
                success = false,
                errorMessage = e.localizedMessage ?: "Unknown error during photo migration"
            )
        }
    }
}
