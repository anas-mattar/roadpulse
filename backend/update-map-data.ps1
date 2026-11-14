# Updates all RoadPulse map data from the latest OpenStreetMap extract for Malaysia, Singapore and
# Brunei (Geofabrik, updated daily):
#   1. downloads the new map file safely (resumable; the current file stays until the new one is complete)
#   2. rebuilds the routing data (Valhalla)          ~10-20 min, local routing is unavailable meanwhile
#   3. reloads places: fuel, parking, cameras...      ~ a few minutes  (setup-places.ps1)
#   4. rebuilds the offline map tiles, if set up     ~5-10 min        (setup-offline-maps.ps1)
# Community reports are live and are not touched.
#
#   cd D:\solutions\waze\backend
#   powershell -ExecutionPolicy Bypass -File update-map-data.ps1
#
# Options:
#   -Force          rebuild even if the downloaded data is not newer than the current file
#   -SkipDownload   rebuild everything from the map file already in valhalla_data
#   -SkipMaps       do not rebuild the offline map tiles
# The previous map file is kept as valhalla_data\previous.osm.pbf (to go back: see the end of this file).

param(
  [switch]$Force,
  [switch]$SkipDownload,
  [switch]$SkipMaps
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$started = Get-Date
$pbfName = "malaysia-singapore-brunei-latest.osm.pbf"
$url = "https://download.geofabrik.de/asia/$pbfName"
$data = Join-Path $PSScriptRoot "valhalla_data"
$pbf = Join-Path $data $pbfName
$previous = Join-Path $data "previous.osm.pbf"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

# Date of the OpenStreetMap data inside a map file (from its header), or "unknown".
function DataDate($file) {
  $dir = Split-Path $file -Parent
  $name = Split-Path $file -Leaf
  $value = docker run --rm -v "${dir}:/w:ro" roadpulse-osmium fileinfo -g header.option.osmosis_replication_timestamp "/w/$name"
  if ($LASTEXITCODE -ne 0 -or -not $value) { return "unknown" }
  return "$value".Trim()
}

# True when the whole file can be read (catches cut-off downloads).
function IsComplete($file) {
  $dir = Split-Path $file -Parent
  $name = Split-Path $file -Leaf
  # Through cmd, so osmium's error output cannot stop this script (Windows PowerShell 5.1).
  cmd /c "docker run --rm -v `"${dir}:/w:ro`" roadpulse-osmium fileinfo -e `"/w/$name`" >nul 2>nul"
  return ($LASTEXITCODE -eq 0)
}

Step "Checking Docker"
docker version --format "Docker {{.Server.Version}}" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }
docker build -q -t roadpulse-osmium tools/osmium | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not build the osmium tool image." }
New-Item -ItemType Directory -Force -Path $data | Out-Null

$oldDate = if (Test-Path $pbf) { DataDate $pbf } else { "none" }
Write-Host "Current map data: $oldDate"

# ---- 1. Download -----------------------------------------------------------------------------
if (-not $SkipDownload) {
  Step "Downloading the latest map file (about 300 MB)"
  # One temporary name per day, so a resumed download never mixes two different days' files.
  $newFile = Join-Path $data ("new-{0:yyyyMMdd}.osm.pbf" -f (Get-Date))
  Get-ChildItem $data -Filter "new-*.osm.pbf" | Where-Object { $_.FullName -ne $newFile } | Remove-Item -Force
  curl.exe -L -C - --retry 10 --retry-delay 5 -o $newFile $url
  if ($LASTEXITCODE -ne 0) {
    throw "The download stopped (curl exit $LASTEXITCODE). Run this script again: it continues where it stopped."
  }

  Step "Checking the downloaded file"
  if (-not (IsComplete $newFile)) {
    throw "The downloaded file is incomplete or damaged. Run this script again to resume. Your current map data is unchanged."
  }
  $newDate = DataDate $newFile
  Write-Host ("Downloaded map data: {0} ({1:N0} MB)" -f $newDate, ((Get-Item $newFile).Length / 1MB))

  if (-not $Force -and $newDate -ne "unknown" -and $newDate -eq $oldDate) {
    Remove-Item $newFile -Force
    Write-Host "Your map data is already up to date. Nothing to do (use -Force to rebuild anyway)." -ForegroundColor Green
    exit 0
  }

  if (Test-Path $pbf) { Move-Item -Force $pbf $previous }
  Move-Item -Force $newFile $pbf
  Write-Host "Map file replaced (previous one kept as valhalla_data\previous.osm.pbf)."
} elseif (-not (Test-Path $pbf)) {
  throw "No map file at $pbf. Run without -SkipDownload."
}
$dataDate = DataDate $pbf

