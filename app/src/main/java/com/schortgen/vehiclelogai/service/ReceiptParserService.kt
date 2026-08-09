package com.schortgen.vehiclelogai.service

import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deterministic receipt parser that consumes OCR raw text and produces
 * a FuelPurchaseCandidate using regular expressions, tokenization,
 * normalization, and heuristics.
 */
class ReceiptParserService {

    private val stationBrandMap = listOf(
        Pair(Regex("""\bshell\b""", RegexOption.IGNORE_CASE), "Shell"),
        Pair(Regex("""\bchevron\b""", RegexOption.IGNORE_CASE), "Chevron"),
        Pair(Regex("""\bexxonmobil\b""", RegexOption.IGNORE_CASE), "ExxonMobil"),
        Pair(Regex("""\bexxon\b""", RegexOption.IGNORE_CASE), "Exxon"),
        Pair(Regex("""\bmobil\b""", RegexOption.IGNORE_CASE), "Mobil"),
        Pair(Regex("""\b(bp|amoco)\b""", RegexOption.IGNORE_CASE), "BP"),
        Pair(Regex("""\btexaco\b""", RegexOption.IGNORE_CASE), "Texaco"),
        Pair(Regex("""\bmarathon\b""", RegexOption.IGNORE_CASE), "Marathon"),
        Pair(Regex("""\bsunoco\b""", RegexOption.IGNORE_CASE), "Sunoco"),
        Pair(Regex("""\bphillips\s*66\b""", RegexOption.IGNORE_CASE), "Phillips 66"),
        Pair(Regex("""\bvalero\b""", RegexOption.IGNORE_CASE), "Valero"),
        Pair(Regex("""\bcircle\s*k\b""", RegexOption.IGNORE_CASE), "Circle K"),
        Pair(Regex("""\b(7-eleven|7\s*eleven|seven\s*eleven|711)\b""", RegexOption.IGNORE_CASE), "7-Eleven"),
        Pair(Regex("""\bcostco\b""", RegexOption.IGNORE_CASE), "Costco"),
        Pair(Regex("""\bsam'?s\s*club\b""", RegexOption.IGNORE_CASE), "Sam's Club"),
        Pair(Regex("""\bbj'?s\b""", RegexOption.IGNORE_CASE), "BJ's Wholesale"),
        Pair(Regex("""\bcasey'?s\b""", RegexOption.IGNORE_CASE), "Casey's"),
        Pair(Regex("""\bkwik\s*(trip|star)\b""", RegexOption.IGNORE_CASE), "Kwik Trip"),
        Pair(Regex("""\bsheetz\b""", RegexOption.IGNORE_CASE), "Sheetz"),
        Pair(Regex("""\bwawa\b""", RegexOption.IGNORE_CASE), "Wawa"),
        Pair(Regex("""\b(pilot|flying\s*j)\b""", RegexOption.IGNORE_CASE), "Pilot Flying J"),
        Pair(Regex("""\blove'?s\b""", RegexOption.IGNORE_CASE), "Love's"),
        Pair(Regex("""\bbuc-?ee'?s\b""", RegexOption.IGNORE_CASE), "Buc-ee's"),
        Pair(Regex("""\bspeedway\b""", RegexOption.IGNORE_CASE), "Speedway"),
        Pair(Regex("""\bcumberland\s*(farms)?\b""", RegexOption.IGNORE_CASE), "Cumberland Farms"),
        Pair(Regex("""\barco\b""", RegexOption.IGNORE_CASE), "ARCO"),
        Pair(Regex("""\bsinclair\b""", RegexOption.IGNORE_CASE), "Sinclair"),
        Pair(Regex("""\bcitgo\b""", RegexOption.IGNORE_CASE), "CITGO"),
        Pair(Regex("""\bmurphy\s*(usa|express)?\b""", RegexOption.IGNORE_CASE), "Murphy USA"),
        Pair(Regex("""\bkum\s*(&|and)\s*go\b""", RegexOption.IGNORE_CASE), "Kum & Go"),
        Pair(Regex("""\bquiktrip\b""", RegexOption.IGNORE_CASE), "QuikTrip"),
        Pair(Regex("""\bmaveri?k\b""", RegexOption.IGNORE_CASE), "Maverik"),
        Pair(Regex("""\bgulf\b""", RegexOption.IGNORE_CASE), "Gulf"),
        Pair(Regex("""\b(racetrac|raceway)\b""", RegexOption.IGNORE_CASE), "RaceTrac"),
        Pair(Regex("""\bthorntons?\b""", RegexOption.IGNORE_CASE), "Thorntons"),
        Pair(Regex("""\bmapco\b""", RegexOption.IGNORE_CASE), "MAPCO"),
        Pair(Regex("""\bgetgo\b""", RegexOption.IGNORE_CASE), "GetGo"),
        Pair(Regex("""\bkroger\b""", RegexOption.IGNORE_CASE), "Kroger"),
        Pair(Regex("""\bmeijer\b""", RegexOption.IGNORE_CASE), "Meijer"),
        Pair(Regex("""\bsafeway\b""", RegexOption.IGNORE_CASE), "Safeway"),
        Pair(Regex("""\bh-?e-?b\b""", RegexOption.IGNORE_CASE), "H-E-B"),
        Pair(Regex("""\bhy-?vee\b""", RegexOption.IGNORE_CASE), "Hy-Vee"),
        Pair(Regex("""\bgiant\s*eagle\b""", RegexOption.IGNORE_CASE), "Giant Eagle"),
        Pair(Regex("""\bholiday\b""", RegexOption.IGNORE_CASE), "Holiday"),
        Pair(Regex("""\broyal\s*farms\b""", RegexOption.IGNORE_CASE), "Royal Farms"),
        Pair(Regex("""\birving\b""", RegexOption.IGNORE_CASE), "Irving Oil"),
        Pair(Regex("""\bdash\s*in\b""", RegexOption.IGNORE_CASE), "Dash In"),
        Pair(Regex("""\bstewart'?s\b""", RegexOption.IGNORE_CASE), "Stewart's")
    )

