# Changelog

All notable changes to this project will be documented in this file.

## v1.0.0-beta1 (2026-07-10)
- Initial beta release with core vehicle management, fuel tracking, OCR, receipt parser, dashboard, timeline, review queue, event editing and deletion.
- Established Room database schema version 6.
- Implemented basic navigation and UI scaffolding.

## v1.0.0-beta2 (2026-07-19)
- **Beta Hardening Sprint**
  - Added `EditVehicleScreen` with full edit functionality and validation rules.
  - Integrated navigation from `VehicleDetailScreen` to `EditVehicleScreen` and `ScanPhotosScreen`.
  - Verified debug screen is only available in debug builds and added gating logic.
  - Added lightweight diagnostic logging throughout the app (screen entry/exit, button clicks, DB operations).
  - Updated `PROJECT_STATUS.md` with current sprint information.
  - Fixed missing import for `EditVehicleScreen` in `NavGraph.kt`.
  - Cleaned up TODO/FIXME comments and ensured Gradle builds pass for both debug and release variants.
  - Updated documentation and README to reflect new features.

## Future Versions
- **v1.0.0-beta3** – Real‑world beta testing, performance optimizations, and additional feature polish.
