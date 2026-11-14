# Builds RoadPulse's own map tiles for Malaysia, Singapore and Brunei and starts a local tile
# server, so the emulator's map works without internet.
#
#   cd D:\solutions\waze\backend
#   powershell -ExecutionPolicy Bypass -File setup-offline-maps.ps1
#
# Needs Docker Desktop running and the map file that the routing server already downloaded
# (valhalla_data\malaysia-singapore-brunei-latest.osm.pbf).
# First run downloads about 1.3 GB of coastline and water data (kept in maps\sources for next time)
# and takes roughly 10-20 minutes. Re-run it to refresh the tiles after updating the map file.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$pbf = Join-Path $PSScriptRoot "valhalla_data\malaysia-singapore-brunei-latest.osm.pbf"
$maps = Join-Path $PSScriptRoot "maps"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

Step "Checking Docker and the map file"
docker version --format "Docker {{.Server.Version}}" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }
if (-not (Test-Path $pbf)) {
  throw "Map file not found: $pbf`nStart the routing server once (docker compose up -d valhalla) or download it as described in backend\README.md."
}
Write-Host ("Map file: {0:N0} MB" -f ((Get-Item $pbf).Length / 1MB))

# The water file (about 890 MB) comes from a single, often slow server. If it was downloaded by
# hand or a download was cut off, check it is complete before Planetiler tries to open it.
$water = Join-Path $maps "sources\water-polygons-split-3857.zip"
$waterUrl = "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip"
if (Test-Path $water) {
  # Through cmd, so tar's error output cannot stop the script (Windows PowerShell 5.1 behaviour).
  cmd /c "tar -tf `"$water`" >nul 2>nul"
  if ($LASTEXITCODE -ne 0) {
    Write-Host ("The water file is incomplete ({0:N1} MB of about 890 MB). Resume the download with:" -f ((Get-Item $water).Length / 1MB)) -ForegroundColor Yellow
    Write-Host "  curl.exe -L -C - --retry 10 -o maps\sources\water-polygons-split-3857.zip $waterUrl"
    Write-Host "then run this script again."
    exit 1
  }
}

Step "Building map tiles (Planetiler; first run downloads coastline data)"
Write-Host "If the water download is very slow, stop with Ctrl+C and fetch it with curl.exe (it can resume):"
Write-Host "  curl.exe -L -C - --retry 10 -o maps\sources\water-polygons-split-3857.zip $waterUrl"
docker run --rm `
  -v "${PSScriptRoot}\valhalla_data:/osm:ro" `
  -v "${maps}:/data" `
  ghcr.io/onthegomap/planetiler:0.10.1 `
  --osm_path=/osm/malaysia-singapore-brunei-latest.osm.pbf `
  --download `
  --output=/data/malaysia.mbtiles `
  --force
if ($LASTEXITCODE -ne 0) { throw "Building the tiles failed (see the messages above)." }
Write-Host ("Tiles: {0:N0} MB" -f ((Get-Item (Join-Path $maps "malaysia.mbtiles")).Length / 1MB))

Step "Downloading map symbols (once)"
$sprites = Join-Path $maps "styles\sprites"
New-Item -ItemType Directory -Force -Path $sprites | Out-Null
foreach ($f in "ofm.json", "ofm.png", "ofm@2x.json", "ofm@2x.png") {
  $target = Join-Path $sprites $f
  if (-not (Test-Path $target)) {
    Invoke-WebRequest -UseBasicParsing -Uri "https://tiles.openfreemap.org/sprites/ofm_f384/$f" -OutFile $target
  }
}

Step "Starting the tile server"
docker compose --profile maps up -d tiles
if ($LASTEXITCODE -ne 0) { throw "Could not start the tile server." }
Start-Sleep -Seconds 5
$style = Invoke-RestMethod "http://localhost:8090/styles/liberty/style.json"
Write-Host "Tile server is up: $($style.name)" -ForegroundColor Green
Write-Host ""
Write-Host "In the app: Settings > 'Maps from my laptop'. Day and night maps now load from this computer."
Write-Host "Later starts: docker compose --profile maps up -d tiles"
