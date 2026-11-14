# 0005. Map data pipeline: weekly OpenStreetMap refresh

Date: 2026-11-14 · Status: Accepted

## Context

Routing (Valhalla), search (Photon) and map tiles all come from OpenStreetMap. Today they come from
public demo servers. Plan section C asks for a defined ingestion and update pipeline with refresh
frequency, rebuilds, rollback and attribution.

## Decision

- **Source**: the Geofabrik `malaysia-singapore-brunei` extract, downloaded weekly with checksum
  verification.
- **Weekly build on the maps server** (scheduled job, off-peak):
  1. Valhalla routing tiles (including admin and timezone data) built into a new folder.
  2. Photon search index rebuilt for the same extract.
  3. Vector tiles built with Planetiler into a single PMTiles file (OpenMapTiles schema, so
     existing MapLibre styles work), plus our own day and night styles.
- **Smoke tests before switching**: a fixed set of Klang Valley routes must return routes of
  similar length and time to last week; known places must be found by search; tiles must
  render at a few zoom levels. Failure keeps the old data and alerts the team.
- **Blue/green switch**: services point to the new build only after the tests pass; the previous
  build is kept for one-step rollback.
- **Attribution and licence**: "© OpenStreetMap contributors" (ODbL) stays visible in the app; tile
  and style credits are kept. If we ever publish a modified database (not just tiles or routes),
  the share-alike terms apply and need review. Errors found in testing are fixed in OpenStreetMap
  itself, not patched locally.

## Consequences

- Map changes reach users within about a week.
- The maps server needs enough memory for the builds (Valhalla and Planetiler for this extract run
  comfortably on 8–16 GB; confirm when sizing the server).
- One weekly job to monitor; the smoke tests double as a routing regression suite.
