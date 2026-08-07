# Event Grouping Plan for Vehicle Log AI

## Overview
This document outlines the plan to automatically group photos into events based on temporal proximity (e.g., photos taken on the same date within 15 minutes of each other).

## Key Design Decisions
1. **Grouping Algorithm**
   - Cluster photos taken within a configurable time window (default 15 minutes)
   - Handle edge cases: midnight rollover, time zone differences, large photo sets
   - Use `captureDate` field from `ScannedPhoto`/`ReviewItem` as primary timestamp

2. **Data Relationships**
   - Add `eventId` foreign key to `ReviewItem` and `ScannedPhoto`
   - Create `Event` entity to store grouped event metadata
   - Index `captureDate` for performance

3. **Service Layer**
   - Implement `EventGroupingService` to handle clustering logic
   - Integrate with existing photo processing pipeline

4. **UI Integration**
   - Update `ReviewQueueViewModel` to display grouped events
   - Add event details screen for event management

## Implementation Checklist
- [ ] Review existing data models (`ScannedPhoto`, `Event`, `ReviewItem`)
- [ ] Define event grouping rules and edge cases
- [ ] Create `EventGroupingService` implementation
- [ ] Update database schema with `Event` entity and relationships
- [ ] Modify DAO/repository interfaces for event operations
- [ ] Implement grouping workflow in photo processing pipeline
- [ ] Update UI components to display events
- [ ] Write unit tests for grouping logic
- [ ] Document feature in README and code comments
- [ ] Perform integration testing
- [ ] Review code for architecture consistency
- [ ] Prepare release notes

## Notes
- Grouping threshold (15 minutes) should be configurable
- Data aggregation strategy needed for combined event metadata
- Existing `ReviewQueueViewModel` may require updates for event-level grouping