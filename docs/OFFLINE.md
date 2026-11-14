# RoadPulse: behaviour without network or GPS

Status: describes the code as of step 4 (November 2026). Verified by unit tests and by simulated
drives on the emulator where noted; **not yet field-tested on a real phone**. Update this file
whenever the behaviour changes.

Plan section B separates three capabilities. Today none of them is available fully offline.

| Capability | No network before starting | Network lost during a drive |
|---|---|---|
| Map display | Shows only areas already viewed (MapLibre's tile cache); other areas stay blank | Same: areas along the route that were never viewed may be blank |
| Place search | Fails with an error message | Not used while driving |
| Route calculation | Fails: a drive cannot be started | Guidance continues on the current route |
| Rerouting | n/a | Each attempt gives up after at most 12 s, and position tracking on the current route continues meanwhile. On failure the current route is kept, the banner shows "Offline: keeping your current route", the app says "No internet connection" once, and it retries at most every 10 s while off route. The message clears when a reroute succeeds or the driver is back on the route |
| Voice prompts | n/a | Continue (Android text-to-speech, as long as the device has an offline voice installed) |
| Position (GPS) | Works without network; network-based location does not | Same |

## GPS loss (tunnels, car parks, GPS switched off)

- After 6 s without a usable fix (accuracy better than 75 m), the banner shows "GPS signal lost:
  keep following the road" and the app says so once. Guidance and the route are kept.
- Fixes worse than 75 m accuracy are ignored: they neither move progress nor count towards
  rerouting.
- When fixes return, matching resumes from the last known position (the search window grows
  with time, so a longer tunnel is fine) and the banner clears.

## App stopped by Android during a drive

- The active drive (route and destination) is saved to app storage when it starts and after each
  reroute, and deleted on arrival or Stop. Simulated drives are not saved.
- If Android stops the app, the navigation service asks to be restarted. When the system allows
  it, guidance continues in the background. Android can refuse background restarts; then the drive
  resumes the next time the app is opened ("Resumed your drive to …").
- A resumed drive finds the driver anywhere along the saved route on the first fix. Saved drives
  older than 1 hour are discarded.
- Not covered: the device rebooting mid-drive (the drive resumes only when the app is opened).

## Known gaps (candidates for later work)

- No offline map download: tiles along a route are not pre-fetched.
- No offline search or offline route calculation (would need on-device geocoding and routing
  data, such as a local Valhalla tile set). Post-MVP per the plan.
- Reports are stored on the device only until the Phase 2 backend exists.
