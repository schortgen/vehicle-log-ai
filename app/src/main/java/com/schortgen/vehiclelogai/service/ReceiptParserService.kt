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

        // 1. Check for single-line @ syntax: e.g. "12.500 GAL @ $3.299/GAL" or "12.500 @ 3.299"
        val atPattern = Regex("""\b(\d+[.]\d{1,3})\s*(?:gal|gals|gallon|gallons|g)?\s*@\s*[$]?(\d+[.]\d{2,3})""", RegexOption.IGNORE_CASE)
        val atMatch = atPattern.find(fullText)
        if (atMatch != null) {
            val v1 = atMatch.groupValues[1].toDoubleOrNull()
            val v2 = atMatch.groupValues[2].toDoubleOrNull()
            if (v1 != null && v2 != null && v1 > 0 && v2 > 0) {
                gVal = v1
                gConf = 0.95f
                pVal = v2
                pConf = 0.95f
            }
        }

        // 2. Line-by-line structured label matching
        if (gVal == null || pVal == null || tVal == null) {
            for (line in lines) {
                val lowerLine = line.lowercase()

                // Check line for gallons keyword & number
                if (gVal == null && (lowerLine.contains("gal") || lowerLine.contains("qty") || lowerLine.contains("volume"))) {
                    val galMatch = Regex("""\b(\d+[.]\d{2,3})\b""").find(line)
                    if (galMatch != null) {
                        val v = galMatch.groupValues[1].toDoubleOrNull()
                        if (v != null && v > 0 && v < 200) {
                            gVal = v
                            gConf = 0.90f
                        }
                    }
                }

                // Check line for price per gallon keyword & number
                if (pVal == null && (lowerLine.contains("price/g") || lowerLine.contains("ppg") || lowerLine.contains("rate") || lowerLine.contains("unit price") || lowerLine.contains("/gal") || lowerLine.contains("price"))) {
                    val priceMatch = Regex("""[$]?(\d+[.]\d{2,3})\b""").find(line)
                    if (priceMatch != null) {
                        val v = priceMatch.groupValues[1].toDoubleOrNull()
                        if (v != null && v > 0 && v < 20) {
                            pVal = v
                            pConf = 0.90f
                        }
                    }
                }

                // Check line for total cost keyword & number
                if (tVal == null && (lowerLine.contains("total") || lowerLine.contains("amount") || lowerLine.contains("charge") || lowerLine.contains("sale"))) {
                    val totalMatch = Regex("""[$]?(\d+[.]\d{2})\b""").find(line)
                    if (totalMatch != null) {
                        val v = totalMatch.groupValues[1].toDoubleOrNull()
                        if (v != null && v > 0 && v < 1000) {
                            tVal = v
                            tConf = 0.95f
                        }
                    }
                }
            }
        }

        // 3. Table header & table row extraction
        if (gVal == null || pVal == null) {
            for (i in 0 until lines.size - 1) {
                val lineHeader = lines[i].lowercase()
                if ((lineHeader.contains("gal") || lineHeader.contains("qty")) &&
                    (lineHeader.contains("price") || lineHeader.contains("ppg") || lineHeader.contains("rate") || lineHeader.contains("amount"))
                ) {
                    val nextLine = lines[i + 1]
                    val nums = Regex("""\b(\d+[.]\d{2,3})\b""").findAll(nextLine).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
                    if (nums.size >= 2) {
                        if (gVal == null) { gVal = nums[0]; gConf = 0.90f }
                        if (pVal == null) { pVal = nums[1]; pConf = 0.90f }
                        if (nums.size >= 3 && tVal == null) { tVal = nums[2]; tConf = 0.90f }
                    }
                }
            }
        }

        // 4. Fallback to existing individual extractors if needed
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

        // 5. Swap Detection & Correction
        if (gVal != null && pVal != null) {
            val isSwappedByRange = (pVal!! > 8.0 && gVal!! < 8.0)
            val isSwappedByMath = (tVal != null && kotlin.math.abs(pVal!! * gVal!! - tVal!!) <= 0.25 && pVal!! > gVal!!)
            if (isSwappedByRange || isSwappedByMath) {
                val tempVal = gVal
                val tempConf = gConf
                gVal = pVal
                gConf = pConf
                pVal = tempVal
                pConf = tempConf
                DiagnosticLogger.d("Parser", "Swapped gallons ($gVal) and pricePerGallon ($pVal) based on range/math heuristic")
            }
        }

        // 6. Cross-calculation for missing values
        if (gVal != null && pVal != null && tVal == null) {
            tVal = kotlin.math.round(gVal!! * pVal!! * 100.0) / 100.0
            tConf = 0.85f
        } else if (gVal != null && tVal != null && pVal == null && gVal!! > 0) {
            pVal = kotlin.math.round(tVal!! / gVal!! * 1000.0) / 1000.0
            pConf = 0.85f
        } else if (pVal != null && tVal != null && gVal == null && pVal!! > 0) {
            gVal = kotlin.math.round(tVal!! / pVal!! * 1000.0) / 1000.0
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
