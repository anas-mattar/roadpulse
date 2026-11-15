# Loads named places from Overture Maps (open data from Meta, Microsoft, Foursquare and others:
# shops, clinics, offices, condos...) for Malaysia, Singapore and Brunei into the RoadPulse
# database, so search finds far more places than OpenStreetMap alone.
#
#   cd D:\solutions\waze\backend
#   powershell -ExecutionPolicy Bypass -File setup-overture-places.ps1
#
# Needs Docker Desktop and internet. Downloads about 200 MB (only the needed columns of the area
# are read from Overture's public storage, no account). Takes a few minutes. Search keeps working
# with the old places until the new ones are loaded. Run monthly (update-map-data.ps1 does it).
#
# Options:
#   -Release 2026-10-21.0   use a specific Overture release (default: the newest)

param([string]$Release = "")

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$maps = Join-Path $PSScriptRoot "maps"
$file = "overture-places.jsonl"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

Step "Checking Docker"
docker version --format "Docker {{.Server.Version}}" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }
New-Item -ItemType Directory -Force -Path $maps | Out-Null

Step "Preparing the Overture tool (first run only)"
docker build -q -t roadpulse-overture tools/overture | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Could not build the Overture tool image." }

Step "Downloading places for Malaysia, Singapore and Brunei"
$extra = @()
if ($Release) { $extra = @("--release", $Release) }
docker run --rm -v "${maps}:/out" roadpulse-overture "/out/$file.tmp" @extra
if ($LASTEXITCODE -ne 0) { throw "Downloading Overture places failed (check the internet connection)." }
Move-Item -Force (Join-Path $maps "$file.tmp") (Join-Path $maps $file)

Step "Loading places into the database"
docker compose up -d --build db api | Out-Host
docker compose run --rm api import-overture "/maps/$file"
if ($LASTEXITCODE -ne 0) { throw "Loading Overture places failed." }

Step "Checking"
Start-Sleep -Seconds 3
$hits = Invoke-RestMethod "http://localhost:8080/v1/places/search?q=mcity&lat=3.16&lon=101.72&limit=3"
Write-Host ("Search 'mcity' near KLCC: {0}" -f (($hits | ForEach-Object { $_.name }) -join ", ")) -ForegroundColor Green
Write-Host "Done. In the app, search finds these places when 'Use my laptop's server' is on."
