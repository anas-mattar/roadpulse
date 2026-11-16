# Jalanna on iPhone

Status (15 November 2026): **step 7 of the iPhone app**: everything from step 5 (map, search, routes,
turn-by-turn with voice, reports, places, tolls, traffic, drive alerts, faster route) plus saved
places (Home, Work, favourites, recent), planned drives with "time to leave" notifications,
profile and points, share my drive, report details (and how long for a closure), "still there?"
after passing a report, and privacy (traffic help opt-in, privacy policy, export and delete my
data, clear saved places), and step 7: route options, map and speed settings, stops, parking,
overview and search by voice, plus the Waze-style route list, drive sheet, share preview and menu. Steps 1-8 built green on GitHub; the app has not been run on a phone yet.

## How the two apps share code

- `shared/` (Kotlin Multiplatform): the logic both apps use. Today: route following and matching,
  turn wording in English, Malay and Arabic, all screen texts, lanes, report rules, traffic delay,
  traffic segments, opening hours, toll/route option models. It is compiled for Android and for
  iPhone (`./gradlew :shared:compileKotlinIosArm64` works on Windows/Linux too).
- `app/`: the Android app (screens, map, GPS, voice, storage, networking).
- `iosApp/`: the iPhone app in Swift (SwiftUI). Step 1 only shows shared texts in three languages,
  to prove the pipeline. The `.xcodeproj` is generated from `iosApp/project.yml` by XcodeGen.

## Building without a Mac

1. Copy `ci/ios-build.yml` to `.github/workflows/ios-build.yml` and push to GitHub.
2. On GitHub: **Actions > iOS build > Run workflow**. When it is green, download the
   **RoadPulse-unsigned-ipa** artifact (a zip with `RoadPulse-unsigned.ipa`).
3. If it is red, send the log of the failed step: the iPhone code cannot be compiled anywhere else.

GitHub's Mac machines are free for public repositories; private repositories have a monthly
allowance that Mac builds use up faster. Check your plan before running it often.

## Installing on an iPhone with a free Apple ID

Apple only installs signed apps. With a free Apple ID and no Mac, the usual route is a third-party
Windows tool that signs the `.ipa` with your Apple ID and installs it over the cable (for example
Sideloadly or AltStore). Not verified by this project. Limits of a free Apple ID: the app stops
working after 7 days and must be installed again, and only a few apps at a time. The paid Apple
Developer Program (USD 99 per year) removes these limits and allows TestFlight.

## Reaching the laptop's servers from an iPhone

The Android USB trick (`connect-phone.bat`) does not exist for iPhone, so the iPhone uses Wi-Fi.
Without this, the iPhone app uses public routing and search only (no reports, places, tolls or
traffic).

1. Laptop and iPhone on the same Wi-Fi, set to **Private** in Windows.
2. Start the servers as usual (`docker compose up -d` in `backend`).
3. Once, in PowerShell as administrator:
   `powershell -ExecutionPolicy Bypass -File backend\open-iphone-access.ps1`.
   It opens ports 8002, 8080 and 8090 for private networks only and prints the laptop address.
   `-Remove` closes them again.
4. iPhone app > Settings (gear) > **Use my laptop's server**, type the address (e.g. `192.168.1.20`),
   then **Test connection**. iOS asks once to allow "local network": tap Allow.
5. Optional **Maps from my laptop**: start the tile server open to Wi-Fi with
   `$env:TILES_BIND="0.0.0.0"; docker compose --profile maps up -d` (otherwise it stays on
   `127.0.0.1`).

The connection is plain HTTP inside your home network (allowed only for local addresses). Anyone
on the same Wi-Fi could reach these ports; use `-Remove` on networks you do not trust.

## Plan (each step ends with a green build and something to try)

1. **Done in code:** shared module; Android uses it; iPhone skeleton and build workflow.
2. **Done in code:** networking in `shared` (routing, search, places, tolls, profile, share, traffic,
   privacy) with OkHttp on Android and URLSession on iPhone; the iPhone app searches places and
   shows route choices (from Kuala Lumpur city centre, public servers).
   Still to move: live reports (WebSocket) and the drive session.
3. **Done in code:** map, search and route preview (MapLibre, CoreLocation); the drive session is
   in `shared` and both apps use it; iPhone turn-by-turn with voice, lanes, speed, rerouting,
   background location, and a simulated drive (play button).
4. **Done in code:** lanes and the drive screen on iPhone.
5. **Done in code:** laptop server over Wi-Fi (setting + firewall script); reports (send, vote,
   map pins), places by category, tolls on route cards, traffic layer, camera/school/report
   alerts, traffic delay and faster route.
6. **Done in code:** saved places and planned drives (same `PlacesStore` as Android, kept on the
   phone; reminders are local notifications), profile, share my drive (`ShareSession` now shared),
   report details and closure time, "still there?", privacy screen and traffic help (opt-in, same
   wording and rules as Android, ADR 0004).
7. **Done in code:** route options (vehicle; avoid tolls, highways, ferries; unpaved), map colours
   (night map after sunset), north up, compass, flat or tilted driving map, speedometer and
   speed-limit options, press and hold the map (drive here, add a stop, parked here, save),
   stops on the preview and during a drive, route overview, and search by voice (Apple speech
   recognition, asks for the microphone the first time).
8. **Done in code (both apps):** route list view with traffic bars, drive sheet with other routes,
   traffic colours on the route line, next-turn label, share preview and viewer count, menu and
   grouped settings.
9. Not possible the same way on iPhone: music controls for other apps (iOS only allows Apple
   Music control), and background limits differ. These will be adapted, not copied.
