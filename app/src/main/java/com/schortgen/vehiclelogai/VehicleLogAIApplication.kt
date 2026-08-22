package com.schortgen.vehiclelogai

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.repository.BackupRepository
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.PhotoScannerRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.service.MlKitOcrService
import com.schortgen.vehiclelogai.service.PhotoMoverService
import com.schortgen.vehiclelogai.service.PhotoScannerService
import com.schortgen.vehiclelogai.service.ReceiptParserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VehicleLogAIApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

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
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var photoMoverService: PhotoMoverService
        private set
    lateinit var backupRepository: BackupRepository
        private set

    private val appStartNanos: Long = System.nanoTime()

    override fun onCreate() {
        super.onCreate()

        // 0. Initialise the diagnostic logger and uncaught crash handler first so any errors or crashes
        //    get captured and saved with CPU & memory metrics.
        DiagnosticLogger.installUncaughtExceptionHandler(this)
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
            reviewItemRepository = reviewItemRepository,
            eventRepository = eventRepository
        )
        mlKitOcrService = MlKitOcrService(this)
        receiptParserService = ReceiptParserService()
        settingsRepository = SettingsRepository(this)
        photoMoverService = PhotoMoverService(this, settingsRepository)
        backupRepository = BackupRepository(database, settingsRepository)

        // 3. Force SQLite to create the databases folder and .db files by
        //    performing a safe, dummy read operation on a background thread.
        //    Without this, Room delays file creation until the UI makes its first query!
        CoroutineScope(Dispatchers.IO).launch {
            val start = System.nanoTime()
            try {
                database.vehicleDao().getById(-1)
                val recovered = database.eventDao().verifyOrphanedManualEvents()
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                DiagnosticLogger.d("App", "vehicle DAO warm-up ok in ${elapsedMs}ms, recovered $recovered orphaned manual event(s)")
            } catch (t: Throwable) {
                DiagnosticLogger.e("App", "vehicle DAO warm-up failed", t)
            }
        }

        val totalMs = (System.nanoTime() - startedAt) / 1_000_000
        val sinceStartMs = (System.nanoTime() - appStartNanos) / 1_000_000
        DiagnosticLogger.i("App", "onCreate complete in ${totalMs}ms (since process start ${sinceStartMs}ms)")
    }
}
