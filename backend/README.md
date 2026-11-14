# RoadPulse backend

ASP.NET Core on .NET 10 (ADR 0001), PostgreSQL 16 + PostGIS, run with Docker Compose (ADR 0002).
Phase 2 foundation: anonymous devices (ADR 0006) and community reports. The Android app does not
use it yet; it switches over after the Gate 1 test drives.

## API (v1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/v1/devices` | none | Register an anonymous device; returns `deviceId` and a secret `token` (shown once) |
| GET | `/v1/reports?bbox=minLon,minLat,maxLon,maxLat` | none | Active reports in a map area (max 0.5° per side, 500 results) |
| POST | `/v1/reports` | device | Create a report `{type, lat, lon, headingDeg?, subtype?, durationMinutes?}` (subtype per type, e.g. HAZARD: POTHOLE, FLOOD; durationMinutes 15-1440 for ROAD_CLOSED only); same type within 100 m in the last 15 min is merged as a confirmation. Send `Idempotency-Key` to make retries safe |
| POST | `/v1/reports/{id}/votes` | device | `{stillThere: true/false}`; one vote per device, reporters cannot vote on their own |
| POST | `/v1/reports/{id}/flags` | device | Flag abuse `{reason: FAKE/SPAM/OFFENSIVE/WRONG_LOCATION}`; 3 flags from different devices hide the report until a moderator reviews it |
| GET | `/v1/moderation/queue` | moderator | Hidden reports first, then flagged ones, with flag reasons and the reporter's history |
| POST | `/v1/moderation/reports/{id}/remove` | moderator | `{reason}`; takes a report off the map |
| POST | `/v1/moderation/reports/{id}/restore` | moderator | `{reason}`; back on the map, and later flags no longer hide it |
| GET | `/v1/moderation/reports?limit=` | moderator | Newest reports in any status, with flags and reporter history |
| GET | `/v1/moderation/reports/{id}` | moderator | One report, any status |
| GET | `/v1/moderation/devices/{id}` | moderator | Device summary: reports, removed reports, blocked |
| POST | `/v1/moderation/devices/{id}/block` | moderator | `{reason, removeActiveReports=true}`; its token stops working |
| POST | `/v1/moderation/devices/{id}/unblock` | moderator | `{reason}` |
| GET | `/v1/moderation/audit?limit=&target=` | moderator | Every moderator action: who, what, when, why |
| WS | `/hubs/reports` | none | Live updates (SignalR), see below |
| GET | `/health` | none | Database connectivity |
| GET | `/openapi/v1.json` | none | OpenAPI contract |

Device auth: `Authorization: Bearer <token>`. Only a SHA-256 hash of the token is stored.
Report types: `POLICE`, `CRASH`, `HAZARD`, `TRAFFIC_JAM`, `ROAD_CLOSED`.

Rules (`Modules/Reports/ReportRules.cs`): time to live per type (jam 30 min, police 45, crash 60,
hazard 90, road closed 6 h); a "still there" vote extends expiry to at least half a TTL from now,
capped at 4x TTL; two or more "not there" votes that outnumber confirmations remove the report.
Errors use problem details (RFC 9457). Rate limits per device (writes) and per IP (reads,
registration) are set in `appsettings.json`.

Moderator auth: `X-Moderator-Key: <key>`. Configuration holds only SHA-256 hashes of keys
(`Moderation:Moderators`), separate from device tokens. Every moderator action needs a reason and
is written to the audit log (IDs only, no locations). Create a key with `new-moderator-key.ps1`
and put the hash in `.env` (see `docker-compose.yml`).

## Moderation page

Open **http://localhost:8080/admin** and paste your moderator key (see `new-moderator-key.ps1`).
Tabs: review queue (hidden reports first), recent reports (any status), look up a report or device
by ID, and the audit log. Remove, restore, block and unblock each ask for a reason, which goes to
the audit log. The key is kept only in that browser tab (session storage) and every action calls
the moderation API with it; the page itself contains no data. Strict browser security headers
(no inline scripts, no outside resources, no framing). Turn the page off with
`Moderation__WebPage=false`.

## Offline map tiles (optional)

The app's map normally loads from OpenFreeMap over the internet. To serve map tiles from this
computer instead (the emulator's map then works with no internet), run once:
```
powershell -ExecutionPolicy Bypass -File setup-offline-maps.ps1
```
It builds vector tiles for Malaysia, Singapore and Brunei from the same OpenStreetMap file the
routing server uses (Planetiler, OpenMapTiles schema; first run downloads about 1.3 GB of
coastline data into `maps/sources`), fetches the map symbols, and starts the `tiles` service
(TileServer GL) at http://localhost:8090. Then turn on **Settings > Maps from my laptop** in a
debug build. Later starts: `docker compose --profile maps up -d tiles`.

`maps/styles` holds the day (liberty) and night (fiord) styles from OpenFreeMap, changed to use
the local tiles and the server's bundled Noto Sans Regular font (bold and italic labels show in
regular). The country-level shaded relief layer is left out. Search (Photon) still needs internet.

## Live updates

SignalR hub at `/hubs/reports` (WebSockets, with fallbacks). The map is split into 0.1° tiles
(about 11 km). A client calls `SubscribeArea(minLon, minLat, maxLon, maxLat)` for the area it shows
(max 0.5° per side; call again when the map moves) and then receives:

- `ReportUpserted(report)`: new report, a confirmation, or a report restored by a moderator
- `ReportRemoved(id)`: dismissed by drivers, hidden by abuse flags, or removed by a moderator

Expiry is not pushed: clients drop a report at its `expiresAt`. Recommended client flow: subscribe,
then load `GET /v1/reports` for the area, then apply events; after a reconnect, subscribe and load
again. Events carry the same public data as the list (no device IDs). One server keeps groups in
memory; running several API servers later needs a Redis backplane.

Not built yet (next backend steps): telemetry (waits for ADR 0004 legal review), deployment workflow.

## Run locally

With Docker:
```
cd backend
docker compose up -d --build
curl http://localhost:8080/health
```

Without Docker (needs .NET 10 SDK and a PostgreSQL with PostGIS):
```
cd backend
dotnet run --project src/RoadPulse.Api
```
The connection string is `ConnectionStrings:Default` in `appsettings.json` (override with the
`ConnectionStrings__Default` environment variable). Migrations run on startup.

## Tests

```
cd backend
dotnet test
```
Integration tests start the real API against a real PostGIS database (set `ROADPULSE_TEST_DB` to a
connection string; the database is dropped and re-created from the migrations). Time is controlled
by a fake clock, so expiry tests run instantly.

## Database changes

```
dotnet tool install --global dotnet-ef --version 10.0.15
dotnet ef migrations add <Name> --project src/RoadPulse.Api --output-dir Data/Migrations
```
