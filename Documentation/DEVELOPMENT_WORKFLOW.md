# Development Workflow

## Building
- `gradlew assembleDebug` — build a debug APK.
- `gradlew installDebug` — install on the connected device.
- `gradlew test` — run unit tests.

## Debug features
A `DiagnosticLogger` is wired into Application bootstrap, `PhotoScannerService`,
`MlKitOcrService`, `ReceiptParserService`, and the review/dashboard ViewModels. It
is a no-op in release builds.

A hidden `DebugScreen` is reachable in **debug builds** by long-pressing the
*Vehicle Log AI* welcome card on the Dashboard. The screen exposes database counts,
scan/OCR/parser statistics, and seed/clear/export actions.

To add a new log statement:
```kotlin
DiagnosticLogger.d("MyTag", "something happened")
```

To record a stat:
```kotlin
DiagnosticLogger.recordOcrSuccess(processingTimeMs)
```

## Beta testing
Follow `docs/BETA_TESTING_CHECKLIST.md` for the full procedure. Use *Debug → Export
diagnostic logs* to attach a log snapshot to a bug report.
