# AI / OCR Engine Notes

The app wires together a deterministic OCR + receipt-parsing pipeline. The whole
pipeline runs on-device with no cloud calls.

```
MediaStore -> PhotoScannerService -> review_items
                  |
                  v
           MlKitOcrService (Text Recognition v2)
                  |
                  v
          ReceiptParserService (regex + heuristics)
                  |
                  v
           FuelPurchaseCandidate (JSON in review_items.parsedData)
                  |
                  v
              CandidateMapper -> Event (FUEL)
```

## Where logging lives
- `com.schortgen.vehiclelogai.debug.DiagnosticLogger` is a debug-only logger.
  It records:
  - OCR success/failure counts and rolling average processing time
  - Receipt parser success/failure counts and computed success rate
  - MediaStore scan run count, candidate count, imported count
  - DB seed/clear counts

In release builds, all of the above is a no-op (the `log()` function early-returns
when `BuildConfig.DEBUG` is `false`).

## How to add new metrics
1. Add a counter to `DiagnosticLogger` (an `AtomicLong`).
2. Expose it through `DiagnosticStats`.
3. Increment it from the appropriate service call site.
4. Surface the new metric in `DebugScreen.kt`.
