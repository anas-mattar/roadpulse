# Run by the Windows scheduled task that register-map-update-task.ps1 creates.
# Starts Docker Desktop if needed, runs update-map-data.ps1 and writes a log to backend\logs.
# You can also run it by hand to test the scheduled run.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$logs = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$log = Join-Path $logs ("map-update-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmm"))
Start-Transcript -Path $log | Out-Null
$failed = $false
try {
  # Docker Desktop must be running; start it and wait up to 5 minutes.
  cmd /c "docker info >nul 2>nul"
  if ($LASTEXITCODE -ne 0) {
    $desktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"
    if (Test-Path $desktop) {
      Write-Host "Starting Docker Desktop..."
      Start-Process $desktop
      for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 5
        cmd /c "docker info >nul 2>nul"
        if ($LASTEXITCODE -eq 0) { break }
      }
    }
    cmd /c "docker info >nul 2>nul"
    if ($LASTEXITCODE -ne 0) { throw "Docker is not running and could not be started." }
  }
  & (Join-Path $PSScriptRoot "update-map-data.ps1")
} catch {
  $failed = $true
  Write-Host ("FAILED: " + $_.Exception.Message) -ForegroundColor Red
} finally {
  Stop-Transcript | Out-Null
  # Keep the 12 newest logs.
  Get-ChildItem $logs -Filter "map-update-*.log" | Sort-Object Name -Descending | Select-Object -Skip 12 | Remove-Item -ErrorAction SilentlyContinue
}
if ($failed) { exit 1 }
