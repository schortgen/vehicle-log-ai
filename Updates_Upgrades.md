# Updates & Upgrades

- [x] **Vehicles screen**: Clicking on *Vehicles* loads the vehicles tab but shows a duplicate bottom navigation bar (second instance). Fix the duplicate NavBar.
- [x] **Unnamed vehicle titles**: When a vehicle does not have a custom name, display `year make model` as the title/name.
- [x] **Review queue photo preview**: Opening a picture in the Review Queue should display a preview of the photo; currently no preview is shown.
- [x] **Review photo zoom**: Photo preview is often overly zoomed; ensure the image fits within its container (e.g., use `ContentScale.Fit`).
- [ ] **Review queue relevance**: Ensure only relevant images appear in the review queue (filter out unrelated scans).
- [x] **Group multiple photos to one event**: Photos taken within 15 minutes of each other are automatically clustered into a single grouped event. The Review Detail screen shows a scrollable photo carousel, runs OCR on all photos, and saves as a single fuel record.
