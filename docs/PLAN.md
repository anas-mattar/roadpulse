# RoadPulse: architecture and roadmap

## Goal

A community-driven navigation app with the feature set drivers expect from the category:
turn-by-turn navigation, real-time community reports, traffic-aware routing and ETAs, and
accounts with social features. Everything is built from scratch on open data and open-source
components. Nothing is copied or decompiled from an existing product: features are
reimplemented from their publicly observable behaviour, with our own code, design, name,
icons, sounds, and servers.

## Architecture

    Android app (Kotlin, Compose, MapLibre)
        |  HTTPS / WebSocket
    API gateway (Ktor or Spring Boot; auth, rate limiting)
        |-- Reports service ----- PostGIS (reports, votes, users)
        |-- Traffic service ----- speed samples -> per-segment speeds -> Valhalla traffic tiles
        |-- Routing ------------- Valhalla (OSM routing tiles + live traffic)
        |-- Search -------------- Photon (OSM geocoder)
        |-- Map tiles ----------- Planetiler/OpenMapTiles -> PMTiles on object storage + CDN
        |-- Realtime ------------ WebSocket hub + Redis pub/sub, keyed by map tile
        |-- Push ---------------- Firebase Cloud Messaging

| Concern        | Choice                                   | Why                                              |
|----------------|------------------------------------------|--------------------------------------------------|
| Map rendering  | MapLibre Native Android                  | Open source, vector tiles, no per-load fees      |
| Map data       | OpenStreetMap                            | Free, editable, good coverage in Malaysia        |
| Routing        | Valhalla                                 | Turn-by-turn narrative, live-traffic support, map matching |
| Geocoding      | Photon                                   | Search-as-you-type on OSM data                   |
| Database       | PostgreSQL + PostGIS                     | Geo queries for reports near a route             |
| Auth           | Firebase Auth or Keycloak (OIDC)         | Google/Apple/phone sign-in without building it   |
| Voice          | Android TextToSpeech                     | Offline, multilingual (EN/BM/AR)                 |

## Roadmap

### Phase 1: Map and navigation (scaffold done; 4-6 weeks to harden)
- Done: map, location, search, routing, route display, follow mode, rerouting, voice
  prompts, distance-to-next-turn, live ETA, background navigation (foreground service).
- Next: snap the position to the route and use heading to avoid false matches on parallel
  roads; maneuver icons; alternative routes; day/night map styles; speedometer and
  speed-limit display (OSM maxspeed); recent and saved places (Room); voice language
  selection (EN/BM/AR) and mute; audio focus so prompts duck music.

### Phase 2: Community reports (4-5 weeks)
- Backend API: POST /reports, POST /reports/{id}/vote, GET /reports?bbox=.
- WebSocket subscription by map tile so new reports appear live.
- Report lifecycle: each type has a time-to-live, extended by "still there" votes and
  shortened by "not there" votes. Reputation weighting to resist spam.
- In-drive alerts: "Hazard reported ahead in 500 m" with one-tap confirm/dismiss.
- App: replace InMemoryReportRepository with the API-backed implementation.

### Phase 3: Live traffic and ETA (6-8 weeks, the hardest part)
- App uploads anonymised speed samples while driving (opt-in, batching, no user id stored).
- Server map-matches samples to road segments (Valhalla trace_attributes), aggregates
  rolling speeds per segment, and writes Valhalla live-traffic tiles every 1-2 minutes.
- Traffic layer on the map (green/orange/red), jam detection that auto-creates jam reports,
  periodic re-check for faster routes during a drive.
- Cold start: with few users there is little traffic data. Options are historical speed
  profiles first, or licensing a commercial traffic feed (TomTom, HERE) until volume grows.

### Phase 4: Accounts and social (4-5 weeks)
- Sign-in, profile, points and ranks for reports and kilometres driven.
- Friends, share-my-drive link with live ETA, planned drives with departure reminders.

### Phase 5: Launch readiness
- Own tile hosting, monitoring, load tests, abuse controls.
- Privacy: location data is personal data under Malaysia's PDPA (and GDPR if offered in the
  EU). Needs consent flows, retention limits, data deletion, and a privacy policy.
- Play Store: background-location declaration, Android Auto support (driving-app category).
- iOS app (MapLibre Native iOS; the backend is shared).

## Main risks

1. Traffic data cold start: the product's value depends on user density. Launch in one metro
   area first (e.g. Klang Valley) rather than nationwide.
2. Map data quality: OSM turn restrictions and speed limits vary; budget for map fixes
   contributed back to OSM.
