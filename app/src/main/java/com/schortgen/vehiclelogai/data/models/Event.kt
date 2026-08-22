package com.schortgen.vehiclelogai.data.models

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.schortgen.vehiclelogai.data.models.Vehicle
import java.io.File

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["eventDate"]),
        Index(value = ["eventType"])
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val eventType: EventType,
    val eventDate: Long,
    val createdDate: Long = System.currentTimeMillis(),
    val confidence: Float? = null,
    val verified: Boolean = false,
    val notes: String? = null,
    // Fuel-specific fields
    val odometer: Int? = null,
    val tripDistance: Double? = null,
    val gallons: Double? = null,
    val pricePerGallon: Double? = null,
    val totalCost: Double? = null,
    val location: String? = null,
    val photoPath: String? = null
)

fun Event.calculateMpg(allEvents: List<Event> = emptyList()): Double? {
    if (eventType != EventType.FUEL) return null
    val gal = gallons ?: return null
    if (gal <= 0) return null

    if (tripDistance != null && tripDistance > 0) {
        return tripDistance / gal
    }

    if (odometer != null && vehicleId != null && allEvents.isNotEmpty()) {
        val prevFuelEvent = allEvents
            .filter { it.vehicleId == vehicleId && it.eventType == EventType.FUEL && it.odometer != null }
            .filter { it.eventDate < eventDate || (it.eventDate == eventDate && it.id < id) }
            .maxByOrNull { it.eventDate }
        if (prevFuelEvent?.odometer != null && odometer > prevFuelEvent.odometer) {
            val trip = (odometer - prevFuelEvent.odometer).toDouble()
            return trip / gal
        }
    }
    return null
}

