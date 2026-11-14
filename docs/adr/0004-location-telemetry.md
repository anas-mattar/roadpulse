# 0004. Location telemetry for live traffic

Date: 2026-11-14 · Status: Accepted for local testing on 2026-11-15 (owner's decision). A PDPA legal
review, privacy policy and consent screen review are still required before any real users opt in.

## Context

Phase 3 (live traffic) needs speed measurements from drivers. Plan section E: GPS traces and
speed samples can identify people even without account IDs; telemetry must be opt-in, minimised,
short-lived, and reviewed for Malaysia's PDPA (and GDPR if offered in the EU).

**Today the app collects no location data on any server.** Debug drive logs stay on the device.

## Decision (proposed)

- **Opt-in only**, asked separately from the location permission, with a plain explanation and an
  off switch in settings. No telemetry from users who have not opted in, and none in the beta until
  this record is accepted.
- **No trip-level identity**: the app sends short segments (about 2 minutes) under a random ID that
  changes every segment; no account or device ID is attached.
- **Trim the ends**: nothing is sent for the first and last ~500 m of each drive, so home and work
  addresses are not revealed.
- **Process, then discard**: the server map-matches each segment to road edges immediately, keeps
  only aggregated speeds per road edge and 5-minute window, and deletes the raw points within
  24 hours (target: immediately after matching).
- **Aggregation threshold**: a road speed is used or shown only when at least 3 different segments
  contributed in the window, so a single driver's speed is not exposed.
- **Retention**: aggregated speeds kept up to 12 months for historical profiles; no raw traces kept.
- **Security**: TLS, write-only ingestion endpoint, rate limits, and no precise locations in logs.

## Implementation (2026-11-15)

- App: `nav/TrafficUploader.kt` (`TrafficSegmenter`): off by default (Settings > "Help with live
  traffic"); 2-minute segments, one point per 2 s, times relative to the segment start, coordinates
  rounded to 5 decimals; first and last 500 m dropped; simulated drives never uploaded; failed
  uploads dropped (no queue on the phone). No token or device id is sent.
- Backend: `Modules/Traffic`: `POST /v1/traffic/samples` (anonymous, rate limited per IP), map
  matched at once on the local Valhalla (`trace_attributes`), added to `traffic_observations`
  (edge, 5-minute window, speed sum, segment count); points are not stored or logged.
  `GET /v1/traffic` returns only edges with at least 3 segments in the last 15 minutes. Aggregates
  are deleted after 12 months.
- Known gap: one driver stuck on the same road for several minutes sends several segments, so the
  3-segment threshold alone does not guarantee 3 different people. Review before real users.

## Consequences

- Fewer data points than full traces: map matching quality and traffic coverage must be validated
  in Gate 3 before showing a traffic layer.
- Needs a privacy policy, consent screens, a data-deletion route, and a legal review of PDPA
  applicability before acceptance.
- With few users, traffic data will be thin; historical profiles or a licensed feed may be needed
  first (plan Phase 3, cold start).
