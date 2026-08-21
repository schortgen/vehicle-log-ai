package com.schortgen.vehiclelogai.data.models

import android.content.Context
import android.net.Uri
import android.os.Environment
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

fun String.getPhotoStatusInfo(context: Context? = null): Triple<String, String, Boolean> {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return Triple("", "", false)
    val fileName = extractPhotoFileName(context)
    
    // Check if it's a web URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return Triple(fileName, trimmed, true)
    }

    // Direct File check without disk heavy tree walking
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead()) {
        return Triple(fileName, directFile.absolutePath, true)
    }

    // Content URI quick check
    if (trimmed.startsWith("content://")) {
        var canRead = false
        if (context != null) {
            try {
                val uri = Uri.parse(trimmed)
                canRead = context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                canRead = false
            }
        }
        return Triple(fileName, trimmed, canRead)
    }

    // Fallback path resolution
    val imageModel = try { toImageModel(context) } catch (_: Exception) { cleanPath }
    var resolvedLocation = cleanPath
    var isResolved = false

    when (imageModel) {
        is File -> {
            resolvedLocation = imageModel.absolutePath
            isResolved = imageModel.exists() && imageModel.canRead()
        }
        is Uri -> {
            resolvedLocation = imageModel.toString()
            isResolved = true
        }
        is String -> {
            resolvedLocation = imageModel
            if (imageModel.startsWith("http://") || imageModel.startsWith("https://")) {
                isResolved = true
            } else {
                val f = File(imageModel)
                isResolved = f.exists() && f.canRead()
            }
        }
    }

    return Triple(fileName, resolvedLocation, isResolved)
}

fun String.extractPhotoFileName(context: Context? = null): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    val rawClean = trimmed.removePrefix("file://")
    val fileName = try {
        val decoded = Uri.decode(rawClean)
        decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    } catch (_: Exception) {
        rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    }
    if (trimmed.startsWith("content://") && context != null && (fileName.all { it.isDigit() } || fileName.isEmpty())) {
        try {
            val uri = Uri.parse(trimmed)
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return if (fileName.isNotBlank()) fileName else trimmed
}

private val resolvedImageModelCache = java.util.concurrent.ConcurrentHashMap<String, Any>()

fun String.toImageModel(context: Context? = null): Any {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""

    resolvedImageModelCache[trimmed]?.let { return it }

    // 1. Web URLs
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        resolvedImageModelCache[trimmed] = trimmed
        return trimmed
    }

    // 2. Direct Content URI
    if (trimmed.startsWith("content://")) {
        val uri = try { Uri.parse(trimmed) } catch (_: Exception) { null }
        if (uri != null) {
            resolvedImageModelCache[trimmed] = uri
            return uri
        }
    }

    // 3. Direct File / file:// URI
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead()) {
        resolvedImageModelCache[trimmed] = directFile
        return directFile
    }

    // 4. Extract filename and check common vehicle photo directories + configured target folder
    val rawClean = trimmed.removePrefix("file://")
    val fileNames = mutableListOf<String>()
    try {
        val decoded = Uri.decode(rawClean)
        val name1 = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
        if (name1.isNotEmpty()) fileNames.add(name1)
    } catch (_: Exception) {}
    val name2 = rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    if (name2.isNotEmpty() && !fileNames.contains(name2)) fileNames.add(name2)

    if (fileNames.isNotEmpty() && context != null) {
        val basePicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val baseDcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val candidateDirs = listOfNotNull(
            File(basePicturesDir, "ProcessedVehiclePhotos"),
            File(baseDcimDir, "ProcessedVehiclePhotos"),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir("ProcessedVehiclePhotos"),
            File(context.filesDir, "photos"),
            File(baseDcimDir, "Camera"),
            File(basePicturesDir, "Camera"),
            File(basePicturesDir, "Screenshots"),
            basePicturesDir,
            baseDcimDir
        )

        for (fileName in fileNames) {
            for (dir in candidateDirs) {
                if (dir.exists()) {
                    val candidateFile = File(dir, fileName)
                    if (candidateFile.exists() && candidateFile.canRead()) {
                        resolvedImageModelCache[trimmed] = candidateFile
                        return candidateFile
                    }
                }
            }
        }

        // Check if app has configured custom completed folder SAF URI
        try {
            val app = context.applicationContext as? com.schortgen.vehiclelogai.VehicleLogAIApplication
            val configuredUriStr = app?.settingsRepository?.getCompletedPhotosFolderUri()
            if (!configuredUriStr.isNullOrBlank() && configuredUriStr.startsWith("content://")) {
                val treeUri = Uri.parse(configuredUriStr)
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
            }
        } catch (_: Exception) {}
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


