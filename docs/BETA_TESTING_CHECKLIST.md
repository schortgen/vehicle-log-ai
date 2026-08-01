# Vehicle Log AI – Beta Testing Checklist

This checklist is intended for beta testers, internal QA, and developers running field
trials. It is grouped by feature area and **must be completed in order**: infrastructure
checks first, then capture flow, then review flow, then long-term reliability.

A test is **PASS** if the observed behavior matches the expected result. Any deviation
should be captured in the *Notes* column and reported through the in-app **Debug →
Export diagnostic logs** flow. When `BuildConfig.DEBUG` is `true`, the Debug screen is
reachable from the Dashboard by long-pressing the "Vehicle Log AI" title.

Use the latest debug build whenever possible. Release builds hide the Debug screen and
the seed/clear buttons, so data-driven test cases below can only be run on debug builds.

---

## 0. Test Session Setup

- [ ] Confirm device is on the **debug** variant of the app (long-press the Dashboard
      title — a *Debug* entry should appear in the bottom sheet).
- [ ] Open **Debug** → **Clear test data** before starting a new session.
- [ ] Open **Debug** → **Seed sample data** if a populated database is required.
- [ ] Capture the **App version** and **DB schema version** from the Debug screen in
      your bug report.
- [ ] (Recommended) **Export diagnostic logs** to a file at the end of every session.

## 1. Cold Start & Application Lifecycle

- [ ] App launches within ~2 seconds on a mid-range device.
- [ ] No `ANR` or `RoomDatabase` initialization error in logcat on first launch.
- [ ] Force-stopping and relaunching the app preserves all data.
- [ ] `DiagnosticLogger` writes a startup line and the cold-start elapsed time to
      `getExternalFilesDir(null)/diagnostics/`.
- [ ] Background scan continues after the app is sent to the background.

## 2. Permissions

- [ ] First launch prompts for `READ_MEDIA_IMAGES` (Android 13+) or
      `READ_EXTERNAL_STORAGE` (≤ Android 12).
- [ ] Denying the permission surfaces a clear message and a retry path.
- [ ] Granting the permission re-enables the *Scan Photos* action.
- [ ] Revoking the permission from system settings shows the prompt again on next
      scan attempt.

## 3. Vehicle Management

- [ ] **Add Vehicle** screen accepts nickname, year, make, model, VIN, mileage.
- [ ] All numeric fields reject non-numeric input.
- [ ] Saving a valid vehicle returns to the previous screen and the new vehicle is
      visible in the list.
- [ ] Tapping a vehicle opens the **Vehicle Detail** screen.
- [ ] Editing and deleting a vehicle from the list updates the count on the dashboard.
- [ ] Deleting a vehicle with attached events cascades correctly (no orphans).

## 4. MediaStore Scan

- [ ] *Scan Photos* returns the expected number of new photos on a small library
      (< 50 images) and finishes in under 5 seconds.
- [ ] Screenshots and very small images are filtered out (count matches the Debug
      screen’s *Last scan: imported/total candidates*).
- [ ] Duplicate scans do not re-import the same MediaStore IDs.
- [ ] The Debug screen reflects each scan under *MediaStore stats*.
- [ ] When the library contains 0 candidates, the scan reports `0` and no review
      items are created.

## 5. Review Queue

- [ ] Newly imported photos appear with status **Pending**.
- [ ] Status filter chips switch between Pending / Processing / Needs Review / Complete.
- [ ] Tapping a card opens the **Review Detail** screen.
- [ ] Deleting a card removes it from the queue and the underlying photo is skipped on
      the next scan.
- [ ] The pending count on the Dashboard matches the Debug screen’s
      `Review items: PENDING`.

## 6. OCR & Receipt Parsing

- [ ] *Run OCR & Parse* on a clear fuel-receipt photo completes in < 3 seconds.
- [ ] Average OCR time on the Debug screen updates after each successful run.
- [ ] Receipt parser extracts station name, date, gallons, price/gallon, total cost,
      odometer for at least 8 out of 10 sample receipts.
- [ ] Parser **success rate** in the Debug screen matches the manual count.
- [ ] Low-quality photos set status to **Needs Review** and surface a confidence
      warning.
- [ ] OCR failures increment the *OCR failures* counter on the Debug screen.

## 7. Saving Events

- [ ] Saving a reviewed receipt creates an `Event` and removes the review item.
- [ ] Vehicle selection is required; missing selection surfaces a validation error.
- [ ] The event appears in the vehicle timeline and in the dashboard’s recent events.
- [ ] Dashboard aggregates (month-to-date fuel cost, year-to-date, average cost) match
      the underlying events.

## 8. Diagnostics (Debug builds only)

- [ ] Debug screen is reachable from the Dashboard (long-press title) and only in
      debug builds.
- [ ] All counts and statistics match a manual SQL/cursor query.
- [ ] **Seed sample data** populates vehicles, events, and review items.
- [ ] **Clear test data** removes only test-seeded records (user data, if any, is left
      alone or, in fully fresh installs, wipes the DB to zero rows).
- [ ] **Export diagnostic logs** writes a `diagnostics-YYYYMMDD-HHmmss.txt` file
      containing every log line, plus the JSON snapshot of the diagnostic screen.
- [ ] `DiagnosticLogger` does not log in release builds (no PII written to logcat
      when `BuildConfig.DEBUG == false`).

## 9. Performance & Stability

- [ ] No crashes after 20 consecutive photo scans.
- [ ] No memory growth > 50 MB across 50 review-detail opens (use Android Studio
      profiler if available).
- [ ] Long-press of dashboard title in release build does **not** open the Debug
      screen and does **not** log a navigation attempt.
- [ ] Database migration runs cleanly when the schema version is bumped in a future
      release.

## 10. Reporting a Bug

When filing a bug, attach:

- The exported diagnostic log (Debug → Export diagnostic logs).
- App version and DB schema version from the Debug screen.
- A short description of steps to reproduce, with expected vs. actual.
- Device model, Android version, and locale.

---

_Generated for Vehicle Log AI beta program. Update whenever a new feature ships._
