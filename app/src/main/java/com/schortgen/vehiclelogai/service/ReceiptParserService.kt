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
            val fuelNumbersResult = extractFuelNumbers(lines, rawText)
            val gallonsResult = fuelNumbersResult.gallons
            val pricePerGallonResult = fuelNumbersResult.pricePerGallon
            val totalCostResult = fuelNumbersResult.totalCost
            val odometerResult = extractOdometer(rawText)
            val tripDistanceResult = extractTripDistance(rawText)

            val missing = mutableListOf<String>()
            if (stationNameResult.first == null) missing.add("stationName")
            if (dateResult.first == null) missing.add("purchaseDate")
            if (gallonsResult.first == null) missing.add("gallons")
            if (pricePerGallonResult.first == null) missing.add("pricePerGallon")
            if (totalCostResult.first == null) missing.add("totalCost")
            if (odometerResult.first == null) missing.add("odometer")
            if (tripDistanceResult.first == null) missing.add("tripDistance")

            val confidences = listOf(
                stationNameResult.second,
                dateResult.second,
                gallonsResult.second,
                pricePerGallonResult.second,
                totalCostResult.second,
                odometerResult.second,
                tripDistanceResult.second
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
                tripDistance = tripDistanceResult.first,
                tripDistanceConfidence = tripDistanceResult.second,
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
                candidate.odometer,
                candidate.tripDistance
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
        val topLines = lines.take(8)
        val fullLower = fullText.lowercase()
        for (keyword in stationKeywords) {
            val index = fullLower.indexOf(keyword)
            if (index != -1) {
                val containingLine = topLines.firstOrNull { it.lowercase().contains(keyword) }
                    ?: lines.firstOrNull { it.lowercase().contains(keyword) }
                    ?: fullText.substring(index, (index + keyword.length + 30).coerceAtMost(fullText.length))
                val cleaned = containingLine.trim().take(60)
                return Pair(cleaned, 0.85f)
            }
        }
        val fallbackLine = topLines.firstOrNull { line ->
            val l = line.lowercase()
            !l.contains("receipt") && !l.contains("welcome") && !l.contains("store") &&
            !l.contains("date") && !l.contains("time") && !l.matches(Regex(""".*\d{3}-\d{3}-\d{4}.*"""))
        } ?: topLines.firstOrNull()
        return Pair(fallbackLine, 0.4f)
    }

    private fun extractDate(text: String): Pair<String?, Float> {
        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return Pair(match.value, 0.85f)
            }
        }
        return Pair(null, 0f)
    }

    data class ExtractedFuelNumbers(
        val gallons: Pair<Double?, Float>,
        val pricePerGallon: Pair<Double?, Float>,
        val totalCost: Pair<Double?, Float>
    )

    private fun extractFuelNumbers(lines: List<String>, fullText: String): ExtractedFuelNumbers {
        var gVal: Double? = null
        var gConf = 0f
        var pVal: Double? = null
        var pConf = 0f
        var tVal: Double? = null
        var tConf = 0f

        // 1. Single-line explicit @ / x / at patterns
        // e.g. "12.345 GAL @ $3.499/GAL" or "12.345 GAL @ 3.499" or "12.345 @ 3.499"
        val atPatterns = listOf(
            Regex("""\b(\d+[.]\d{1,3})\s*(?:gal|gals|gallon|gallons|g)?\s*@\s*[$]?(\d+[.]\d{2,3})""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d+[.]\d{1,3})\s*(?:gal|gals|gallon|gallons|g)?\s+at\s+[$]?(\d+[.]\d{2,3})""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d+[.]\d{1,3})\s*(?:gal|gals|gallon|gallons|g)?\s*x\s*[$]?(\d+[.]\d{2,3})""", RegexOption.IGNORE_CASE)
        )
        for (pattern in atPatterns) {
            val match = pattern.find(fullText)
            if (match != null) {
                val v1 = match.groupValues[1].toDoubleOrNull()
                val v2 = match.groupValues[2].toDoubleOrNull()
                if (v1 != null && v2 != null && v1 > 0 && v2 > 0) {
                    gVal = v1
                    gConf = 0.95f
                    pVal = v2
                    pConf = 0.95f
                    break
                }
            }
        }

        // 2. Line-by-line structured label matching
        for (line in lines) {
            val lowerLine = line.lowercase()
            val isPriceLine = lowerLine.contains("price") || lowerLine.contains("ppg") || lowerLine.contains("rate") ||
                              lowerLine.contains("unit") || lowerLine.contains("/gal") || lowerLine.contains("/g") ||
                              lowerLine.contains("per gal") || lowerLine.contains("per g") || lowerLine.contains("$/") ||
                              lowerLine.contains("@")
            val isTotalLine = lowerLine.contains("total") || lowerLine.contains("amount") || lowerLine.contains("charge") ||
                              lowerLine.contains("sale") || lowerLine.contains("balance") || lowerLine.contains("due")

            // A) Check for Gallons quantity (must NOT be a price or total line)
            if (gVal == null && !isPriceLine && !isTotalLine &&
                (lowerLine.contains("gal") || lowerLine.contains("qty") || lowerLine.contains("volume") || lowerLine.contains("gallons"))) {
                val galMatch = Regex("""\b(\d+[.]\d{2,3})\b""").find(line)
                if (galMatch != null) {
                    val v = galMatch.groupValues[1].toDoubleOrNull()
                    if (v != null && v > 0.1 && v < 200.0) {
                        gVal = v
                        gConf = 0.90f
                    }
                }
            }

            // B) Check for Price Per Gallon (must NOT be a total line)
            if (pVal == null && !isTotalLine && isPriceLine) {
                val priceMatch = Regex("""[$]?(\d+[.]\d{2,3})\b""").find(line)
                if (priceMatch != null) {
                    val v = priceMatch.groupValues[1].toDoubleOrNull()
                    if (v != null && v > 0.5 && v < 20.0) {
                        pVal = v
                        pConf = 0.90f
                    }
                }
            }

            // C) Check for Total Cost
            if (tVal == null && isTotalLine) {
                val totalMatch = Regex("""[$]?(\d+[.]\d{2})\b""").find(line)
                if (totalMatch != null) {
                    val v = totalMatch.groupValues[1].toDoubleOrNull()
                    if (v != null && v > 0.5 && v < 1000.0) {
                        tVal = v
                        tConf = 0.95f
                    }
                }
            }
        }

        // 3. Multi-number lines (e.g. "12.345  3.499  43.19" or "GALLONS 12.345 PRICE 3.499")
        if (gVal == null || pVal == null || tVal == null) {
            for (line in lines) {
                val numbers = Regex("""\b(\d+[.]\d{2,3})\b""").findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
                if (numbers.size >= 3) {
                    val n1 = numbers[0]
                    val n2 = numbers[1]
                    val n3 = numbers[2]
                    if (kotlin.math.abs(n1 * n2 - n3) < 0.25) {
                        if (gVal == null) { gVal = n1; gConf = 0.95f }
                        if (pVal == null) { pVal = n2; pConf = 0.95f }
                        if (tVal == null) { tVal = n3; tConf = 0.95f }
                    }
                } else if (numbers.size == 2 && gVal == null && pVal == null) {
                    val n1 = numbers[0]
                    val n2 = numbers[1]
                    if (n1 > n2 && n2 < 15.0 && n1 < 200.0) {
                        gVal = n1; gConf = 0.85f
                        pVal = n2; pConf = 0.85f
                    }
                }
            }
        }

        // 4. Fallback extractors for remaining nulls
        if (gVal == null) {
            val extractedG = extractGallons(fullText)
            gVal = extractedG.first
            gConf = extractedG.second
        }
        if (pVal == null) {
            val extractedP = extractPricePerGallon(fullText)
            pVal = extractedP.first
            pConf = extractedP.second
        }
        if (tVal == null) {
            val extractedT = extractTotalCost(fullText, lines)
            tVal = extractedT.first
            tConf = extractedT.second
        }

        // 5. Sanity Check & Swap Correction
        if (gVal != null && pVal != null) {
            if (gVal == pVal) {
                if (tVal != null && pVal!! > 0) {
                    gVal = kotlin.math.round((tVal!! / pVal!!) * 1000.0) / 1000.0
                    gConf = 0.85f
                }
            } else if (gVal!! < pVal!! && pVal!! > 0.5 && pVal!! < 20.0) {
                val tempG = gVal
                val tempConfG = gConf
                gVal = pVal
                gConf = pConf
                pVal = tempG
                pConf = tempConfG
                DiagnosticLogger.d("Parser", "Swapped gallons ($gVal) and pricePerGallon ($pVal) based on range heuristic")
            }
        }

        // 6. Cross-calculation for missing 3rd value if 2 are known
        if (gVal != null && pVal != null && tVal == null && gVal!! > 0 && pVal!! > 0) {
            tVal = kotlin.math.round(gVal!! * pVal!! * 100.0) / 100.0
            tConf = 0.85f
        } else if (gVal != null && tVal != null && pVal == null && gVal!! > 0) {
            pVal = kotlin.math.round((tVal!! / gVal!!) * 1000.0) / 1000.0
            pConf = 0.85f
        } else if (pVal != null && tVal != null && gVal == null && pVal!! > 0) {
            gVal = kotlin.math.round((tVal!! / pVal!!) * 1000.0) / 1000.0
            gConf = 0.85f
        }

        return ExtractedFuelNumbers(
            gallons = Pair(gVal, gConf),
            pricePerGallon = Pair(pVal, pConf),
            totalCost = Pair(tVal, tConf)
        )
    }

    private fun extractGallons(text: String): Pair<Double?, Float> {
        val lower = text.lowercase()
        val galSuffixPattern = Regex("""\b(\d+[.]\d{2,3})\s*(?:gal|gals|gallon|gallons)\b""", RegexOption.IGNORE_CASE)
        for (match in galSuffixPattern.findAll(lower)) {
            val valStr = match.groupValues[1]
            val startIdx = (match.range.first - 10).coerceAtLeast(0)
            val endIdx = (match.range.last + 10).coerceAtMost(lower.length)
            val context = lower.substring(startIdx, endIdx)
            if (!context.contains("price") && !context.contains("/") && !context.contains("per") && !context.contains("@") && !context.contains("ppg")) {
                val value = valStr.toDoubleOrNull()
                if (value != null && value > 0.5 && value < 200.0) {
                    return Pair(value, 0.90f)
                }
            }
        }

        val galPrefixPattern = Regex("""\b(?:gal|gals|gallons|gallon|qty|volume)\s*[:]?\s*(\d+[.]\d{2,3})\b""", RegexOption.IGNORE_CASE)
        for (match in galPrefixPattern.findAll(lower)) {
            val valStr = match.groupValues[1]
            val value = valStr.toDoubleOrNull()
            if (value != null && value > 0.5 && value < 200.0) {
                return Pair(value, 0.85f)
            }
        }

        return Pair(null, 0f)
    }

    private fun extractPricePerGallon(text: String): Pair<Double?, Float> {
        val lower = text.lowercase()
        val ppgPattern = Regex("""[$]?(\d+[.]\d{2,3})\s*(?:/|per)\s*(?:gal|gals|gallon|gallons|g)\b""", RegexOption.IGNORE_CASE)
        val match1 = ppgPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0.5 && value < 20.0) {
                return Pair(value, 0.90f)
            }
        }

        val pricePrefixPattern = Regex("""\b(?:price/gal|price/g|price|ppg|rate|unit\s+price|unit)\s*[:/]?\s*[$]?(\d+[.]\d{2,3})\b""", RegexOption.IGNORE_CASE)
        val match2 = pricePrefixPattern.find(lower)
        if (match2 != null) {
            val value = match2.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0.5 && value < 20.0) {
                return Pair(value, 0.85f)
            }
        }

        return Pair(null, 0f)
    }

    private fun extractTotalCost(text: String, lines: List<String>): Pair<Double?, Float> {
        val lower = text.lowercase()
        val totalPattern = Regex("""\b(?:total|fuel\s+total|total\s+cost|total\s+due|amount|amount\s+due|balance|charge|sale)\b[\s:.$]*[$]?\s*(\d+[.]\d{2})\b""", RegexOption.IGNORE_CASE)
        val match1 = totalPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0.5 && value < 1000.0) {
                return Pair(value, 0.95f)
            }
        }

        val dollarPattern = Regex("""[$](\d+[.]\d{2})\b""")
        val allMatches = dollarPattern.findAll(lower).toList()
        if (allMatches.isNotEmpty()) {
            val last = allMatches.last()
            val value = last.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0.5 && value < 1000.0) {
                return Pair(value, 0.50f)
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

    private fun extractTripDistance(text: String): Pair<Double?, Float> {
        val lower = text.lowercase()
        // Match patterns like "trip 254.3", "trip a 120.5", "trip dist: 310.2", "dist 45.1"
        val tripPattern = Regex("""\b(?:trip|trip\s*[ab]|dist|distance|trip\s*miles)\s*[:]?\s*(\d+[.]\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val match1 = tripPattern.find(lower)
        if (match1 != null) {
            val value = match1.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 9999) {
                return Pair(value, 0.85f)
            }
        }
        // Match suffix "254.3 mi" or "254.3 miles"
        val miPattern = Regex("""\b(\d+[.]\d{1,2})\s*(?:mi|miles)\b""", RegexOption.IGNORE_CASE)
        val match2 = miPattern.find(lower)
        if (match2 != null) {
            val value = match2.groupValues[1].toDoubleOrNull()
            if (value != null && value > 0 && value < 9999) {
                return Pair(value, 0.80f)
            }
        }
        return Pair(null, 0f)
    }
}
