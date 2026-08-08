# App Description & AI Photo Processing Pipeline

## Overview
Vehicle Log AI is an intelligent vehicle maintenance and fuel tracking application. It automatically scans receipt photos, extracts log data using optical character recognition (OCR) and pattern matching heuristics, and organizes fuel/service events into structured timelines for user vehicles.

---

## AI & Photo Data Processing Pipeline

```
[ Photo Scan ] ──> [ ML Kit OCR ] ──> [ Receipt Parser ] ──> [ Candidate Mapper ] ──> [ Review Queue ] ──> [ Vehicle Timeline ]
```

### 1. Photo Scanning (`PhotoScannerService` & `PhotoScannerRepository`)
* Scans photos from the device MediaStore across specified date ranges.
* Prevents duplicate entries by checking both `mediaStoreId` and normalized photo URIs against previously imported photos and existing review queue items.

### 2. On-Device Text Recognition (`MlKitOcrService`)
* Uses Google's ML Kit Vision Text Recognition engine on-device to extract text from photo captures.
* Reconstructs recognized text line-by-line, left-to-right, and top-to-bottom to preserve spatial formatting necessary for receipt structure.

### 3. Receipt Parsing & Candidate Extraction (`ReceiptParserService`)
* Consumes raw OCR text and applies deterministic parsing heuristics and regular expressions to identify key fuel metrics:
  * **Total Cost:** Looks for total keywords (`TOTAL`, `AMOUNT`, `DUE`, `$XX.XX`).
  * **Gallons / Quantity:** Identifies gallon indicators (`GAL`, `GALLONS`, `PUMP`).
  * **Price Per Gallon:** Matches unit rate formats (`$/GAL`, `@ $X.XXX`).
  * **Odometer Reading:** Identifies odometer tags (`ODO`, `MILES`, `MI`).
  * **Station / Location:** Detects gas station brand names (e.g., Shell, Chevron, Exxon, Mobil, Costco, BP).
* Assigns confidence scores (0.0 – 1.0) for each field based on contextual matching strength, computing an overall purchase candidate confidence.

### 4. Event Grouping (`EventGroupingService`)
* Groups unconfirmed review items into photo clusters based on capture timestamp proximity.
* Automatically merges multi-photo fuel receipts, infers event types (e.g., Fuel Purchase vs. Service), and selects representative cover photos.

---

## Human-in-the-Loop & Error Correction ("Learning from Mistakes")

### 1. Interactive Review Queue
* Extracted candidates are placed in the Review Queue before being added to vehicle logs.
* Users can open any candidate in `ReviewDetailScreen` to inspect parsed values against the receipt photo.
* Any misread values (e.g., incorrect price, wrong gallon count, misidentified station, or wrong odometer reading) can be manually corrected by the user.

### 2. Ground-Truth Data Preservation
* When a user saves or confirms a candidate, `CandidateMapper` converts the `FuelPurchaseCandidate` into a permanent `VehicleEvent`.
* The system preserves the raw `ocrText`, confidence scores, and original photo references alongside the user's corrected values.

### 3. Continuous Model Improvement Pathway
* By maintaining both the raw input (`ocrText` & photo) and verified user output (`VehicleEvent`), the app maintains a labelled ground-truth dataset.
* This dataset allows for:
  * Tuning heuristic regex patterns based on frequent misread edge cases.
  * Feeding feedback pairs (raw OCR -> user correction) into cloud models like Gemini API for fine-tuning or prompt refinement.
