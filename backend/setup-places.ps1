# Loads places that drivers search for or are warned about (fuel, parking, food, EV charging,
# R&R rest areas, speed cameras, toll plazas, schools) from the Malaysia map file into the RoadPulse database.
#
#   cd D:\solutions\waze\backend
#   powershell -ExecutionPolicy Bypass -File setup-places.ps1
#
# Needs Docker Desktop and the map file the routing server downloaded
# (valhalla_data\malaysia-singapore-brunei-latest.osm.pbf). Takes a few minutes; no extra download
# except the small osmium tool image on the first run. Re-run after updating the map file.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$pbfName = "malaysia-singapore-brunei-latest.osm.pbf"
$pbf = Join-Path $PSScriptRoot "valhalla_data\$pbfName"
$maps = Join-Path $PSScriptRoot "maps"

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }

Step "Checking Docker and the map file"
docker version --format "Docker {{.Server.Version}}" | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }
if (-not (Test-Path $pbf)) { throw "Map file not found: $pbf (start the routing server once: docker compose up -d valhalla)" }
New-Item -ItemType Directory -Force -Path $maps | Out-Null

Step "Preparing the osmium tool (first run only)"
docker build -q -t roadpulse-osmium tools/osmium | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Could not build the osmium tool image." }

Step "Extracting places from the map file"
docker run --rm -v "${PSScriptRoot}\valhalla_data:/osm:ro" -v "${maps}:/work" roadpulse-osmium `
  tags-filter "/osm/$pbfName" `
  n/highway=speed_camera `
  nw/amenity=fuel,parking,charging_station,restaurant,fast_food,cafe,food_court,school `
  nw/hazard=school_zone `
  nw/highway=rest_area,services `
  nw/barrier=toll_booth `
  -o /work/places-filtered.osm.pbf --overwrite
if ($LASTEXITCODE -ne 0) { throw "Extracting places failed." }
docker run --rm -v "${maps}:/work" roadpulse-osmium `
  export /work/places-filtered.osm.pbf -f geojsonseq -a type,id --geometry-types=point,polygon `
  -o /work/places.geojsonseq --overwrite
if ($LASTEXITCODE -ne 0) { throw "Converting places failed." }
Remove-Item (Join-Path $maps "places-filtered.osm.pbf") -ErrorAction SilentlyContinue

Step "Loading places into the database"
docker compose up -d --build db api | Out-Host
docker compose run --rm api import-places /maps/places.geojsonseq
if ($LASTEXITCODE -ne 0) { throw "Loading places failed." }

Step "Checking"
Start-Sleep -Seconds 3
$fuel = Invoke-RestMethod "http://localhost:8080/v1/places/nearby?category=FUEL&lat=3.1579&lon=101.7116&radiusM=3000&limit=3"
Write-Host ("Fuel stations near KLCC: {0}" -f (($fuel | ForEach-Object { if ($_.name) { $_.name } else { $_.brand } }) -join ", ")) -ForegroundColor Green
Write-Host "Done. In the app: tap a category under the search box (Fuel, Parking, Food, EV, R&R)."
