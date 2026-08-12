package com.schortgen.vehiclelogai.debug

import android.content.Context
import android.os.Build
import android.util.Log
import com.schortgen.vehiclelogai.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight, debug-only diagnostic logger.
 *
 * Goals:
 *  - Cheap to call from anywhere in the app.
 *  - Thread-safe.
 *  - Disabled in release builds (no PII written to logcat or disk).
 *  - Retains a small in-memory ring buffer that the Debug screen can dump.
 *  - Optionally appends to a rolling log file under the app's external files dir so
 *    beta testers can "Export diagnostic logs" without needing adb.
 *
 * Initialise once from [com.schortgen.vehiclelogai.VehicleLogAIApplication.onCreate].
 */
object DiagnosticLogger {

    private const val TAG = "VLADiag"
    private const val MAX_IN_MEMORY_LINES = 500
    private const val MAX_LOG_FILES = 5

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private val buffer = ConcurrentLinkedDeque<String>()
    private val droppedCount = AtomicLong(0)
    private val ocrSuccessCount = AtomicLong(0)
    private val ocrFailureCount = AtomicLong(0)
    private val ocrTotalMillis = AtomicLong(0)
    private val parserSuccessCount = AtomicLong(0)
    private val parserFailureCount = AtomicLong(0)
    private val scanCandidateCount = AtomicLong(0)
    private val scanImportedCount = AtomicLong(0)
    private val scanRuns = AtomicLong(0)
    private val dbSeedCount = AtomicLong(0)
    private val dbClearCount = AtomicLong(0)

