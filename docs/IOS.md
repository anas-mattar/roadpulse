# RoadPulse on iPhone

Status (15 November 2026): **step 3 of the iPhone app**: map with your position, place search and
route choices on the map. Steps 1-2 built green on GitHub; the app has not been run on a phone yet.

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

The Android USB trick (`connect-phone.bat`) does not exist for iPhone. The iPhone must reach the
laptop over Wi-Fi (same network, the laptop's address such as `http://192.168.1.20:8080`, and
Windows Firewall allowing ports 8002, 8080 and 8090 on the private network), or the servers must
be online. This is a change from "nothing opened to Wi-Fi" and will be an explicit setting.

## Plan (each step ends with a green build and something to try)

1. **Done in code:** shared module; Android uses it; iPhone skeleton and build workflow.
2. **Done in code:** networking in `shared` (routing, search, places, tolls, profile, share, traffic,
   privacy) with OkHttp on Android and URLSession on iPhone; the iPhone app searches places and
   shows route choices (from Kuala Lumpur city centre, public servers).
   Still to move: live reports (WebSocket) and the drive session.
3. **Map, search and route preview written** (MapLibre, CoreLocation). Next: turn-by-turn with
   voice and background navigation (needs the drive session in `shared`).
4. iPhone: reports, places, tolls, lanes, schools, cameras, traffic layer.
5. iPhone: settings, Arabic layout, saved places, planned drives, profile, share, privacy.
6. Not possible the same way on iPhone: music controls for other apps (iOS only allows Apple
   Music control), and background limits differ. These will be adapted, not copied.
