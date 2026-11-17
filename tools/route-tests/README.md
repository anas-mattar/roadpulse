# Route test set

Twenty trips around the Klang Valley, run through the routing server and measured, so that changes
to how routes are chosen can be compared instead of guessed.

- `trips.csv`: the trips. The end points are approximate landmarks: replace them with trips you
  drive often. Fill `waze_minutes`, `waze_km` and `waze_via` with what Waze suggests for the same
  trip (same time of day each time) to compare against it.
- `profiles.json`: routing settings to compare. `current` is what the apps send today.
- `route_tests.py`: runs every trip with every profile and prints, per route: minutes and km (the
  server's estimate without traffic), km on small streets (residential and service roads), turns,
  U-turns, toll or not, and the road that carries most of the trip.
- `run-route-tests.ps1`: runs it with Docker (no Python needed on Windows).
- `baseline-2026-11-17.csv`: the first run, on the public routing server.

## What the first run showed (17 Nov 2026)

Two candidate settings were tried against `current`: higher penalties for turns, service roads,
gates and private roads (`calm_light`, `calmer`).

- 19 of 20 trips came out identical with all three settings.
- The one trip that changed (Taman Melawati to KLIA Terminal 1) got worse: 11 minutes and 7 km
  longer, to save 0.6 km of small streets.
- With the current settings the routes already use little of the small streets: 9 km out of 353 km
  in total, almost all of it at the start and end of trips.

So the penalties were **not** adopted: the apps keep the current settings. The candidates stay in
`profiles.json` as a record of what was tried.

What the run cannot tell: whether the chosen roads are the ones a local driver or Waze would
choose, and whether the time estimates are realistic. That needs the `waze_*` columns.