    @Volatile private var initialized: Boolean = false
    @Volatile private var logDir: File? = null
    @Volatile private var appStartMillis: Long = System.currentTimeMillis()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val dir = File(context.getExternalFilesDir(null), "diagnostics")
            if (!dir.exists()) dir.mkdirs()
            logDir = dir
            pruneOldFiles(dir)
            initialized = true
            i(
                "DiagnosticLogger",
                buildString {
                    append("init device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(" android=").append(Build.VERSION.RELEASE)
                    append(" sdk=").append(Build.VERSION.SDK_INT)
                    append(" app=").append(BuildConfig.VERSION_NAME)
                    append(" (").append(BuildConfig.VERSION_CODE).append(')')
                }
            )
        }
    }

    fun installUncaughtExceptionHandler(context: Context) {
        init(context)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = generateCrashReport(thread, throwable)
                writeCrashReport(report)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to write crash report", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun getSystemMetricsString(contextLabel: String = ""): String {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val usedMem = totalMem - freeMem

        val usedMb = usedMem / (1024 * 1024)
        val maxMb = maxMem / (1024 * 1024)
        val pct = if (maxMem > 0) (usedMem * 100) / maxMem else 0

        val nativeAllocatedMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val nativeSizeMb = android.os.Debug.getNativeHeapSize() / (1024 * 1024)
        val processors = runtime.availableProcessors()
        val activeThreads = Thread.activeCount()

        return buildString {
            if (contextLabel.isNotBlank()) append('[').append(contextLabel).append("] ")
            append("Heap: ").append(usedMb).append("MB / ").append(maxMb).append("MB (").append(pct).append("%), ")
            append("Native: ").append(nativeAllocatedMb).append("MB / ").append(nativeSizeMb).append("MB, ")
            append("Threads: ").append(activeThreads).append(", Cores: ").append(processors)
        }
    }

    fun logSystemMetrics(tag: String, contextLabel: String = "") {
        val metricsStr = getSystemMetricsString(contextLabel)
        i(tag, "System Metrics: $metricsStr")
        val runtime = Runtime.getRuntime()
        val pct = if (runtime.maxMemory() > 0) ((runtime.totalMemory() - runtime.freeMemory()) * 100) / runtime.maxMemory() else 0
        if (pct >= 85) {
            w(tag, "HIGH MEMORY WARNING: Heap usage at $pct%!")
        }
    }

    private fun generateCrashReport(thread: Thread, throwable: Throwable): String {
        val uptimeSeconds = (System.currentTimeMillis() - appStartMillis) / 1000
        val metrics = getSystemMetricsString("Crash Time")

        return buildString {
            append("=== VEHICLE LOG AI UNCAUGHT CRASH REPORT ===\n")
            append("Timestamp: ").append(timeFormat.format(Date())).append('\n')
            append("App Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (Android ").append(Build.VERSION.RELEASE).append(", SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("App Uptime: ").append(uptimeSeconds).append("s\n")
            append("Crash Thread: ").append(thread.name).append(" (id: ").append(thread.id).append(")\n")
            append("Resource Usage: ").append(metrics).append('\n')
            append('\n')
            append("--- EXCEPTION DETAILS ---\n")
            append("Exception: ").append(throwable.javaClass.name).append(": ").append(throwable.message ?: "no message").append('\n')
            append(Log.getStackTraceString(throwable))
            append('\n')
        }
    }

    private fun writeCrashReport(report: String) {
        val dir = logDir ?: return
        try {
            val lastCrashFile = File(dir, "last_crash.txt")
            FileWriter(lastCrashFile, false).use { it.write(report) }

            val timestampFile = File(dir, "crash-${fileNameFormat.format(Date())}.txt")
            FileWriter(timestampFile, false).use { it.write(report) }

            e("CrashHandler", report, null)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed writing crash report file", t)
        }
    }

    fun getLatestCrashReport(): String? {
        val dir = logDir ?: return null
        val file = File(dir, "last_crash.txt")
        return if (file.exists()) {
            runCatching { file.readText() }.getOrNull()
        } else null
    }

    fun clearCrashReport() {
        val dir = logDir ?: return
        val file = File(dir, "last_crash.txt")
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }

    fun v(tag: String, message: String) = log(Log.VERBOSE, tag, message, null)
    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Log.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Log.ERROR, tag, message, throwable)

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val line = formatLine(priority, tag, message, throwable)
        when (priority) {
            Log.VERBOSE -> Log.v(TAG, line)
            Log.DEBUG -> Log.d(TAG, line)
            Log.INFO -> Log.i(TAG, line)
            Log.WARN -> Log.w(TAG, line)
            Log.ERROR -> Log.e(TAG, line, throwable)
        }
        buffer.addLast(line)
        while (buffer.size > MAX_IN_MEMORY_LINES) {
            buffer.pollFirst()
            droppedCount.incrementAndGet()
        }
        appendToFile(line)
    }

    private fun formatLine(priority: Int, tag: String, message: String, throwable: Throwable?): String {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        val sb = StringBuilder()
        sb.append(timeFormat.format(Date())).append(' ')
        sb.append(level).append('/').append(tag).append(": ")
        sb.append(message)
        if (throwable != null) {
            sb.append(" | ").append(throwable::class.java.simpleName).append(": ").append(throwable.message)
        }
        return sb.toString()
    }

    private fun appendToFile(line: String) {
        val dir = logDir ?: return
        try {
            val file = File(dir, "diagnostics.log")
            FileWriter(file, true).use { it.append(line).append('\n') }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write diagnostic log file: ${t.message}")
        }
    }

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("diagnostics-") && f.name.endsWith(".log") }
            ?: return
        if (files.size <= MAX_LOG_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_LOG_FILES)
            .forEach { runCatching { it.delete() } }
    }

    // ---- Stats API used by services and ViewModels ----

    fun recordOcrSuccess(processingTimeMs: Long) {
        ocrSuccessCount.incrementAndGet()
        ocrTotalMillis.addAndGet(processingTimeMs.coerceAtLeast(0L))
    }

    fun recordOcrFailure() {
        ocrFailureCount.incrementAndGet()
    }

    fun recordParserSuccess() {
        parserSuccessCount.incrementAndGet()
    }

    fun recordParserFailure() {
        parserFailureCount.incrementAndGet()
    }

    fun recordScan(candidates: Int, imported: Int) {
        scanRuns.incrementAndGet()
        scanCandidateCount.addAndGet(candidates.toLong())
        scanImportedCount.addAndGet(imported.toLong())
    }

    fun recordSeed(count: Int) {
        dbSeedCount.addAndGet(count.toLong())
    }

    fun recordClear(count: Int) {
        dbClearCount.addAndGet(count.toLong())
    }

    fun stats(): DiagnosticStats = DiagnosticStats(
        inMemoryLines = buffer.size,
        droppedLines = droppedCount.get(),
        ocrSuccessCount = ocrSuccessCount.get(),
        ocrFailureCount = ocrFailureCount.get(),
        averageOcrMs = if (ocrSuccessCount.get() > 0) ocrTotalMillis.get().toDouble() / ocrSuccessCount.get() else 0.0,
        parserSuccessCount = parserSuccessCount.get(),
        parserFailureCount = parserFailureCount.get(),
        scanRuns = scanRuns.get(),
        scanCandidates = scanCandidateCount.get().toInt(),
        scanImported = scanImportedCount.get().toInt(),
        seededRows = dbSeedCount.get().toInt(),
        clearedRows = dbClearCount.get().toInt()
    )

    fun snapshotLines(): List<String> = buffer.toList()

    /**
     * Export the current in-memory buffer to a fresh file in the diagnostic logs
     * directory and return the resulting [File]. Returns null if the logger is
     * disabled (release builds) or the file could not be written.
     */
    fun exportToFile(): File? {
        if (!BuildConfig.DEBUG) return null
        val dir = logDir ?: return null
        return try {
            val file = File(dir, "diagnostics-${fileNameFormat.format(Date())}.log")
            FileWriter(file).use { writer ->
                writer.append("# Vehicle Log AI diagnostic export\n")
                writer.append("# Generated: ").append(timeFormat.format(Date())).append('\n')
                writer.append("# BuildConfig.DEBUG=").append(BuildConfig.DEBUG.toString()).append('\n')
                writer.append("# VERSION_NAME=").append(BuildConfig.VERSION_NAME).append('\n')
                writer.append("# VERSION_CODE=").append(BuildConfig.VERSION_CODE.toString()).append('\n')
                writer.append('\n')
                buffer.forEach { writer.append(it).append('\n') }
            }
            file
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to export diagnostic log: ${t.message}")
            null
        }
    }

    /** Best-effort reset of in-memory counters. Used by Clear test data. */
    fun resetCounters() {
        buffer.clear()
        droppedCount.set(0)
        ocrSuccessCount.set(0)
        ocrFailureCount.set(0)
        ocrTotalMillis.set(0)
        parserSuccessCount.set(0)
        parserFailureCount.set(0)
        scanCandidateCount.set(0)
        scanImportedCount.set(0)
        scanRuns.set(0)
        dbSeedCount.set(0)
        dbClearCount.set(0)
    }
}

data class DiagnosticStats(
    val inMemoryLines: Int,
    val droppedLines: Long,
    val ocrSuccessCount: Long,
    val ocrFailureCount: Long,
    val averageOcrMs: Double,
    val parserSuccessCount: Long,
    val parserFailureCount: Long,
    val scanRuns: Long,
    val scanCandidates: Int,
    val scanImported: Int,
    val seededRows: Int,
    val clearedRows: Int
) {
    val parserTotal: Long get() = parserSuccessCount + parserFailureCount
    val parserSuccessRate: Double
        get() = if (parserTotal == 0L) 0.0 else parserSuccessCount.toDouble() / parserTotal.toDouble()
    val ocrTotal: Long get() = ocrSuccessCount + ocrFailureCount
    val ocrSuccessRate: Double
        get() = if (ocrTotal == 0L) 0.0 else ocrSuccessCount.toDouble() / ocrTotal.toDouble()
}
