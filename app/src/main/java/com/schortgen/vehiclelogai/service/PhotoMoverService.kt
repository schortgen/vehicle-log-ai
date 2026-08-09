package com.schortgen.vehiclelogai.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PhotoMoverService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Moves a photo at [photoPath] to the user's configured destination folder
     * if photo moving is enabled in settings.
     * Returns the new path/URI string if moved, or the original [photoPath] if not moved.
     */
    fun movePhotoIfEnabled(photoPath: String?): String? {
        if (photoPath.isNullOrBlank()) return photoPath

        val moveEnabled = settingsRepository.getMovePhotosOnComplete()
        if (!moveEnabled) return photoPath

        val targetFolderUri = settingsRepository.getCompletedPhotosFolderUri()
        return movePhoto(photoPath, targetFolderUri)
    }

    fun movePhoto(photoPath: String, targetFolderUri: String?): String {
        try {
            val fileName = extractFileName(photoPath)

            // 1. If targetFolderUri is a SAF tree URI
            if (!targetFolderUri.isNullOrBlank() && targetFolderUri.startsWith("content://")) {
                val treeUri = Uri.parse(targetFolderUri)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.exists() && targetDir.canWrite()) {
                    val destFile = targetDir.createFile("image/jpeg", fileName)
                    if (destFile != null) {
                        context.contentResolver.openOutputStream(destFile.uri)?.use { outStream ->
                            openInputStream(photoPath)?.use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        deleteOriginal(photoPath)
                        DiagnosticLogger.i("PhotoMover", "Moved photo $photoPath to SAF tree ${destFile.uri}")
                        return destFile.uri.toString()
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
            if (destFile.exists()) {
                val nameWithoutExt = fileName.substringBeforeLast('.')
                val ext = if (fileName.contains('.')) fileName.substringAfterLast('.') else "jpg"
                destFile = File(destDir, "${nameWithoutExt}_${System.currentTimeMillis()}.$ext")
            }

            FileOutputStream(destFile).use { outStream ->
                openInputStream(photoPath)?.use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            deleteOriginal(photoPath)
            DiagnosticLogger.i("PhotoMover", "Moved photo $photoPath to local path ${destFile.absolutePath}")
            return destFile.absolutePath

        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoMover", "Failed to move photo $photoPath", e)
            return photoPath
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

    private fun deleteOriginal(photoPath: String) {
        try {
            if (photoPath.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(photoPath), null, null)
            } else {
                val cleanPath = if (photoPath.startsWith("file://")) photoPath.removePrefix("file://") else photoPath
                File(cleanPath).delete()
            }
        } catch (e: Exception) {
            DiagnosticLogger.w("PhotoMover", "Failed to delete original file at $photoPath: ${e.message}")
        }
    }

    private fun extractFileName(photoPath: String): String {
        return try {
            val decoded = Uri.decode(photoPath)
            val name = decoded.substringAfterLast('/').substringAfterLast('\\').trim()
            if (name.isNotBlank() && name.contains('.')) name else "photo_${System.currentTimeMillis()}.jpg"
        } catch (_: Exception) {
            "photo_${System.currentTimeMillis()}.jpg"
        }
    }
}
