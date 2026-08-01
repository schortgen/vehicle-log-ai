package com.schortgen.vehiclelogai.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images
import com.schortgen.vehiclelogai.data.models.PhotoCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.repository.PhotoScannerRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the Android MediaStore for new image files, filters out screenshots,
 * very small images, and unsupported types, then creates ReviewItems for
 * newly discovered photos.
 *
 * Designed so a real OCR engine can be plugged into the ReviewItemRepository
 * or a dedicated OcrService later without changing this scanner.
 */
class PhotoScannerService(
    private val context: Context,
    private val photoScannerRepository: PhotoScannerRepository,
    private val reviewItemRepository: ReviewItemRepository
) {

    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif"
    )

    private val minDimension = 400

    private val screenshotKeywords = setOf(
        "screenshot", "screen shot", "screen_shot",
        "capture", "screen capture", "screen_recording",
        "instagram", "facebook", "twitter", "whatsapp",
        "telegram", "snapchat", "tiktok"
    )

    /**
     * Scan the MediaStore for new photos and create ReviewItems for them.
     *
     * @return the number of new photos imported
     */
    suspend fun scanAndImport(): Int = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        DiagnosticLogger.i("Scanner", "scanAndImport begin")
        try {
            val candidates = queryMediaStore()
            var importedCount = 0
            var skippedAlreadyImported = 0
            var skippedScreenshots = 0
            var skippedSmall = 0
            var skippedType = 0
            var skippedVehicleRelated = 0

            for (candidate in candidates) {
                if (photoScannerRepository.isAlreadyImported(candidate.mediaStoreId)) {
                    skippedAlreadyImported++
                    continue
                }

                if (looksLikeScreenshot(candidate.displayName)) {
                    skippedScreenshots++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip screenshot mediaStoreId=${candidate.mediaStoreId} name=${candidate.displayName}"
                    )
                    continue
                }

                if (candidate.width < minDimension || candidate.height < minDimension) {
                    skippedSmall++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip small mediaStoreId=${candidate.mediaStoreId} ${candidate.width}x${candidate.height}"
                    )
                    continue
                }

                if (candidate.mimeType !in supportedMimeTypes) {
                    skippedType++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip mime mediaStoreId=${candidate.mediaStoreId} mime=${candidate.mimeType}"
                    )
                    continue
                }

                // Filter out images that are unlikely to be vehicle-related based on filename heuristics
                if (!isVehicleRelated(candidate)) {
                    skippedVehicleRelated++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip non-vehicle mediaStoreId=${candidate.mediaStoreId} name=${candidate.displayName}"
                    )
                    continue
                }

                val reviewItem = ReviewItem(
                    photoPath = candidate.uri,
                    captureDate = candidate.dateTaken,
                    status = ProcessingStatus.PENDING,
                    reason = "Imported: ${candidate.displayName}"
                )
                reviewItemRepository.insertReviewItem(reviewItem)

                photoScannerRepository.markAsImported(
                    ScannedPhoto(
                        mediaStoreId = candidate.mediaStoreId,
                        uri = candidate.uri,
                        displayName = candidate.displayName
                    )
                )
                importedCount++
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            DiagnosticLogger.recordScan(candidates.size, importedCount)
            // Detailed scan statistics
            DiagnosticLogger.i(
                "Scanner",
                "Scanned: ${candidates.size} photos\n"
                    + "Skipped (already imported): $skippedAlreadyImported\n"
                    + "Skipped (too small): $skippedSmall\n"
                    + "Skipped (screenshots): $skippedScreenshots\n"
                    + "Skipped (not vehicle related): $skippedVehicleRelated\n"
                    + "Imported: $importedCount\n"
                    + "Elapsed: ${elapsedMs}ms"
            )
            importedCount
        } catch (t: Throwable) {
            DiagnosticLogger.e("Scanner", "scanAndImport failed", t)
            throw t
        }
    }

    private fun looksLikeScreenshot(displayName: String): Boolean {
        val lower = displayName.lowercase()
        return screenshotKeywords.any { lower.contains(it) }
    }

    // Heuristic to determine if a photo is likely vehicle‑related based on filename keywords
    private val vehicleKeywords = setOf(
        "gallons",
        "fuel",
        "total",
        "receipt",
        "invoice",
        "oil",
        "tire",
        "maintenance",
        "brake",
        "inspection",
        "registration",
        "vin",
        "odometer"
    )

    private fun isVehicleRelated(candidate: PhotoCandidate): Boolean {
        val lower = candidate.displayName.lowercase()
        return vehicleKeywords.any { lower.contains(it) }
    }

    private suspend fun queryMediaStore(): List<PhotoCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<PhotoCandidate>()
        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATE_TAKEN,
            Images.Media.WIDTH,
            Images.Media.HEIGHT,
            Images.Media.SIZE,
            Images.Media.MIME_TYPE
        )

        val sortOrder = "${Images.Media.DATE_TAKEN} DESC"

        val cursor = context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)
            val widthCol = c.getColumnIndexOrThrow(Images.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(Images.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(Images.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val displayName = c.getString(nameCol) ?: "image_$id"
                val dateTaken = c.getLong(dateCol)
                val width = c.getInt(widthCol)
                val height = c.getInt(heightCol)
                val size = c.getLong(sizeCol)
                val mime = c.getString(mimeCol) ?: ""
                val uri = ContentUris.withAppendedId(collectionUri, id).toString()

                candidates.add(
                    PhotoCandidate(
                        mediaStoreId = id,
                        uri = uri,
                        displayName = displayName,
                        dateTaken = if (dateTaken > 0) dateTaken else System.currentTimeMillis(),
                        width = width,
                        height = height,
                        fileSize = size,
                        mimeType = mime
                    )
                )
            }
        }

        DiagnosticLogger.d("Scanner", "queryMediaStore returned ${candidates.size} candidate(s)")
        candidates
    }
}
