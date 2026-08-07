package com.schortgen.vehiclelogai.service

import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic receipt parser that consumes OCR raw text and produces
 * a FuelPurchaseCandidate using regular expressions, tokenization,
 * normalization, and heuristics.
 */
class ReceiptParserService {

    private val stationKeywords = listOf(
        "shell", "exxon", "bp", "chevron", "mobil", "esso", "sunoco", "speedway",
        "circle k", "7-eleven", "valero", "costco", "sam's club", "walmart",
        "kroger", "pilot", "flying j", "love's", "ta", "petro", "marathon",
        "citgo", "phillips 66", "conoco", "76", "arco", "sinclair", "hess",
        "holiday", "kwik trip", "casey's", "murphy", "racetrac", "wawa",
        "sheetz", "buc-ee's", "quiktrip", "cumberland farms", "getgo",
        "texaco", "amoco", "gulf"
    )

    private val datePatterns = listOf(
        Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b"""),
        Regex("""\b(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b"""),
        Regex("""\b([A-Z][a-z]{2,8})\s+(\d{1,2}),?\s+(\d{4})\b"""),
        Regex("""\b(\d{1,2})\s+([A-Z][a-z]{2,8})\s+(\d{4})\b""")
    )

    suspend fun parse(rawText: String): FuelPurchaseCandidate = withContext(Dispatchers.Default) {
        val startedAt = System.nanoTime()
        if (rawText.isBlank()) {
            DiagnosticLogger.recordParserFailure()
            DiagnosticLogger.w("Parser", "parse called with empty text")
            return@withContext FuelPurchaseCandidate(
                missingFields = listOf("stationName", "purchaseDate", "gallons", "pricePerGallon", "totalCost", "odometer"),
                overallConfidence = 0f
            )
        }

        try {
            val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val stationNameResult = extractStationName(lines, rawText)
            val dateResult = extractDate(rawText)
            val gallonsResult = extractGallons(rawText)
            val pricePerGallonResult = extractPricePerGallon(rawText)
            val totalCostResult = extractTotalCost(rawText, lines)
            val odometerResult = extractOdometer(rawText)

            val missing = mutableListOf<String>()
            if (stationNameResult.first == null) missing.add("stationName")
            if (dateResult.first == null) missing.add("purchaseDate")
            if (gallonsResult.first == null) missing.add("gallons")
            if (pricePerGallonResult.first == null) missing.add("pricePerGallon")
            if (totalCostResult.first == null) missing.add("totalCost")
            if (odometerResult.first == null) missing.add("odometer")

            val confidences = listOf(
                stationNameResult.second,
                dateResult.second,
                gallonsResult.second,
                pricePerGallonResult.second,
                totalCostResult.second,
                odometerResult.second
            ).filter { it > 0f }
            val overall = if (confidences.isEmpty()) 0f else confidences.average().toFloat()

            val candidate = FuelPurchaseCandidate(
                stationName = stationNameResult.first,
                stationNameConfidence = stationNameResult.second,
                purchaseDate = dateResult.first,
                purchaseDateConfidence = dateResult.second,
                gallons = gallonsResult.first,
                gallonsConfidence = gallonsResult.second,
                pricePerGallon = pricePerGallonResult.first,
                pricePerGallonConfidence = pricePerGallonResult.second,
                totalCost = totalCostResult.first,
                totalCostConfidence = totalCostResult.second,
                odometer = odometerResult.first,
                odometerConfidence = odometerResult.second,
                missingFields = missing,
                overallConfidence = overall
            )

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            val hadAny = listOf(
                candidate.stationName,
                candidate.purchaseDate,
                candidate.gallons,
                candidate.pricePerGallon,
                candidate.totalCost,
                candidate.odometer
            ).any { it != null }
            if (hadAny) DiagnosticLogger.recordParserSuccess() else DiagnosticLogger.recordParserFailure()
            DiagnosticLogger.d(
                "Parser",
                "parse ok lines=${lines.size} missing=${missing.size} overall=${"%.2f".format(overall)} elapsedMs=$elapsedMs"
            )
            candidate
        } catch (t: Throwable) {
            DiagnosticLogger.recordParserFailure()
            DiagnosticLogger.e("Parser", "parse failed", t)
            throw t
        }
    }

    private fun extractStationName(lines: List<String>, fullText: String): Pair<String?, Float> {
        val topLines = lines.take(5)
        val fullLower = fullText.lowercase()
        for (keyword in stationKeywords) {
            val index = fullLower.indexOf(keyword)
            if (index != -1) {
                val containingLine = topLines.firstOrNull { it.lowercase().contains(keyword) }
                    ?: fullText.substring(index, (index + keyword.length + 30).coerceAtMost(fullText.length))
                val cleaned = containingLine.trim().take(60)
                return Pair(cleaned, 0.85f)
            }
        }
        return Pair(topLines.firstOrNull(), 0.4f)
    }

    private fun extractDate(text: String): Pair<String?, Float> {
        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val (a, b, c) = match.groupValues.drop(1).take(3)
                val candidates = listOf(
                    "$a/$b/$c",
                    "$a-$b-$c",
                    "$c-$a-$b",
                    "$c/$a/$b"
                )
                return Pair(candidates.first(), 0.8f)
            }
        }
        return Pair(null, 0f)
    }

    private fun extractGallons(text: String): Pair<Double?, Float> {
        val lower = text.lowercase()
        val ppgPattern = Regex("""\b(\d+[.]\d{3})\s*(?:gal|gallon|gallons)\b""", RegexOption.IGNORE_CASE)
        val match1 = ppgPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 100) {
                return Pair(value, 0.9f)
            }
        }
        val galPrefixPattern = Regex("""\b(?:gal|gallons|gallon)\s+(\d+[.]?\d*)\b""", RegexOption.IGNORE_CASE)
        val match2 = galPrefixPattern.find(lower)
        if (match2 != null) {
            val value = match2.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 100) {
                return Pair(value, 0.85f)
            }
        }
        val decimalPattern = Regex("""\b(\d+[.]\d{3})\b""")
        val match3 = decimalPattern.find(text)
        if (match3 != null) {
            val value = match3.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 100) {
                return Pair(value, 0.6f)
            }
        }
        return Pair(null, 0f)
    }

    private fun extractPricePerGallon(text: String): Pair<Double?, Float> {
        val lower = text.lowercase()
        val ppgPattern = Regex("""[$]?(\d+[.]\d{3})\s*(?:/|per)\s*(?:gal|gallon|gallons)""", RegexOption.IGNORE_CASE)
        val match1 = ppgPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 20) {
                return Pair(value, 0.9f)
            }
        }
        val pricePattern = Regex("""(?:price|rate|ppg)\s*(?:[:/]|per|gal)\s*[$]?(\d+[.]\d{3})""", RegexOption.IGNORE_CASE)
        val match2 = pricePattern.find(lower)
        if (match2 != null) {
            val value = match2.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 20) {
                return Pair(value, 0.85f)
            }
        }
        val standalonePattern = Regex("""[$](\d+[.]\d{3})\b""")
        val match3 = standalonePattern.find(text)
        if (match3 != null) {
            val value = match3.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 20) {
                return Pair(value, 0.5f)
            }
        }
        return Pair(null, 0f)
    }

    private fun extractTotalCost(text: String, lines: List<String>): Pair<Double?, Float> {
        val lower = text.lowercase()
        val totalPattern = Regex("""\b(?:total|amount|amount\s+due|balance|charge|sale)\b[\s:.$]*[$]?\s*(\d+[.]\d{2})\b""", RegexOption.IGNORE_CASE)
        val match1 = totalPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 500) {
                return Pair(value, 0.95f)
            }
        }
        val dollarPattern = Regex("""[$](\d+[.]\d{2})\b""")
        val allMatches = dollarPattern.findAll(lower).toList()
        if (allMatches.isNotEmpty()) {
            val last = allMatches.last()
            val value = last.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 500) {
                return Pair(value, 0.4f)
            }
        }
        return Pair(null, 0f)
    }

    private fun extractOdometer(text: String): Pair<Int?, Float> {
        val lower = text.lowercase()
        // Primary pattern: look for odometer keywords followed by a number (allow commas)
        val odoPattern = Regex("""\b(?:odo|odometer|mileage|mi|miles)\s*[:]?\s*([\d,]{4,8})\b""", RegexOption.IGNORE_CASE)
        val candidates = mutableListOf<Pair<Int, Float>>()
        odoPattern.findAll(lower).forEach { match ->
            val raw = match.groupValues[1].replace(",", "")
            val value = raw.toIntOrNull()
            if (value != null && value > 0 && value < 999999) {
                // High confidence when keyword is present
                candidates.add(Pair(value, 0.85f))
            }
        }
        // Fallback: any standalone 5-6 digit number could be an odometer reading
        val standalonePattern = Regex("""\b(\d{5,6})\b""")
        standalonePattern.findAll(lower).forEach { match ->
            val value = match.groupValues[1].toIntOrNull()
            if (value != null && value > 10000 && value < 999999) {
                candidates.add(Pair(value, 0.4f))
            }
        }
        if (candidates.isNotEmpty()) {
            // Choose highest confidence; if tie, pick largest value (odometer should be monotonic)
            val best = candidates.maxWithOrNull(compareBy({ it.second }, { it.first }))
            // Log all candidates for debugging purposes
            DiagnosticLogger.d("Parser", "Odometer candidates: ${candidates.map { "${it.first}@${it.second}" }}")
            return Pair(best!!.first, best.second)
        }
        return Pair(null, 0f)
    }
}
