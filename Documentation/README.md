# Vehicle Log AI

An Android app that scans your MediaStore for fuel-receipt photos, runs on-device
OCR + receipt parsing, and lets you review, correct, and persist the resulting
vehicle events.

## Stack
- Kotlin + Jetpack Compose (Material 3)
- Room (entities: `vehicles`, `events`, `review_items`, `scanned_photos`)
- ML Kit Text Recognition v2 for OCR
- Kotlin Coroutines + Flow
- Manual dependency injection via the `Application` class

## Project layout
- `app/src/main/java/com/schortgen/vehiclelogai`
  - `data/` — Room entities, DAOs, repositories.
  - `service/` — `PhotoScannerService`, `MlKitOcrService`, `ReceiptParserService`, `CandidateMapper`.
  - `ui/` — Compose screens, organised by feature (`dashboard`, `vehicles`, `fuel`, `reviewqueue`, `reviewdetail`, `eventdetail`, `vehicle`, `debug`).
  - `debug/` — `DiagnosticLogger` + `DiagnosticsViewModel`. No-op in release builds.
  - `navigation/` — `Screen` sealed class + `NavGraph`.
- `docs/BETA_TESTING_CHECKLIST.md` — beta test procedure.
- `docs/database-schema.md` — Room schema reference.

## Build
```
gradlew assembleDebug
```

## Debug screen
Long-press the *Vehicle Log AI* welcome card on the Dashboard (debug builds only)
to open the hidden **Debug** screen. It shows:
- App version, version code, and database schema version
- Row counts for vehicles, events, review items, pending reviews, scanned photos
- MediaStore scan statistics (candidates, imported, run count)
- OCR processing statistics (successes, failures, average time)
- Receipt parser success rate
- Buttons to seed sample data, clear test data, and export diagnostic logs
- A live tail of the last log lines

In release builds, the long-press is a no-op, the `debug` route is never
registered, and `DiagnosticLogger` is fully disabled.
