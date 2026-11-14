# Creates a Windows scheduled task that updates all RoadPulse map data once a month
# (update-map-data.ps1: routing, places, toll plazas, offline tiles). Logs go to backend\logs.
#
#   cd D:\solutions\waze\backend
#   powershell -ExecutionPolicy Bypass -File register-map-update-task.ps1
#
# Options:
#   -Day 1 -Time 03:00   day of the month and time (default: the 1st at 03:00)
#   -Remove              delete the scheduled task
#
# The task runs only while you are signed in to Windows (Docker Desktop needs your session).
# If the laptop is off or asleep at that time, it runs as soon as possible afterwards.
# Local routing is unavailable for 10-20 minutes while it rebuilds.

param(
  [ValidateRange(1, 28)][int]$Day = 1,
  [string]$Time = "03:00",
  [switch]$Remove
)

$ErrorActionPreference = "Stop"
$name = "RoadPulse monthly map update"

if ($Remove) {
  cmd /c "schtasks /Delete /TN ""$name"" /F >nul 2>nul"
  if ($LASTEXITCODE -eq 0) { Write-Host "Removed the scheduled task." -ForegroundColor Green }
  else { Write-Host "There was no scheduled task to remove." }
  exit 0
}

if ($Time -notmatch '^\d{2}:\d{2}$') { throw "Time must look like 03:00" }
$script = Join-Path $PSScriptRoot "scheduled-map-update.ps1"
if (-not (Test-Path $script)) { throw "Missing $script" }

# schtasks makes the monthly schedule; the action is set below (safe quoting for paths with spaces).
schtasks /Create /F /TN $name /SC MONTHLY /D $Day /ST $Time /TR powershell.exe /RL LIMITED | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Could not create the scheduled task." }
$action = New-ScheduledTaskAction -Execute "powershell.exe" -WorkingDirectory $PSScriptRoot `
  -Argument ('-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "{0}"' -f $script)
Set-ScheduledTask -TaskName $name -Action $action | Out-Null

# Run late when the time was missed, allow on battery, stop after 3 hours.
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
  -ExecutionTimeLimit (New-TimeSpan -Hours 3)
Set-ScheduledTask -TaskName $name -Settings $settings | Out-Null

$task = Get-ScheduledTask -TaskName $name
$info = $task | Get-ScheduledTaskInfo
Write-Host ""
Write-Host ("Scheduled: '{0}', day {1} of every month at {2}." -f $name, $Day, $Time) -ForegroundColor Green
Write-Host ("Next run: {0}" -f $info.NextRunTime)
Write-Host "Logs: $PSScriptRoot\logs"
Write-Host "Test it now:   schtasks /Run /TN `"$name`""
Write-Host "Remove it:     powershell -ExecutionPolicy Bypass -File register-map-update-task.ps1 -Remove"