fun Event.getPhotoPaths(): List<String> {
    if (photoPath.isNullOrBlank()) return emptyList()
    val clean = photoPath.trim().removePrefix("[").removeSuffix("]").replace("\"", "").replace("'", "")
    return clean.split(',', '|', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * Aggregates and deduplicates all photo paths associated with an Event,
 * prioritizing the Event's primary photo paths, followed by ReviewItems and ScannedPhotos.
 */
fun Event.resolveAllDisplayPhotoPaths(
    reviewItems: List<ReviewItem> = emptyList(),
    scannedPhotos: List<ScannedPhoto> = emptyList(),
    context: Context? = null
): List<String> {
    val result = mutableListOf<String>()
    val seenFileNames = mutableSetOf<String>()

    fun addPathIfUnique(rawPath: String?, defaultDisplayName: String? = null) {
        if (rawPath.isNullOrBlank()) return
        val clean = rawPath.trim().removePrefix("[").removeSuffix("]").replace("\"", "").replace("'", "")
        clean.split(',', '|', '\n', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach { singlePath ->
            val fileName = defaultDisplayName?.takeIf { it.isNotBlank() } ?: singlePath.extractPhotoFileName(context)
            val key = fileName.lowercase().takeIf { it.isNotBlank() && it.contains(".") } ?: singlePath.lowercase()
            if (!seenFileNames.contains(key)) {
                seenFileNames.add(key)
                result.add(singlePath)
            }
        }
    }

    // 1. Primary: Event's own photoPaths (these are the canonical paths stored with the event)
    getPhotoPaths().forEach { addPathIfUnique(it) }

    // 2. Review items attached to this event
    reviewItems.forEach { addPathIfUnique(it.photoPath) }

    // 3. Scanned photos attached to this event
    scannedPhotos.forEach { addPathIfUnique(it.uri, it.displayName) }

    return result
}

fun String.getPhotoStatusInfo(context: Context? = null): Triple<String, String, Boolean> {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return Triple("", "", false)
    val fileName = extractPhotoFileName(context)
    
    // Check if it's a web URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return Triple(fileName, trimmed, true)
    }

    // Direct File check
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead() && directFile.length() > 0) {
        return Triple(fileName, directFile.absolutePath, true)
    }

    // Fallback path resolution via toImageModel
    val imageModel = try { toImageModel(context) } catch (_: Exception) { cleanPath }
    var resolvedLocation = cleanPath
    var isResolved = false

    when (imageModel) {
        is File -> {
            resolvedLocation = imageModel.absolutePath
            isResolved = imageModel.exists() && imageModel.canRead() && imageModel.length() > 0
        }
        is Uri -> {
            resolvedLocation = imageModel.toString()
            isResolved = if (context != null && imageModel.scheme == "content") {
                try {
                    context.contentResolver.openInputStream(imageModel)?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
            } else true
        }
        is String -> {
            resolvedLocation = imageModel
            if (imageModel.startsWith("http://") || imageModel.startsWith("https://")) {
                isResolved = true
            } else {
                val f = File(imageModel)
                isResolved = f.exists() && f.canRead() && f.length() > 0
            }
        }
    }

    return Triple(fileName, resolvedLocation, isResolved)
}

fun String.extractPhotoFileName(context: Context? = null): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    val rawClean = trimmed.removePrefix("file://")
    var fileName = try {
        val decoded = Uri.decode(rawClean)
        decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    } catch (_: Exception) {
        rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    }

    if (trimmed.startsWith("content://") && context != null) {
        // 1. Try querying the URI directly
        try {
            val uri = Uri.parse(trimmed)
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. If it's a numeric MediaStore ID (e.g. content://media/external/images/media/12345), query MediaStore by ID
        val numericId = if (fileName.all { it.isDigit() }) fileName.toLongOrNull() else {
            val last = try { Uri.parse(trimmed).lastPathSegment } catch (_: Exception) { null }
            if (last?.all { it.isDigit() } == true) last.toLongOrNull() else null
        }
        if (numericId != null) {
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA),
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(numericId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    return if (fileName.isNotBlank()) fileName else trimmed
}

private val resolvedImageModelCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

fun clearImageModelCache() {
    resolvedImageModelCache.clear()
}

fun String.toImageModel(context: Context? = null): Any {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""

    resolvedImageModelCache[trimmed]?.let { return it }

    // 1. Web URLs
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        resolvedImageModelCache[trimmed] = trimmed
        return trimmed
    }

    // 2. Direct File / file:// URI check
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead() && directFile.length() > 0) {
        resolvedImageModelCache[trimmed] = directFile
        return directFile
    }

    // 3. Direct Content URI (verify accessibility first)
    if (trimmed.startsWith("content://")) {
        val uri = try { Uri.parse(trimmed) } catch (_: Exception) { null }
        if (uri != null) {
            var isAccessible = false
            if (context != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { isAccessible = true }
                } catch (_: Exception) {
                    isAccessible = false
                }
            }
            if (isAccessible) {
                resolvedImageModelCache[trimmed] = uri
                return uri
            }
        }
    }

    // 4. Extract candidate filenames and search all storage locations
    val fileNames = mutableListOf<String>()
    val primaryFileName = extractPhotoFileName(context)
    if (primaryFileName.isNotBlank()) fileNames.add(primaryFileName)

    val rawClean = trimmed.removePrefix("file://")
    try {
        val decoded = Uri.decode(rawClean)
        val name1 = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
        if (name1.isNotEmpty() && !fileNames.contains(name1)) fileNames.add(name1)
    } catch (_: Exception) {}
    val name2 = rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    if (name2.isNotEmpty() && !fileNames.contains(name2)) fileNames.add(name2)

    if (fileNames.isNotEmpty() && context != null) {
        val app = context.applicationContext as? com.schortgen.vehiclelogai.VehicleLogAIApplication
        val userFolderName = app?.settingsRepository?.getCompletedPhotosFolderName()
        val userFolderUriStr = app?.settingsRepository?.getCompletedPhotosFolderUri()

        val basePicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val baseDcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val baseDocsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val baseDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val externalStorageDir = Environment.getExternalStorageDirectory()

        val candidateDirs = mutableListOf<File>()

        // Add user-selected folder by name/path if available
        if (!userFolderName.isNullOrBlank()) {
            val cleanFolderName = userFolderName.trim()
            if (cleanFolderName.startsWith("/")) {
                candidateDirs.add(File(cleanFolderName))
            } else {
                candidateDirs.add(File(externalStorageDir, cleanFolderName))
                candidateDirs.add(File(basePicturesDir, cleanFolderName))
                candidateDirs.add(File(baseDcimDir, cleanFolderName))
                candidateDirs.add(File(baseDocsDir, cleanFolderName))
                candidateDirs.add(File(baseDownloadsDir, cleanFolderName))
                context.getExternalFilesDir(null)?.let { candidateDirs.add(File(it, cleanFolderName)) }
            }
        }

        // Add standard directories
        listOfNotNull(
            File(basePicturesDir, "ProcessedVehiclePhotos"),
            File(baseDcimDir, "ProcessedVehiclePhotos"),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir("ProcessedVehiclePhotos"),
            File(context.filesDir, "photos"),
            File(baseDcimDir, "Camera"),
            File(basePicturesDir, "Camera"),
            File(basePicturesDir, "Screenshots"),
            basePicturesDir,
            baseDcimDir,
            baseDocsDir,
            baseDownloadsDir
        ).forEach { if (!candidateDirs.contains(it)) candidateDirs.add(it) }

        // Check local filesystem candidate directories
        for (fileName in fileNames) {
            for (dir in candidateDirs) {
                if (dir.exists()) {
                    val candidateFile = File(dir, fileName)
                    if (candidateFile.exists() && candidateFile.canRead() && candidateFile.length() > 0) {
                        resolvedImageModelCache[trimmed] = candidateFile
                        return candidateFile
                    }
                }
            }
        }

        // Query MediaStore directly for any of the candidate filenames
        for (fileName in fileNames) {
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA),
                    "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (dataIdx != -1) {
                            val dataPath = cursor.getString(dataIdx)
                            if (!dataPath.isNullOrBlank()) {
                                val f = File(dataPath)
                                if (f.exists() && f.canRead() && f.length() > 0) {
                                    resolvedImageModelCache[trimmed] = f
                                    return f
                                }
                            }
                        }
                        val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                        if (idIdx != -1) {
                            val mediaId = cursor.getLong(idIdx)
                            val mediaUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
                            resolvedImageModelCache[trimmed] = mediaUri
                            return mediaUri
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Check user-configured SAF folder tree URIs and persisted URI permissions
        val treeUrisToCheck = mutableListOf<Uri>()
        if (!userFolderUriStr.isNullOrBlank() && userFolderUriStr.startsWith("content://")) {
            try { treeUrisToCheck.add(Uri.parse(userFolderUriStr)) } catch (_: Exception) {}
        }
        try {
            context.contentResolver.persistedUriPermissions.forEach { perm ->
                val u = perm.uri
                if (u.toString().startsWith("content://") && !treeUrisToCheck.contains(u)) {
                    treeUrisToCheck.add(u)
                }
            }
        } catch (_: Exception) {}

        for (treeUri in treeUrisToCheck) {
            try {
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.exists()) {
                    for (fileName in fileNames) {
                        val doc = targetDir.findFile(fileName)
                        if (doc != null && doc.exists()) {
                            resolvedImageModelCache[trimmed] = doc.uri
                            return doc.uri
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Fallback parsing
    val fallback: Any = when {
        trimmed.startsWith("content://") -> Uri.parse(trimmed)
        trimmed.startsWith("file://") -> Uri.parse(trimmed)
        trimmed.startsWith("/") -> File(trimmed)
        else -> {
            val file = File(cleanPath)
            if (file.exists() || cleanPath.contains("/")) file else Uri.parse(trimmed)
        }
    }
    resolvedImageModelCache[trimmed] = fallback
    return fallback
}


