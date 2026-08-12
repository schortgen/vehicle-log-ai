package com.schortgen.vehiclelogai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.schortgen.vehiclelogai.data.local.dao.EventDao
import com.schortgen.vehiclelogai.data.local.dao.ReviewItemDao
import com.schortgen.vehiclelogai.data.local.dao.ScannedPhotoDao
import com.schortgen.vehiclelogai.data.local.dao.VehicleDao
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventTypeConverter
import com.schortgen.vehiclelogai.data.models.ProcessingStatusConverter
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.models.Vehicle

@Database(
    entities = [Vehicle::class, Event::class, ReviewItem::class, ScannedPhoto::class],
    version = 7, // Incremented for photoPath index
    exportSchema = false
)
@TypeConverters(EventTypeConverter::class, ProcessingStatusConverter::class)
abstract class VehicleLogDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun eventDao(): EventDao
    abstract fun reviewItemDao(): ReviewItemDao
    abstract fun scannedPhotoDao(): ScannedPhotoDao
}