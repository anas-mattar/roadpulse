# 0003. Offline scope for the first public release

Date: 2026-11-14 · Status: Accepted

## Context

Plan section B separates offline map display, offline search and offline route calculation, and
says not to claim offline rerouting unless it is implemented and tested. Current behaviour is
documented in `docs/OFFLINE.md`.

## Decision

For the first public release (Gate 4):

- **Map display**: MapLibre's automatic tile cache only (areas already viewed). No downloadable
  regions yet.
- **Search and route calculation**: online only. The app says so clearly when offline.
- **During a drive**: keep guiding on the current route without network, keep voice prompts, and
  retry rerouting when the connection returns (already implemented and emulator-tested).

After launch, in this order, each with its own record:

1. Downloadable Klang Valley map region (MapLibre offline regions from our own PMTiles), with
   size estimate, update policy and storage management.
2. Pre-fetching map tiles along the active route.
3. On-device routing (for example an embedded Valhalla or GraphHopper build with a Malaysia
   routing graph): only if field data shows offline rerouting matters to users.

## Consequences

- No large map downloads or on-device routing data in the first release; app size stays small.
- Store listing and help text must not promise offline navigation.
- `docs/OFFLINE.md` stays the source of truth for what works without network.
