package com.schortgen.vehiclelogai

import android.app.Application
import androidx.room.Room
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.PhotoScannerRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.service.MlKitOcrService
import com.schortgen.vehiclelogai.service.PhotoScannerService
import com.schortgen.vehiclelogai.service.ReceiptParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VehicleLogAIApplication : Application {

    lateinit var database: VehicleLogDatabase
        private set
    lateinit var vehicleRepository: VehicleRepository
        private set
    lateinit var eventRepository: EventRepository
        private set
    lateinit var reviewItemRepository: ReviewItemRepository
        private set
    lateinit var photoScannerRepository: PhotoScannerRepository
        private set
    lateinit var photoScannerService: PhotoScannerService
        private set
    lateinit var mlKitOcrService: MlKitOcrService
        private set
    lateinit var receiptParserService: ReceiptParserService
        private set

    constructor() : super()

    private val appStartNanos: Long = System.nanoTime()

    override fun onCreate() {
        super.onCreate()

        // 0. Initialise the diagnostic logger first so any startup errors below
        //    get captured. No-op in release builds.
        DiagnosticLogger.init(this)
        val startedAt = System.nanoTime()
        DiagnosticLogger.i("App", "onCreate begin")

        // 1. Instantiate the database immediately when the app starts
        database = Room.databaseBuilder(
            this,
            VehicleLogDatabase::class.java,
            "vehicle_log_database"
        ).fallbackToDestructiveMigration()
         .build()

        // 2. Repositories receive the DAO instances
        vehicleRepository = VehicleRepository(database.vehicleDao())
        eventRepository = EventRepository(database.eventDao())
        reviewItemRepository = ReviewItemRepository(database.reviewItemDao())
        photoScannerRepository = PhotoScannerRepository(database.scannedPhotoDao())
        photoScannerService = PhotoScannerService(
            context = this,
            photoScannerRepository = photoScannerRepository,
            reviewItemRepository = reviewItemRepository
        )
        mlKitOcrService = MlKitOcrService(this)
        receiptParserService = ReceiptParserService()

        // 3. Force SQLite to create the databases folder and .db files by
        //    performing a safe, dummy read operation on a background thread.
        //    Without this, Room delays file creation until the UI makes its first query!
        CoroutineScope(Dispatchers.IO).launch {
            val start = System.nanoTime()
            try {
                database.vehicleDao().getById(-1)
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                DiagnosticLogger.d("App", "vehicle DAO warm-up ok in ${elapsedMs}ms")
            } catch (t: Throwable) {
                DiagnosticLogger.e("App", "vehicle DAO warm-up failed", t)
            }
        }

        val totalMs = (System.nanoTime() - startedAt) / 1_000_000
        val sinceStartMs = (System.nanoTime() - appStartNanos) / 1_000_000
        DiagnosticLogger.i("App", "onCreate complete in ${totalMs}ms (since process start ${sinceStartMs}ms)")
    }
}
