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

            val formattedText = formatTextLeftToRightLineByLine(visionText)
            val rawText = formattedText.ifBlank { visionText.text }
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

    /**
     * Reconstructs OCR text line-by-line, left-to-right, top-to-bottom
     * using element bounding box coordinates. This prevents ML Kit's block-based
     * layout from scrambling side-by-side columns or labels and values.
     */
    private fun formatTextLeftToRightLineByLine(visionText: com.google.mlkit.vision.text.Text): String {
        data class TextChunk(
            val text: String,
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int
        ) {
            val height: Int = (bottom - top).coerceAtLeast(1)
        }

        val chunks = mutableListOf<TextChunk>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox
                    if (box != null && element.text.isNotBlank()) {
                        chunks.add(
                            TextChunk(
                                text = element.text,
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom
                            )
                        )
                    }
                }
            }
        }

        if (chunks.isEmpty()) {
            return visionText.text
        }

        // Sort chunks strictly top-to-bottom by top Y coordinate
        val sortedChunks = chunks.sortedBy { it.top }

        // Group chunks into visual horizontal lines using vertical bounding box overlap
        val visualLines = mutableListOf<MutableList<TextChunk>>()
        for (chunk in sortedChunks) {
            var placed = false
            for (line in visualLines) {
                val lineTop = line.minOf { it.top }
                val lineBottom = line.maxOf { it.bottom }
                val lineMinHeight = line.minOf { it.height }

                val overlapTop = maxOf(chunk.top, lineTop)
                val overlapBottom = minOf(chunk.bottom, lineBottom)
                val overlapHeight = (overlapBottom - overlapTop).coerceAtLeast(0)

                val minHeight = minOf(chunk.height, lineMinHeight)
                if (overlapHeight >= minHeight * 0.35) {
                    line.add(chunk)
                    placed = true
                    break
                }
            }
            if (!placed) {
                visualLines.add(mutableListOf(chunk))
            }
        }

        // Sort lines top-to-bottom by average top coordinate
        visualLines.sortBy { line -> line.map { it.top }.average() }

        // For each line, sort chunks left-to-right by left coordinate and join with spaces
        val resultLines = visualLines.map { line ->
            line.sortBy { it.left }
            line.joinToString(" ") { it.text }
        }

        return resultLines.joinToString("\n")
    }
}
