# Database Schema

Room database: `vehicle_log_database`, current schema version: **6**.

## Tables

### `vehicles`
| column            | type     | notes                                  |
|-------------------|----------|----------------------------------------|
| `id`              | INTEGER  | primary key, auto-generate             |
| `nickname`        | TEXT     | optional, e.g. "Daily Driver"          |
| `year`            | INTEGER  | optional                               |
| `make`            | TEXT     | optional                               |
| `model`           | TEXT     | optional                               |
| `licensePlate`    | TEXT     | optional                               |
| `vin`             | TEXT     | optional                               |
| `currentMileage`  | INTEGER  | optional                               |
| `isActive`        | INTEGER  | boolean, default 1                     |
| `createdDate`     | INTEGER  | epoch millis                           |

### `events`
| column            | type     | notes                                  |
|-------------------|----------|----------------------------------------|
| `id`              | INTEGER  | primary key, auto-generate             |
| `vehicleId`       | INTEGER  | FK -> vehicles.id, ON DELETE CASCADE   |
| `eventType`       | TEXT     | one of `EventType` (FUEL, MAINTENANCE, MILEAGE, INSPECTION, REGISTRATION, TIRE_ROTATION) |
| `eventDate`       | INTEGER  | epoch millis                           |
| `createdDate`     | INTEGER  | epoch millis                           |
| `confidence`      | REAL     | optional, 0.0 - 1.0                    |
| `verified`        | INTEGER  | boolean                                |
| `notes`           | TEXT     | optional                               |
| `odometer`        | INTEGER  | optional (fuel events)                 |
| `gallons`         | REAL     | optional (fuel events)                 |
| `pricePerGallon`  | REAL     | optional (fuel events)                 |
| `totalCost`       | REAL     | optional (fuel events)                 |
| `location`        | TEXT     | optional                               |
| `photoPath`       | TEXT     | optional URI                           |

Indices: `idx_event_vehicleId`, `idx_event_eventDate`, `idx_event_eventType`.

### `review_items`
| column              | type    | notes                                  |
|---------------------|---------|----------------------------------------|
| `id`                | INTEGER | primary key, auto-generate             |
| `photoPath`         | TEXT    | URI                                    |
| `captureDate`       | INTEGER | epoch millis                           |
| `vehicleId`         | INTEGER | optional FK                            |
| `eventId`           | INTEGER | optional FK                            |
| `status`            | TEXT    | one of `ProcessingStatus`              |
| `reason`            | TEXT    | optional, free-form description        |
| `confidence`        | REAL    | optional, 0.0 - 1.0                    |
| `ocrText`           | TEXT    | optional, raw OCR output               |
| `ocrProcessingTimeMs` | INTEGER | optional, milliseconds               |
| `parsedData`        | TEXT    | optional, JSON of `FuelPurchaseCandidate` |
| `createdDate`       | INTEGER | epoch millis                           |

### `scanned_photos`
| column          | type    | notes                                  |
|-----------------|---------|----------------------------------------|
| `mediaStoreId`  | INTEGER | primary key (MediaStore _ID)           |
| `uri`           | TEXT    | content URI                            |
| `displayName`   | TEXT    | file display name                      |
| `importedDate`  | INTEGER | epoch millis                           |

## Migrations
- `MIGRATION_5_6` creates performance indices on `events` and `review_items`.

## Diagnostics
Counts and aggregated statistics are surfaced through the hidden Debug screen
(debug builds only). See `docs/BETA_TESTING_CHECKLIST.md` for the test plan.
