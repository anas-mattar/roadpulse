# Runs the route test set (tools/route-tests/trips.csv) and writes results.csv next to it.
# Needs only Docker (Python runs inside a small container).
#   powershell -ExecutionPolicy Bypass -File tools\route-tests\run-route-tests.ps1            # public routing server
#   powershell -ExecutionPolicy Bypass -File tools\route-tests\run-route-tests.ps1 -Local     # the routing server on this laptop
param([switch]$Local)

$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot
$server = if ($Local) { "http://host.docker.internal:8002" } else { "https://valhalla1.openstreetmap.de" }
docker run --rm -v "${dir}:/t" python:3.12-slim python /t/route_tests.py --server $server --out /t/results.csv
if ($LASTEXITCODE -ne 0) { throw "The route tests did not finish." }
Write-Host "Results: $dir\results.csv (compare with baseline-2026-11-17.csv)" -ForegroundColor Green