# ---- 2. Routing ------------------------------------------------------------------------------
Step "Rebuilding routing data (10-20 minutes; local routing is unavailable meanwhile)"
# The routing server reuses its built data unless told to rebuild (it does not notice a new file
# with the same name). force_rebuild is read from VALHALLA_FORCE_REBUILD (docker-compose.yml).
$env:VALHALLA_FORCE_REBUILD = "True"
docker compose up -d --force-recreate valhalla
if ($LASTEXITCODE -ne 0) { throw "Could not restart the routing server." }
$deadline = (Get-Date).AddMinutes(60)
$ready = $false
Start-Sleep -Seconds 20
while ((Get-Date) -lt $deadline) {
  $running = docker compose ps --status running -q valhalla
  if (-not $running) { throw "The routing server stopped while rebuilding. See: docker compose logs valhalla" }
  try {
    Invoke-RestMethod -TimeoutSec 5 "http://localhost:8002/status" | Out-Null
    $ready = $true
    break
  } catch {
    Write-Host ("  still building... {0:mm\:ss} elapsed" -f ((Get-Date) - $started))
    Start-Sleep -Seconds 30
  }
}
if (-not $ready) { throw "Routing rebuild did not finish within 60 minutes. See: docker compose logs valhalla" }

# Back to normal starts (no rebuild on the next restart).
$env:VALHALLA_FORCE_REBUILD = "False"
docker compose up -d --force-recreate valhalla | Out-Null
$routeOk = $false
for ($i = 0; $i -lt 30 -and -not $routeOk; $i++) {
  Start-Sleep -Seconds 5
  try {
    $body = '{"locations":[{"lat":2.9264,"lon":101.6964},{"lat":3.1579,"lon":101.7116}],"costing":"auto","units":"kilometers"}'
    $trip = (Invoke-RestMethod -Method Post -TimeoutSec 10 -ContentType "application/json" -Body $body "http://localhost:8002/route").trip
    Write-Host ("Routing works: Putrajaya to KLCC {0:N1} km, {1:N0} min" -f $trip.summary.length, ($trip.summary.time / 60)) -ForegroundColor Green
    $routeOk = $true
  } catch { }
}
if (-not $routeOk) { throw "The routing server started but did not answer a test route. See: docker compose logs valhalla" }

# ---- 3. Places -------------------------------------------------------------------------------
Step "Reloading places"
& (Join-Path $PSScriptRoot "setup-places.ps1")

# ---- 4. Offline map tiles --------------------------------------------------------------------
$tiles = Join-Path $PSScriptRoot "maps\malaysia.mbtiles"
if ($SkipMaps) {
  Write-Host ""; Write-Host "Offline map tiles skipped (-SkipMaps)."
} elseif (Test-Path $tiles) {
  Step "Rebuilding offline map tiles"
  & (Join-Path $PSScriptRoot "setup-offline-maps.ps1")
  docker compose --profile maps restart tiles | Out-Null
} else {
  Write-Host ""; Write-Host "Offline map tiles are not set up; skipped (see setup-offline-maps.ps1)."
}

Step "Done"
Write-Host ("Map data date: {0} (was {1})" -f $dataDate, $oldDate) -ForegroundColor Green
Write-Host ("Total time: {0:hh\:mm\:ss}" -f ((Get-Date) - $started))
Write-Host "Restart the app on the emulator to load fresh routes and places."

# To go back to the previous map file:
#   Move-Item -Force valhalla_data\previous.osm.pbf valhalla_data\malaysia-singapore-brunei-latest.osm.pbf
#   powershell -ExecutionPolicy Bypass -File update-map-data.ps1 -SkipDownload
