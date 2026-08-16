# Google Play Data Safety baseline — MathCrossword

Baseline date: 2026-08-16

This document records the intended Play Console answers for the current `play` distribution. It is an engineering/compliance checklist, not legal advice. Re-check it whenever permissions, SDKs, telemetry, accounts, networking, analytics, ads, or research synchronization change.

## Current Play-build facts

- `applicationId`: `com.offline.mathcrossword`
- target SDK: 36
- no advertising SDK
- no account/login system
- no automatic research or analytics upload
- no `INTERNET` permission in the Play flavor
- no `REQUEST_INSTALL_PACKAGES` permission in the Play flavor
- gameplay progress and session telemetry are stored locally
- research export is created only after an explicit user action
- Android's document/share UI lets the user choose the destination/recipient
- the export includes local puzzle/session telemetry and a random installation participant ID
- the export does not intentionally include Google account identifiers, advertising ID, contacts, phone number, or location

## Proposed Data safety answers for the current Play flavor

### Does the app collect or share any of the required user data types?

**Proposed answer: No.**

Reasoning:

1. Local-only access/storage is not treated as collection for the Data safety section when the data is not transmitted off-device by the app.
2. The current Play build has no automatic telemetry upload.
3. Research export/share is a specific user-initiated action in which the user chooses the destination. Google Play's Data safety guidance lists user-initiated transfers where sharing is reasonably expected as an exception that does not need to be disclosed as data sharing.

This answer must be changed if the app later adds automatic or optional background/server synchronization, cloud backup controlled by the app, third-party analytics, crash-reporting SDKs that transmit telemetry, ads, accounts, or any other off-device transmission performed by the app/SDKs.

## Data types handled locally

These are still described in the privacy policy even though they are not currently declared as Play Data safety "collection":

- app activity / gameplay interaction data
- puzzle/session identifiers and seeds
- timestamps and active-time measurements
- placements, candidate actions, undo, hints, reset/navigation interaction
- generator/solver versions and puzzle structure metrics
- HumanSolver route/model comparisons
- derived difficulty/research statistics
- random installation participant ID used only in a manually created research export

## Research export

The export is intentionally user-controlled:

`Analysis -> Export research data -> Android document picker -> optional Android share sheet`

The app does not choose a recipient and does not silently upload the file.

If a future version adds a `Research Sync` switch that sends exports to a developer-controlled endpoint, the Data safety form must be revised before release. At minimum, the relevant app-activity / identifiers data types, optional collection status, purpose, retention/deletion, and encryption-in-transit answers will need review.

## Privacy policy requirements before Play submission

Before production/closed-test submission:

- provide a public, active, non-geofenced privacy-policy URL (not a PDF)
- make the privacy policy accessible inside the app as text or a link
- ensure the publisher/developer identity in the policy matches the Google Play listing
- provide a privacy contact mechanism appropriate for the final developer account
- keep retention/deletion language consistent with actual behavior
- keep the Play Data safety form synchronized with this document and the released binary

## Release gate idea

Future CI should verify the merged `playRelease` manifest does not contain forbidden/unexpected permissions and should fail if new dependencies/permissions are introduced without a Data safety review.
