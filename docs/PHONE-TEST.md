# Testing RoadPulse on a real phone

Both apps build, but neither has been tried on a real phone. Work through this list in order and
note anything that fails. For each problem, send: **the test number, what you did, what you saw**,
and a screenshot if possible. Nothing here needs a Mac.

Legend: ✅ works · ❌ fails (send details) · ⏭ skipped

---

## Part A: install on the iPhone (once, then every 7 days)

A free Apple ID signs the app for 7 days; after that it will not open until you install it again.

1. On GitHub: **Actions > iOS build**, open the latest green run, download
   **RoadPulse-unsigned-ipa** and unzip it (you get `RoadPulse-unsigned.ipa`).
2. On the laptop, install **Sideloadly** (sideloadly.io). It needs Apple's iTunes and iCloud for
   Windows installed from Apple's website (not the Microsoft Store versions); Sideloadly's page
   explains this.
3. Connect the iPhone with the cable, unlock it and tap **Trust** on the phone.
4. In Sideloadly: drag in the `.ipa`, type your Apple ID, press **Start**. It asks for your Apple ID
   password (and a code if you use two-factor sign-in).
5. On the iPhone (iOS 16 or later): **Settings > Privacy & Security > Developer Mode** → on, restart
   when asked.
6. **Settings > General > VPN & Device Management** → tap your Apple ID → **Trust**.
7. Open RoadPulse. Allow location **While Using the App**.

If any of these steps looks different on your screen, send a screenshot. Sideloadly is a third-party
tool and was not tested by this project.

---

## Part B: iPhone without the laptop (public servers)

| # | Try | Expected |
|---|-----|----------|
| B1 | Open the app | Map of where you are (blue dot). Kuala Lumpur if location is off. |
| B2 | Search "KLCC", pick it | Up to 3 route cards with time, km and "via …". The selected route is indigo on the map. |
| B3 | Tap ▶ (simulated drive) | Car moves along the route, turn card at top, voice says turns. ✕ stops. |
| B4 | Settings > Language > Bahasa Melayu, then العربية | All texts change; Arabic is right-to-left; the voice speaks that language if the phone has the voice. |
| B5 | Microphone in the search box, say "Mid Valley" | Asks for microphone and speech recognition once, then the words appear and results show. |
| B6 | Press and hold the map | Card with the place name: Drive here, Add a stop, Parked here, Save place. |
| B7 | With a destination chosen: press and hold elsewhere > Add a stop | Stop 1 appears on the card; routes are planned again through it. |
| B8 | Home chip > search your home address > pick it | "Home saved". Tapping Home later plans the route. |
| B9 | ★ on a destination, name it | It appears as a chip under the search box. |
| B10 | Settings > Route > Avoid tolls on | Routes are planned again; the card says "avoiding tolls". |
| B11 | Settings > Map colours > Always dark | Night map. Back to Automatic: dark only after sunset. |

## Part C: a real drive (as a passenger, or with the phone mounted; do not handle it while driving)

| # | Try | Expected |
|---|-----|----------|
| C1 | Choose a destination 5–15 min away, **Go now** | Map follows the car, tilted; turns announced before each junction. |
| C2 | Lock the screen for a while | Voice keeps guiding; blue location bar/pill at the top of iOS. |
| C3 | Play music in Spotify/Apple Music | Music gets quieter during each instruction, then comes back. |
| C4 | Take a wrong turn on purpose (safely) | "Finding a new route…", then new instructions. |
| C5 | Speed and limit signs | Your speed in the circle; limit sign where the road has one; red speed when over. |
| C6 | Lanes before a big junction | Lane arrows under the turn card (only where the map has lane data). |
| C7 | Map button (overview) | Whole remaining route; "my location" goes back to following. |
| C8 | Arrive | Arrival is announced and navigation ends. |

## Part D: iPhone with the laptop servers (home Wi-Fi)

Before: laptop and phone on the same Wi-Fi (set to **Private** in Windows), servers running
(`docker compose up -d` in `backend`), and once as administrator:
`powershell -ExecutionPolicy Bypass -File backend\open-iphone-access.ps1`.

| # | Try | Expected |
|---|-----|----------|
| D1 | Settings > Use my laptop's server, type the address the script printed, **Test connection** | "Connected to http://…:8080". iOS asks about Local Network once: Allow. |
| D2 | Back on the map | Report button and fuel/parking/food chips appear; the profile button appears top left. |
| D3 | Report > Police > Hidden > Send | "Thanks!", a police pin on the map. |
| D4 | Tap someone else's pin (or ask a second phone to report) | Card with Still there / Not there. |
| D5 | Fuel chip | Petrol stations nearby with Open/Closed. |
| D6 | Route over a toll road | Route card shows "Toll ~RM …". |
| D7 | Profile button | Level, points, badges; set a nickname and save. |
| D8 | During a drive: share button | Share sheet opens with "I'm driving to …"; open the link on another phone on the same Wi-Fi (the link points to the laptop): live car on a map. |
| D9 | Settings > Privacy > Export my data | Share sheet with a `.json` file. |
| D10 | Optional: "Maps from my laptop" (start tiles with `$env:TILES_BIND="0.0.0.0"; docker compose --profile maps up -d`) | Map still shows, now from the laptop. |

## Part E: planned drive

| # | Try | Expected |
|---|-----|----------|
| E1 | Pick a destination > alarm button > arrive in ~45 min > Remind me | Asks to allow notifications once; "Reminder set for …"; the drive is listed under the search box. |
| E2 | Wait | "Time to leave for …" notification about 5 minutes before the leave time. Tapping it opens the route. |

## Part F: Android phone (if you have one)

Connect over USB with `connect-phone.bat` (it also forwards the laptop servers to the phone),
install the debug app from Android Studio, then repeat B1–B11, C1–C8, D2–D8 and E1–E2. Extra on
Android only: music controls during a drive (Settings > Music controls, needs notification access).

---

## Known limits (not bugs)

- Free Apple ID: the app stops opening after 7 days; install again with Sideloadly.
- Reports, places, tolls, traffic and sharing work only on the same Wi-Fi as the laptop. Away from
  home the app uses the public routing and search servers only.
- Lane arrows, speed limits and opening hours exist only where OpenStreetMap has them.
- Voices: Malay and Arabic voices depend on the phone (iPhone: Settings > Accessibility >
  Spoken Content > Voices). Without one, instructions are in English.
