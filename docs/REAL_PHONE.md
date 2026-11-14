# Using RoadPulse on a real phone (everything stays on your laptop)

The debug app can use the servers on your laptop (routing, reports, places, tolls, offline maps)
from a real phone through the **USB cable**. Nothing is opened to Wi-Fi or the internet, and no
firewall change is needed.

## One-time setup

1. Install the debug app: see "Installing the debug app on a real phone" in
   [FIELD_TEST_PLAN.md](FIELD_TEST_PLAN.md).
2. Start the servers on the laptop as usual:
   `cd D:\solutions\waze\backend` then `docker compose up -d` (add `--profile maps` for offline tiles).

## Every time you connect the phone

1. Connect the cable and run **`connect-phone.bat`** (repository root). It runs `adb reverse`
   for ports 8002, 8080 and 8090, so the phone's own `127.0.0.1` reaches the laptop.
   Run it again after unplugging or restarting the phone.
2. In the app: **Settings > Use my laptop's server** on, then **Laptop reached from >
   Phone on USB cable**. On a real phone this is chosen automatically the first time.
3. Check: search a place and plan a route. Route cards with toll estimates show that
   the backend is reachable.

Wireless debugging (Android 11+) works too: pair with `adb pair`, `adb connect`, then run
`connect-phone.bat`. The phone and laptop must be on the same Wi-Fi, but the ports are still only
reachable through adb, not from other devices.

## Driving tests

On the road the phone only reaches the laptop while the cable is connected. Choose one:

- **Laptop in the car** (passenger holds it, cable connected): everything local, as at the desk.
- **No laptop**: switch "Use my laptop's server" off. The app then uses public routing
  (valhalla1.openstreetmap.de) and OpenFreeMap tiles. Reports, places, cameras and tolls are not
  available, and reports stay on the phone.

## Phone settings that stop navigation in the background

Some brands stop apps that run with the screen off. For the test phone:

- Settings > Apps > RoadPulse > Battery: **Unrestricted** (Samsung: "Unrestricted";
  Xiaomi: "No restrictions", and turn on **Autostart**).
- Location permission: **Allow all the time** is not needed. "While using the app" is enough,
  because navigation runs as a foreground service with a notification.
- Keep "Developer options > Don't keep activities" **off**.
