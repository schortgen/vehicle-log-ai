package com.schortgen.vehiclelogai.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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

            if (!originalExists(photoPath)) {
                DiagnosticLogger.w("PhotoMover", "Original photo does not exist or already moved: $photoPath")
                return photoPath
            }

            // 1. If targetFolderUri is a SAF tree URI
            if (!targetFolderUri.isNullOrBlank() && targetFolderUri.startsWith("content://")) {
                val treeUri = Uri.parse(targetFolderUri)
                val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                if (targetDir != null && targetDir.exists() && targetDir.canWrite()) {
                    var destFile = targetDir.findFile(fileName)
                    if (destFile == null) {
                        destFile = targetDir.createFile("image/jpeg", fileName)
                    }
                    if (destFile != null) {
                        if (destFile.uri.toString() == photoPath) {
                            DiagnosticLogger.i("PhotoMover", "Photo $photoPath is already in target directory")
                            return photoPath
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
                            deleteOriginal(photoPath)
                            DiagnosticLogger.i("PhotoMover", "Moved photo $photoPath to SAF tree ${destFile.uri}")
                            return destFile.uri.toString()
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
                return destFile.absolutePath
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
                deleteOriginal(photoPath)
                DiagnosticLogger.i("PhotoMover", "Moved photo $photoPath to local path ${destFile.absolutePath}")
                return destFile.absolutePath
            }

            return photoPath

        } catch (e: Exception) {
            DiagnosticLogger.e("PhotoMover", "Failed to move photo $photoPath", e)
            return photoPath
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

    private fun deleteOriginal(photoPath: String) {
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
