package com.schortgen.vehiclelogai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedPhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scannedPhoto: ScannedPhoto)

    @Query("SELECT mediaStoreId FROM scanned_photos")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM scanned_photos WHERE mediaStoreId = :mediaStoreId)")
    suspend fun exists(mediaStoreId: Long): Boolean

    @Query("SELECT COUNT(*) FROM scanned_photos")
    suspend fun count(): Int

    @Query("SELECT * FROM scanned_photos WHERE eventId = :eventId ORDER BY dateTaken ASC")
    suspend fun getByEventId(eventId: Long): List<ScannedPhoto>

    @Query("SELECT * FROM scanned_photos WHERE eventId IS NULL ORDER BY dateTaken DESC")
    suspend fun getUngroupedScannedPhotos(): List<ScannedPhoto>

    @Query("SELECT * FROM scanned_photos WHERE eventId = :eventId ORDER BY dateTaken ASC")
    fun observeByEvent(eventId: Long): Flow<List<ScannedPhoto>>

    @Query("SELECT * FROM scanned_photos ORDER BY dateTaken DESC")
    fun observeAll(): Flow<List<ScannedPhoto>>

    @Query("SELECT * FROM scanned_photos")
    suspend fun getAllScannedPhotos(): List<ScannedPhoto>

    @Query("DELETE FROM scanned_photos")
    suspend fun deleteAllScannedPhotos()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scannedPhotos: List<ScannedPhoto>)
}