3. Operating cost: routing and tile servers for a country are modest (one or two VMs);
   worldwide coverage needs roughly 64 GB+ RAM machines for tile builds.
4. Legal: use only original names, icons, and sounds; keep OSM attribution visible; comply
   with the ODbL share-alike terms for any derived map database.

---

## Implementation guidance for Claude Code (added November 2026)

**Project scope:** RoadPulse is an independent, public, consumer navigation application, initially targeting Android and a geographically limited beta (e.g. Klang Valley). It is **not** an internal fleet-management product. Preserve the original goal and phases above.

**Source-of-truth rule:** The "Done" items in Phase 1 are claims in this plan, not independently verified implementations. Before changing code, inspect the repository, build configuration, tests, APIs and actual behavior. Report each feature as `verified working`, `implemented but unverified`, `partial`, or `missing`. Do not rewrite working features without a specific defect or approved refactor. Do not treat time estimates as commitments.

### A. Navigation accuracy and reliability (Phase 1 — P0)

- Implement route-aware map matching: snap GPS fixes to plausible route segments, using heading, accuracy, speed, proximity, and continuity; do not snap across divided or parallel roads solely by distance.
- Handle GPS drift, tunnels, overpasses, ramps, U-turns, low-speed movement and temporary loss of signal. Clearly distinguish raw location from matched display position.
- Define off-route detection with distance/time hysteresis and cooldown to prevent repeated reroutes; preserve the active route until a replacement is valid.
- Complete maneuver icons, alternative routes, day/night styles, speedometer, speed limits where OSM data is present, saved/recent places, voice language selection and mute, and Android audio focus.
- Validate foreground navigation, permissions and notification behavior on supported Android versions, including screen-off navigation and process interruption. Do not assume the foreground service guarantees continuous location in every state.
- **Acceptance:** field-test a documented route set covering urban streets, parallel roads, highway exits, tunnels and poor GPS conditions; record incorrect turn prompts, false reroutes, lost navigation sessions and battery consumption. Agree measurable thresholds from pilot baselines before marking complete.

### B. Offline and poor-network behavior (Phase 1 design; staged delivery)

- Separate *offline map display* from *offline search* and *offline route calculation*: these are distinct capabilities and must be specified and tested independently.
- Define downloadable regions, storage estimates, update/version policy, integrity checks and disk-space management for map data; assess MapLibre-compatible offline tiles and the licensing/attribution requirements.
- For the initial release, retain an active route and cached essential guidance when connectivity drops where technically feasible; queue permitted telemetry/reports for later delivery without duplicates.
- Do not claim full offline rerouting unless a local routing engine and suitable routing data are actually implemented and tested. Full offline routing may remain post-MVP.
- **Acceptance:** test airplane mode before route creation and mid-trip, intermittent connectivity, stale map data and low storage; document exactly what continues to work.

### C. Backend architecture and infrastructure (Phase 2 foundation — P0)

- **Decision pending:** original architecture proposes Ktor or Spring Boot. Select one based on team familiarity, library compatibility, deployment needs and maintenance; document the decision in an ADR. ASP.NET Core is an alternative only if explicitly selected after review, not an automatic replacement.
- Prefer a **modular monolith** for reports, user profiles, moderation and business APIs initially. Keep Valhalla, Photon and map-tile generation/hosting as separate specialized processes. Split application services only after measurable operational need.
- Define API contracts (OpenAPI), authentication/authorization, input validation, pagination, idempotent writes, rate limits, error schemas and versioning. Separate public read APIs from authenticated write/moderation APIs.
- Use PostgreSQL/PostGIS for geo queries; document spatial indexes, report TTL/expiry, retention and migration strategy. Use Redis/WebSockets only where needed, with reconnection, backpressure and duplicate-event handling.
- Define map/OSM ingestion and update pipeline: data extracts, refresh frequency, tile builds, routing tile rebuilds, geocoder index refresh, rollback and attribution. Verify dataset licensing and third-party service usage policies before deployment.
- Start with reproducible local Docker-based development and simple production hosting; Kubernetes is optional when justified by load and operational capacity.
- **Acceptance:** a new developer can launch the documented stack, run migrations and integration tests, and deploy/rollback a staging release using documented steps.

### D. Testing, observability and performance (cross-cutting — P0)