    private val stationKeywords = stationBrandMap.map { it.second.lowercase() }

    private val datePatterns = listOf(
        Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b"""),
        Regex("""\b(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b"""),
        Regex("""\b([A-Z][a-z]{2,8})\s+(\d{1,2}),?\s+(\d{4})\b"""),
        Regex("""\b(\d{1,2})\s+([A-Z][a-z]{2,8})\s+(\d{4})\b""")
    )

    suspend fun parse(
        rawText: String,
        preferredTripMeter: PreferredTripMeter = PreferredTripMeter.TRIP_A,
        previousOdometer: Int? = null
    ): FuelPurchaseCandidate = withContext(Dispatchers.Default) {
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

            var warningMessage: String? = null
            var tripDistanceResult = extractTripDistance(
                rawText,
                gallonsResult.first,
                totalCostResult.first,
                preferredTripMeter,
                odometerResult.first,
                previousOdometer
            )

            if (preferredTripMeter == PreferredTripMeter.ANY) {
                val curOdo = odometerResult.first
                if (curOdo != null) {
                    if (previousOdometer != null) {
                        val diff = curOdo - previousOdometer
                        if (diff >= 0) {
                            tripDistanceResult = Pair(diff.toDouble(), 0.95f)
                            if (diff > 600) {
                                warningMessage = "Odometer difference is over 600 miles ($diff mi). You might be missing a Fueling event."
                            }
                        } else {
                            tripDistanceResult = Pair(null, 0f)
                            warningMessage = "Current odometer ($curOdo) is less than previous odometer ($previousOdometer)."
                        }
                    } else {
                        tripDistanceResult = Pair(null, 0f)
                        warningMessage = "No previous odometer recorded. You might be missing a Fueling event."
                    }
                }
            }

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
                overallConfidence = overall,
                warningMessage = warningMessage
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
        val fullLower = fullText.lowercase()
        // OCR text normalization (e.g. "S H E L L" -> "shell", "C H E V R O N" -> "chevron")
        val normalizedFull = fullLower
            .replace(Regex("""\b([a-z])\s+([a-z])\s+([a-z])\s+([a-z])\b"""), "$1$2$3$4")
            .replace(Regex("""\b([a-z])\s+([a-z])\s+([a-z])\b"""), "$1$2$3")

        // 1. Direct Brand Match using Canonical Brand Dictionary
        for ((regex, canonicalName) in stationBrandMap) {
            if (regex.containsMatchIn(fullLower) || regex.containsMatchIn(normalizedFull)) {
                return Pair(canonicalName, 0.95f)
            }
        }

        // 2. Generic station indicator match in top 8 lines
        val topLines = lines.take(8)
        val stationIndicatorPattern = Regex("""\b(?:station|gas|mart|express|store|market|service\s+station|fuel|clean|stop|plaza|inc|llc)\b""", RegexOption.IGNORE_CASE)
        val excludePattern = Regex("""\b(?:receipt|welcome|date|time|phone|thank|mileage|odometer|trip|temp|°f|°c|mph|rpm|prnd|street|ave|blvd|rd|dr|hwy)\b""", RegexOption.IGNORE_CASE)

        for (line in topLines) {
            val l = line.lowercase()
            if (stationIndicatorPattern.containsMatchIn(l) && !excludePattern.containsMatchIn(l)) {
                val cleaned = line.trim()
                    .replace(Regex("""^[*#\s]+"""), "")
                    .replace(Regex("""[#*]+.*$"""), "")
                    .trim()
                    .take(50)
                if (cleaned.length >= 3) {
                    return Pair(cleaned, 0.65f)
                }
            }
        }

        // 3. Fallback to clean business header line (top 4 lines)
        for (line in topLines.take(4)) {
            val upper = line.uppercase()
            if (upper.contains("WELCOME") || upper.contains("RECEIPT") || upper.contains("THANK") ||
                upper.contains("DATE") || upper.contains("TIME") || upper.contains("PUMP") ||
                upper.contains("GALLON") || upper.contains("TOTAL") || upper.contains("TEL") ||
                upper.contains("PHONE") || line.contains("$") || line.matches(Regex(""".*\d{3,}.*"""))) {
                continue
            }
            if (line.count { it.isLetter() } >= 3) {
                val cleanedHeader = line.split(" ")
                    .filter { word -> !word.contains(Regex("""\d""")) }
                    .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
                    .trim()
                if (cleanedHeader.length >= 3) {
                    return Pair(cleanedHeader, 0.50f)
                }
            }
        }

        return Pair(null, 0f)
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
        val lowerFull = fullText.lowercase()
        val hasDollar = lowerFull.contains("$")
        val hasStationKw = stationKeywords.any { lowerFull.contains(it) }
        val hasFuelKw = lowerFull.contains("gallons") || lowerFull.contains("price/gal") ||
                        lowerFull.contains("price per gal") || lowerFull.contains("total cost") ||
                        lowerFull.contains("fuel total") || lowerFull.contains("total due") ||
                        lowerFull.contains("pump") || lowerFull.contains("receipt")

        if (!hasDollar && !hasStationKw && !hasFuelKw) {
            return ExtractedFuelNumbers(
                gallons = Pair(null, 0f),
                pricePerGallon = Pair(null, 0f),
                totalCost = Pair(null, 0f)
            )
        }

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

            // A) Check for Gallons quantity (must NOT be a price or total line, and must NOT be MPG)
            if (gVal == null && !isPriceLine && !isTotalLine && !lowerLine.contains("mpg") && !lowerLine.contains("mi/gal") &&
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
            if (!context.contains("price") && !context.contains("/") && !context.contains("per") && !context.contains("@") && !context.contains("ppg") && !context.contains("mpg")) {
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
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val candidates = mutableListOf<Pair<Int, Float>>()

        // Normalize text line for OCR character confusion in numeric contexts (e.g., "12345O" -> "123450")
        // and clean gear shift indicators
        val sanitizedText = text
            .replace(Regex("""(\b\d{4,6})[Oo]\b"""), "$10")
            .replace(Regex("""\b[Oo](\d{4,6}\b)"""), "0$1")
            .replace(Regex("""\bP\s*R\s*N\s*D\s*[321L]?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bPRND[321L]?\b""", RegexOption.IGNORE_CASE), "")

        // Pattern 1: Number followed by unit or keyword (e.g. "124,567 mi", "124567.8 mi", "124567 miles", "124567 odo", "124567 km")
        val numKeyPattern = Regex("""\b([\d,]{4,7}(?:\.\d)?)\s*(?:mi|miles|km|odo|odometer)\b""", RegexOption.IGNORE_CASE)
        numKeyPattern.findAll(sanitizedText).forEach { match ->
            val lineStart = (match.range.first - 25).coerceAtLeast(0)
            val lineEnd = (match.range.last + 25).coerceAtMost(sanitizedText.length)
            val context = sanitizedText.substring(lineStart, lineEnd).lowercase()
            if (!context.contains("trip") && !context.contains("dist") && !context.contains("gal") && !context.contains("$")) {
                val raw = match.groupValues[1].replace(",", "")
                val doubleVal = raw.toDoubleOrNull()
                val value = doubleVal?.toInt()
                if (value != null && value in 1000..999999) {
                    candidates.add(Pair(value, 0.95f))
                }
            }
        }

        // Pattern 2: Keyword followed by number (e.g. "odo 124567", "odometer: 124,567", "mileage 124567")
        val keyNumPattern = Regex("""\b(?:odo|odometer|mileage)\s*[:=]?\s*([\d,]{4,7}(?:\.\d)?)\b""", RegexOption.IGNORE_CASE)
        keyNumPattern.findAll(sanitizedText).forEach { match ->
            val raw = match.groupValues[1].replace(",", "")
            val doubleVal = raw.toDoubleOrNull()
            val value = doubleVal?.toInt()
            if (value != null && value in 1000..999999) {
                candidates.add(Pair(value, 0.95f))
            }
        }

        // Pattern 3: Multi-line adjacent check (e.g., Line 1: "ODO" or "ODOMETER", Line 2: "124,567")
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            if ((lineLower.contains("odo") || lineLower.contains("odometer") || lineLower.contains("mileage")) && !lineLower.contains("trip")) {
                val neighborLines = listOfNotNull(
                    lines[i],
                    lines.getOrNull(i - 1),
                    lines.getOrNull(i + 1)
                )
                for (nLine in neighborLines) {
                    if (!nLine.lowercase().contains("trip")) {
                        val numMatch = Regex("""\b([\d,]{4,7}(?:\.\d)?)\b""").find(nLine)
                        if (numMatch != null) {
                            val raw = numMatch.groupValues[1].replace(",", "")
                            val value = raw.toDoubleOrNull()?.toInt()
                            if (value != null && value in 1000..999999) {
                                candidates.add(Pair(value, 0.85f))
                            }
                        }
                    }
                }
            }
        }

        // Pattern 4: Fallback standalone 5-6 digit integer (e.g., on dashboard photo)
        // Must strictly filter out US zip codes, phone numbers, store numbers, addresses, dates, timestamps, AND trip numbers
        val standalonePattern = Regex("""\b(\d{5,6})\b""")
        standalonePattern.findAll(sanitizedText).forEach { match ->
            val lineStart = (match.range.first - 40).coerceAtLeast(0)
            val lineEnd = (match.range.last + 40).coerceAtMost(sanitizedText.length)
            val context = sanitizedText.substring(lineStart, lineEnd).lowercase()

            val isTripContext = context.contains("trip") || context.contains("dist")
            val isMetadata = context.contains("$") || isTripContext || context.contains("tel") ||
                    context.contains("phone") || context.contains("fax") || context.contains("store") ||
                    context.contains("pump") || context.contains("st#") || context.contains("trans") ||
                    context.contains("auth") || context.contains("card") || context.contains("date") ||
                    context.contains("time") || context.contains("receipt") || context.contains("invoice") ||
                    context.contains("st") || context.contains("ave") || context.contains("blvd") ||
                    context.contains("rd") || context.contains("dr") || context.contains("hwy") ||
                    context.contains("suite") || context.contains("po box") || context.contains("zip")

            // Check if preceded by 2-letter state code (e.g. "CA 90210", "TX 75001")
            val isZipCode = Regex("""\b(?:AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY)\s+\d{5}\b""", RegexOption.IGNORE_CASE).containsMatchIn(context)

            if (!isMetadata && !isZipCode) {
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 10000..999999) {
                    candidates.add(Pair(value, 0.45f))
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val best = candidates.maxWithOrNull(compareBy({ it.second }, { it.first }))
            DiagnosticLogger.d("Parser", "Odometer candidates: ${candidates.map { "${it.first}@${it.second}" }}")
            return Pair(best!!.first, best.second)
        }

        return Pair(null, 0f)
    }

    private fun extractTripDistance(
        text: String,
        gallons: Double? = null,
        totalCost: Double? = null,
        preferredTripMeter: PreferredTripMeter = PreferredTripMeter.TRIP_A
    ): Pair<Double?, Float> {
        // Clean gear shift indicators (e.g. PRND3, PRND, P R N D 3, PRNDL) commonly present on instrument clusters
        val sanitizedText = text
            .replace(Regex("""\bP\s*R\s*N\s*D\s*[321L]?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bPRND[321L]?\b""", RegexOption.IGNORE_CASE), "")

        val lines = sanitizedText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val candidates = mutableListOf<Triple<Double, Float, String>>()

        fun getAdjustedConfidence(baseConf: Float, meterType: String): Float {
            return when (preferredTripMeter) {
                PreferredTripMeter.TRIP_A -> when (meterType) {
                    "A" -> (baseConf + 0.05f).coerceAtMost(0.99f)
                    "B" -> baseConf - 0.35f
                    else -> baseConf
                }
                PreferredTripMeter.TRIP_B -> when (meterType) {
                    "B" -> (baseConf + 0.05f).coerceAtMost(0.99f)
                    "A" -> baseConf - 0.35f
                    else -> baseConf
                }
                PreferredTripMeter.ANY -> baseConf
            }
        }

        // Pattern 0: Direct dashboard "A: 412.6" or "B: 4582.4" match when "TRIP" or "MI" is anywhere in raw text
        val isDashboardContext = sanitizedText.contains("trip", ignoreCase = true) || sanitizedText.contains("mi", ignoreCase = true)
        if (isDashboardContext) {
            val abTripPattern = Regex("""\b([AB12])\s*[:=]?\s*(\d+(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE)
            abTripPattern.findAll(sanitizedText).forEach { match ->
                val typeChar = match.groupValues[1].uppercase()
                val meterType = if (typeChar == "A" || typeChar == "1") "A" else "B"
                var value = match.groupValues[2].toDoubleOrNull()
                if (value != null) {
                    if (value in 1000.0..99999.0 && !match.groupValues[2].contains(".")) {
                        value /= 10.0
                    }
                    if (value > 0.0 && value < 9999.0) {
                        val matchesGallons = gallons != null && kotlin.math.abs(value - gallons) < 0.05
                        val matchesTotal = totalCost != null && kotlin.math.abs(value - totalCost) < 0.05
                        if (!matchesGallons && !matchesTotal) {
                            val conf = getAdjustedConfidence(0.95f, meterType)
                            candidates.add(Triple(value, conf, meterType))
                        }
                    }
                }
            }
        }

        // Pattern 1: Explicit trip keyword + number on same line (e.g. "trip a 120.5", "trip b 45", "trip dist: 310.2")
        val tripPattern = Regex("""\b(?:trip\s*([ab12])?|dist|distance|trip\s*dist|trip\s*miles)\s*[:=]?\s*(\d+(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE)
        tripPattern.findAll(sanitizedText).forEach { match ->
            val typeGroup = match.groupValues[1].uppercase()
            val meterType = when (typeGroup) {
                "A", "1" -> "A"
                "B", "2" -> "B"
                else -> "GENERIC"
            }
            var value = match.groupValues[2].toDoubleOrNull()
            if (value != null) {
                if (value in 1000.0..99999.0 && !match.groupValues[2].contains(".")) {
                    value /= 10.0
                }
                if (value > 0.0 && value < 9999.0) {
                    val matchesGallons = gallons != null && kotlin.math.abs(value - gallons) < 0.05
                    val matchesTotal = totalCost != null && kotlin.math.abs(value - totalCost) < 0.05
                    if (!matchesGallons && !matchesTotal) {
                        val conf = getAdjustedConfidence(0.95f, meterType)
                        candidates.add(Triple(value, conf, meterType))
                    }
                }
            }
        }

        // Pattern 2: Multi-line trip check (e.g., Line 1: "TRIP A:", Line 2: "412.6 MI")
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase()
            if (lineLower.contains("trip") || lineLower.contains("dist")) {
                val meterType = when {
                    lineLower.contains("trip a") || lineLower.contains("a:") || lineLower.contains("trip 1") -> "A"
                    lineLower.contains("trip b") || lineLower.contains("b:") || lineLower.contains("trip 2") -> "B"
                    else -> "GENERIC"
                }
                val neighborLines = listOfNotNull(
                    lines.getOrNull(i + 1),
                    lines.getOrNull(i - 1),
                    lines[i]
                )
                for (nLine in neighborLines) {
                    val nLower = nLine.lowercase()
                    if (!nLower.contains("odometer") && !nLower.contains("odo ")) {
                        val numMatches = Regex("""\b(\d+(?:\.\d{1,2})?)\b""").findAll(nLine)
                        for (numMatch in numMatches) {
                            var value = numMatch.groupValues[1].toDoubleOrNull()
                            if (value != null) {
                                // Infer missing decimal point on dot matrix display if 4 or 5 digits without decimal (e.g. 45824 -> 4582.4 or 4126 -> 412.6)
                                if (value in 1000.0..99999.0 && !numMatch.value.contains(".")) {
                                    value /= 10.0
                                }
                                if (value > 0.0 && value < 9999.0) {
                                    val matchesGallons = gallons != null && kotlin.math.abs(value - gallons) < 0.05
                                    val matchesTotal = totalCost != null && kotlin.math.abs(value - totalCost) < 0.05
                                    if (!matchesGallons && !matchesTotal) {
                                        val conf = getAdjustedConfidence(0.92f, meterType)
                                        candidates.add(Triple(value, conf, meterType))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pattern 3: Suffix "254.3 mi" or "254.3 miles" if value < 2000.0 and context has trip or no odo
        val miPattern = Regex("""\b(\d+[.]\d{1,2})\s*(?:mi|miles)\b""", RegexOption.IGNORE_CASE)
        miPattern.findAll(sanitizedText).forEach { match ->
            val lineStart = (match.range.first - 15).coerceAtLeast(0)
            val lineEnd = (match.range.last + 15).coerceAtMost(sanitizedText.length)
            val context = sanitizedText.substring(lineStart, lineEnd).lowercase()
            if (!context.contains("odometer") && !context.contains("odo ")) {
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null && value > 0.0 && value < 2000.0) {
                    val matchesGallons = gallons != null && kotlin.math.abs(value - gallons) < 0.05
                    val matchesTotal = totalCost != null && kotlin.math.abs(value - totalCost) < 0.05
                    if (!matchesGallons && !matchesTotal) {
                        val conf = getAdjustedConfidence(0.75f, "GENERIC")
                        candidates.add(Triple(value, conf, "GENERIC"))
                    }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            val best = candidates.maxWithOrNull(compareBy({ it.second }, { it.first }))
            DiagnosticLogger.d("Parser", "Trip candidates (pref=$preferredTripMeter): ${candidates.map { "${it.first}@${it.second}[${it.third}]" }}")
            return Pair(best!!.first, best.second)
        }

        return Pair(null, 0f)
    }
}
