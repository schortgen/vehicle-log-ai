# Vehicle Log AI – Roadmap

## Current sprint
- [x] Lightweight diagnostic logging (`DiagnosticLogger`)
- [x] Hidden Debug screen (debug builds only) with seed/clear/export actions
- [x] Beta testing checklist (`docs/BETA_TESTING_CHECKLIST.md`)

## Up next
- [ ] Promote DiagnosticLogger counters into DataStore preferences so they survive
      process restarts.
- [ ] Wire ML Kit recognizer init failures into the logger.
- [ ] Add a "Force MediaStore rescan" button to the Debug screen.
- [ ] Add SQL-level diagnostic queries (e.g. last 10 events, last 10 review items)
      to the Debug screen.

## Future
- [ ] Replace placeholder Timeline screen with the proper timeline UI.
- [ ] Add cloud backup of diagnostic logs for crash triage.
- [ ] Add an opt-in telemetry channel.