- Add automated unit, integration and end-to-end tests for routing API integration, reports/votes/expiry, authentication, network recovery and migration safety.
- Create a reproducible navigation field-test matrix for GPS quality, highway ramps, tunnels, city canyons, screen-off sessions, background restrictions, battery, thermal behavior and different Android devices.
- Track route-completion rate, wrong-turn/false-reroute events, location accuracy, navigation crashes/ANRs, search/routing latency, ETA error against actual arrival, and battery use per hour. Define sampling and privacy safeguards.
- Instrument backend requests and queues with structured logs, metrics, traces and alerts (e.g. OpenTelemetry, Prometheus/Grafana). Do not log precise location, access tokens or identifiable trip traces by default.
- Test concurrency, tile CDN cache behavior, WebSocket fan-out and routing throughput with realistic load profiles before public release.
- **Acceptance:** staging dashboards and alerts exist; critical workflows have passing tests; performance baselines and release thresholds are documented and reviewed.

### E. Privacy, security and abuse prevention (before public beta — P0)

- Treat raw GPS trails and speed samples as potentially identifiable even without account IDs. Document data flow, purpose, lawful basis/consent, retention, deletion and access controls; seek jurisdiction-specific legal review for PDPA/GDPR applicability.
- Make traffic telemetry opt-in with an understandable permission flow; minimize granularity and retention, avoid storing unnecessary raw tracks, and evaluate aggregation thresholds against re-identification risks.
- Protect transport with TLS, authenticate write operations, enforce least privilege, rotate secrets, validate input, apply rate limiting and audit privileged moderation actions.
- Implement report abuse controls: per-user/device rate limits, duplicate/spatial-temporal checks, reputation signals, moderation queue, expiry and a way to dispute/remove harmful reports. Do not depend solely on reputation scores.
- Provide account deletion, data export/deletion handling where required, clear privacy policy, and an incident response/backup and restore procedure.
- Review Google Play location/background access and data safety requirements using current official documentation at release time. Keep map attribution visible and review ODbL obligations for derived databases.
- **Acceptance:** threat model and privacy checklist reviewed; abuse tests pass; consent withdrawal and deletion are tested end-to-end; no sensitive data appears in logs.

### F. Release gates and revised delivery order

**Gate 1 — Navigation MVP (finish Phase 1):** navigation reliability verified on real drives; routing/search/voice/background behavior tested; known limitations and poor-network behavior documented. No public traffic-quality promises.

**Gate 2 — Community beta (Phase 2 + essential Phase 4 account features):** minimal sign-in or appropriate anonymous identity design, report creation/voting/expiry, moderation, abuse controls, privacy controls, live updates and staged backend monitoring. Defer friends, ranks and other social features until core reports prove useful.

**Gate 3 — Traffic experiment (Phase 3):** collect speed samples only from opted-in users; validate map matching, aggregation coverage and privacy thresholds. Compare estimated speeds/ETA against measured trips. Use historical or licensed traffic feeds only after checking coverage, cost and redistribution rights. Do not launch a traffic layer without sufficient data quality.

**Gate 4 — Public Google Play release (Phase 5):** Play policy and legal review complete; crash/ANR, battery, routing reliability, security, moderation, backup/restore, load testing and support processes meet explicitly documented thresholds. Start in one metro area, monitor and expand gradually.

**Post-launch / optional:** full offline routing, iOS, Android Auto, richer social features and wider geographic coverage. Android Auto requires its own platform compliance and testing; do not treat it as a mandatory prerequisite for the first phone-only public launch.

### G. First actions for Claude Code

1. Audit the current repository against every Phase 1 "Done" and "Next" item; produce an evidence-based status table with relevant file paths, tests and gaps.
2. Identify the smallest safe set of P0 changes needed to reach Gate 1; provide dependencies, implementation sequence and risks.
3. Propose architecture decisions (ADRs) for backend framework, offline scope, location telemetry and map-data update pipeline; do not silently change the existing stack.
4. Implement one small, testable task at a time, running appropriate tests and reporting changed files, observed results and remaining issues.
5. Ask for confirmation before major refactors, introducing paid third-party services, collecting location telemetry or expanding the scope.

### Official technical references (verify current policies before release)

- Android foreground service/location: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android location permissions: https://developer.android.com/develop/sensors-and-location/location/permissions
- Google Play location policy: https://support.google.com/googleplay/android-developer/answer/9799150
- MapLibre: https://maplibre.org/
- Valhalla: https://valhalla.github.io/valhalla/
- PostGIS: https://postgis.net/documentation/
- OpenStreetMap copyright/attribution: https://www.openstreetmap.org/copyright
- OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/
