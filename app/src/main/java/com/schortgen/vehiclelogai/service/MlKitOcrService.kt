package com.schortgen.vehiclelogai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.schortgen.vehiclelogai.data.models.OcrResult
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * ML Kit Text Recognition v2 service that processes a photo URI and returns
 * recognized text with metadata.
 *
 * This service is independent of event creation. Future receipt parsing and
 * AI assistance can be layered on top by consuming OcrResult without changing
 * this class.
 */
class MlKitOcrService(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process an image from a content URI and return OCR results.
     */
    suspend fun recognizeImage(imageUri: String): OcrResult? = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        DiagnosticLogger.d("OCR", "recognizeImage uri=$imageUri")
        try {
            val uri = Uri.parse(imageUri)
            val startTime = System.currentTimeMillis()

            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap == null) {
                DiagnosticLogger.w("OCR", "decode failed uri=$imageUri")
                DiagnosticLogger.recordOcrFailure()
                return@withContext null
            }

            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val task = recognizer.process(inputImage)
            val visionText = Tasks.await(task, 30, TimeUnit.SECONDS)
            val processingTime = System.currentTimeMillis() - startTime

            val rawText = visionText.text
            if (rawText.isBlank()) {
                DiagnosticLogger.recordOcrSuccess(processingTime)
                DiagnosticLogger.i("OCR", "recognizeImage empty uri=$imageUri timeMs=$processingTime")
                return@withContext OcrResult(
                    rawText = "",
                    processingTimeMs = processingTime,
                    confidence = null,
                    blockCount = 0,
                    lineCount = 0
                )
            }

            val blockCount = visionText.textBlocks.size
            val lineCount = visionText.textBlocks.sumOf { block -> block.lines.size }

            DiagnosticLogger.recordOcrSuccess(processingTime)
            val totalMs = (System.nanoTime() - startedAt) / 1_000_000
            DiagnosticLogger.i(
                "OCR",
                "recognizeImage ok uri=$imageUri timeMs=$processingTime totalMs=$totalMs blocks=$blockCount lines=$lineCount chars=${rawText.length}"
            )

            OcrResult(
                rawText = rawText,
                processingTimeMs = processingTime,
                confidence = null,
                blockCount = blockCount,
                lineCount = lineCount
            )
        } catch (e: Exception) {
            DiagnosticLogger.recordOcrFailure()
            DiagnosticLogger.e("OCR", "recognizeImage failed uri=$imageUri", e)
            null
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val targetSize = 1920
            options.inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight, targetSize
            )

            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            DiagnosticLogger.e("OCR", "decodeBitmapFromUri failed uri=$uri", e)
            null
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int, targetSize: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > targetSize || rawWidth > targetSize) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= targetSize
                && halfWidth / inSampleSize >= targetSize
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun close() {
        runCatching { recognizer.close() }
            .onFailure { DiagnosticLogger.e("OCR", "close failed", it) }
    }
}
