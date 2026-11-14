# RoadPulse Gate 1 field test plan

Purpose: verify navigation on real drives before Phase 2, as required by plan section A ("field-test
a documented route set covering urban streets, parallel roads, highway exits, tunnels and poor GPS
conditions; record incorrect turn prompts, false reroutes, lost navigation sessions and battery
consumption").

Everything up to now has been tested with unit tests and emulator simulations only.

**Status (14 Nov 2026): waiting for a real Android test phone. Gate 1 stays open until these drives
are done.**

## Safety first

- **Two people per test car.** The driver only drives. A passenger operates the phone, presses
  Flag issue, and takes notes.
- Mount the phone in a holder; never hold it while driving. Follow Malaysian road and phone laws.
- Never break traffic rules to follow a prompt. A wrong prompt is a finding: flag it and carry on safely.
- Do not do the "wrong turn" or "tunnel" routes in heavy rain or at peak hour.

## Equipment

- An Android phone (Android 8 or newer) with the **debug** APK (`apk/RoadPulse-debug.apk`).
  Only debug builds write drive logs and show the Flag issue button.
- Phone holder, car charger (but see battery test), and a laptop with this repository for afterwards.
- Ideally two phones from different makers (for example a Samsung and a Xiaomi): background
  restrictions differ between brands.

### Installing the debug app on a real phone

1. On the phone: Settings > About phone > tap "Build number" 7 times to enable Developer options.
2. Settings > System > Developer options > turn on "USB debugging".
3. Connect the phone to the laptop by USB and accept the "Allow USB debugging?" prompt.
4. On the laptop (cmd), with the emulator closed:
   ```
   set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
   %ADB% devices
   %ADB% install -r -g D:\solutions\waze\apk\RoadPulse-debug.apk
   ```
   `devices` should list the phone as "device". `-g` grants location permission.
5. On the phone, open RoadPulse once, and in the phone's Settings > Apps > RoadPulse > Battery,
   choose "Unrestricted" (Samsung: "Unrestricted"; Xiaomi: "No restrictions"). Note whether you did
   this: the screen-off test is run both ways.

Alternative without USB: copy the APK to the phone and open it (allow "Install unknown apps").

## Before each drive

- Phone charged above 80%, location on, mobile data on, volume up.
- In RoadPulse settings, choose the voice language for this run (alternate English and Malay
  across runs so both get tested).
- Plan the route, check the route cards, press **Go now** (not Simulate).

## During the drive (passenger)

- Press **Flag issue** at the moment anything is wrong and pick the closest category: wrong or
  missing prompt, prompt timing, false reroute, missed reroute, car in the wrong place, wrong
  route, wrong speed limit, map error, other. The time and position are saved automatically.
- Write one line of notes per flag if the reason is not obvious (for example "said turn left,
  road was a no-entry").
- At the end, press **Stop** (or arrive). **Do not copy logs while a drive is running**, or the
  report will count it as a crash.

## Route set (Klang Valley)

Each route targets specific risks. All names below were checked to be found by RoadPulse's search.
Start from the first place (or drive there), then search the destination.
Run each route at least twice (different days or times), once in English and once in Malay.

| # | Route | What it tests | Special instructions |
|---|---|---|---|
| R1 | KL Sentral → Pavilion Kuala Lumpur | Dense city streets, close turns (merged prompts), urban-canyon GPS around Bukit Bintang | Normal drive |
| R2 | KLCC → Ampang Point | Elevated expressway (AKLEH) running above and beside surface roads: parallel-road matching | Note any time the car appears on the wrong level |
| R3 | Mid Valley Megamall → Perdana Putra (Putrajaya) | Motorway interchanges and numbered exits (E37, E20/MEX), tolls, speed limits up to 110 | Watch exit prompts and the speed-limit sign |
| R4 | Through the SMART Tunnel (search "Terowong Smart"; plan a route that uses the tunnel, for example from Jalan Tun Razak towards Sungai Besi) | Long tunnel: GPS loss, "GPS signal lost", recovery after the tunnel | Check the route actually goes through the tunnel; only when it is open to cars (it closes during floods) |
| R5 | Perdana Putra → Presint 9 (Putrajaya) | Many roundabouts: exit counting in both languages | Check "take the 2nd exit" matches reality |
| R6 | Any route, with one deliberate wrong turn | Rerouting speed and prompts | Passenger tells the driver where to deviate safely, early in the route |
| R7 | Any 20+ minute route with the phone locked | Screen-off guidance and background survival | Lock the phone after Go now; run once with battery "Unrestricted" and once with the default setting |
| R8 | Any route; close RoadPulse mid-drive from the recent-apps screen | Drive resume | Reopen the app after a minute; expect "Resumed your drive to …" |
| R9 | Any route through a weak-signal area, or turn mobile data off mid-drive | Offline behaviour | Expect guidance to continue; turn data back on and check a reroute works |
| R10 | Repeat R1 or R3 at night | Night map, readability | Normal drive |

Battery: for R3 and R7, start with the phone **unplugged** and above 80%. The log records the battery
level every 5 minutes; the report needs at least 15 minutes unplugged.

## After the drives

1. Connect the phone by USB (or keep the emulator running for emulator tests).
2. Double-click `pull-drive-logs.bat`: copies the logs into `navlogs\`.
3. Double-click `analyze-drives.bat`: writes `navlogs\report.md` (needs Python 3:
   `winget install Python.Python.3.12`).
4. Send `report.md`, the CSV files and your notes for review. Logs and the report contain precise
   locations: share them only with the team, never publicly, and delete them after the review.

## What the report measures

- Per drive: distance, time, how much of the drive matched the route, share of poor GPS fixes,
  95th-percentile distance from the route, reroutes (and how many followed poor GPS), GPS-lost
  events and duration, prompts (and prompts closer than 4 s), speed-limit data status, arrival,
  battery use per hour.
- Summary and Gate 1 checks against the proposed targets below, plus every flagged issue with a
  map link.

## Proposed Gate 1 targets (to be agreed after the first drives)

The plan asks for thresholds based on pilot baselines. These are starting proposals:

| Measure | Proposed target |
|---|---|
| Reroutes flagged as false | ≤ 1 per 100 km |
| Crashes or lost sessions | 0 (any crash blocks Gate 1 until fixed) |
| Arrival detected when the car reached the destination | ≥ 95% |
| Wrong or badly timed prompts | 0 unexplained after review (each one investigated) |
| Battery use, screen on, not charging | ≤ 15% per hour |
| Screen-off guidance (R7) | Prompts continue for the whole drive on both test phones |
| Resume after the app is closed (R8) | Resumes within one minute of reopening |

Gate 1 is passed when R1–R10 have each been run at least twice, the targets are met (or the team
has agreed adjusted targets with reasons), and every flagged issue is fixed or documented as a
known limitation.

## Using the laptop's servers from the phone

See [REAL_PHONE.md](REAL_PHONE.md): `connect-phone.bat` plus "Phone on USB cable" in Settings.
