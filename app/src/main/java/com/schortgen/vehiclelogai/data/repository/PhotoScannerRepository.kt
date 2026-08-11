package com.schortgen.vehiclelogai.data.repository

import com.schortgen.vehiclelogai.data.local.dao.ScannedPhotoDao
import com.schortgen.vehiclelogai.data.models.ScannedPhoto

/**
 * Repository for tracking which photos have already been scanned/imported.
 * Used by PhotoScannerService to prevent duplicate imports.
 */
class PhotoScannerRepository(private val scannedPhotoDao: ScannedPhotoDao) {

    suspend fun isAlreadyImported(mediaStoreId: Long): Boolean {
        return scannedPhotoDao.exists(mediaStoreId)
    }

    suspend fun markAsImported(scannedPhoto: ScannedPhoto) {
        scannedPhotoDao.insert(scannedPhoto)
    }

    suspend fun getAllImportedIds(): List<Long> {
        return scannedPhotoDao.getAllMediaStoreIds()
    }

    suspend fun getImportedCount(): Int {
        return scannedPhotoDao.count()
    }

    suspend fun clearAll() {
        scannedPhotoDao.deleteAllScannedPhotos()
    }
}