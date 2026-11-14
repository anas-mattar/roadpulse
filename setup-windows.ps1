# RoadPulse: one-time Windows setup + run.
# Installs Android Studio, the Android SDK and an emulator, builds the app, and launches it.
# Run by double-clicking setup-windows.bat (re-running is safe; finished steps are skipped).
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Project = $PSScriptRoot
$Sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$Avd = "RoadPulse_Pixel"

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

# 1. Android Studio (also provides the Java runtime used to build)
$StudioDir = "C:\Program Files\Android\Android Studio"
if (-not (Test-Path "$StudioDir\bin\studio64.exe")) {
    Step "Installing Android Studio (about 1 GB)"
    winget install --id Google.AndroidStudio -e --accept-package-agreements --accept-source-agreements
}
$env:JAVA_HOME = "$StudioDir\jbr"
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw "Java runtime not found at $env:JAVA_HOME" }

# 2. Android SDK command-line tools
$SdkManager = "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Step "Downloading Android SDK command-line tools"
    New-Item -ItemType Directory -Force -Path "$Sdk\cmdline-tools" | Out-Null
    $zip = Join-Path $env:TEMP "android-cmdline-tools.zip"
    Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath "$Sdk\cmdline-tools\tmp" -Force
    Move-Item "$Sdk\cmdline-tools\tmp\cmdline-tools" "$Sdk\cmdline-tools\latest"
    Remove-Item "$Sdk\cmdline-tools\tmp" -Recurse -Force
    Remove-Item $zip
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

# 3. SDK packages + emulator system image (about 2 GB)
Step "Installing SDK platform, build tools, emulator and system image"
$yes = ("y`n" * 30)
$yes | & $SdkManager --licenses | Out-Null
& $SdkManager "platform-tools" "platforms;android-35" "build-tools;36.0.0" "emulator" "system-images;android-35;google_apis;x86_64"

# 4. Virtual phone
$AvdManager = "$Sdk\cmdline-tools\latest\bin\avdmanager.bat"
$Emulator = "$Sdk\emulator\emulator.exe"
$Adb = "$Sdk\platform-tools\adb.exe"
if (-not ((& $Emulator -list-avds) -contains $Avd)) {
    Step "Creating virtual phone '$Avd'"
    "no" | & $AvdManager create avd -n $Avd -k "system-images;android-35;google_apis;x86_64" -d pixel_7
}

# Let the laptop keyboard type into the virtual phone (hw.keyboard=yes).
$AvdHome = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $env:USERPROFILE ".android\avd" }
$AvdConfig = Join-Path $AvdHome "$Avd.avd\config.ini"
if (Test-Path $AvdConfig) {
    $cfg = Get-Content $AvdConfig | Where-Object { $_ -notmatch '^hw\.keyboard\s*=' }
    $cfg += "hw.keyboard=yes"
    Set-Content -Path $AvdConfig -Value $cfg
}

# 5. Build
Step "Building RoadPulse"
Set-Content -Path "$Project\local.properties" -Value ("sdk.dir=" + ($Sdk -replace '\\', '\\\\'))
Push-Location $Project
& .\gradlew.bat :app:assembleDebug
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Build failed" }
Pop-Location
$Apk = "$Project\app\build\outputs\apk\debug\app-debug.apk"

# 6. Start emulator (or reuse a running one), install, launch
# adb writes progress to stderr; run it with errors relaxed so Windows PowerShell does not abort.
function Invoke-Adb {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { (& $Adb @args 2>&1) | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $previous }
}
function Get-EmulatorSerial {
    foreach ($line in (Invoke-Adb devices)) {
        if ($line -match '^(emulator-\d+)\s+device\s*$') { return $Matches[1] }
    }
    return $null
}

Invoke-Adb start-server | Out-Null
$Serial = Get-EmulatorSerial
if ($Serial) {
    Step "Reusing the running emulator ($Serial)"
} else {
    Step "Starting the emulator (first boot can take a few minutes)"
    Start-Process $Emulator -ArgumentList "-avd", $Avd, "-gpu", "auto", "-no-snapshot-load", "-timezone", "Asia/Kuala_Lumpur"   # cold boot: a saved state can be corrupt after a force-stop
    $deadline = (Get-Date).AddMinutes(10)
    while (-not ($Serial = Get-EmulatorSerial)) {
        if ((Get-Date) -gt $deadline) { throw "The emulator did not appear within 10 minutes. Run reset-emulator.bat, then run this script again." }
        Start-Sleep 3
    }
}

# Each check runs with a 15 s limit, because adb can hang forever on a frozen emulator.
function Test-Booted {
    $job = Start-Job -ArgumentList $Adb, $Serial -ScriptBlock {
        param($adb, $serial)
        & $adb -s $serial shell getprop sys.boot_completed 2>&1 | Out-String
    }
    $booted = $false
    if (Wait-Job $job -Timeout 15) { $booted = ((Receive-Job $job) -match '(?m)^\s*1\s*$') } else { Stop-Job $job }
    Remove-Job $job -Force
    return $booted
}
$deadline = (Get-Date).AddMinutes(10)
while (-not (Test-Booted)) {
    if ((Get-Date) -gt $deadline) {
        throw "Android did not finish booting within 10 minutes. Run reset-emulator.bat, then run this script again."
    }
    Start-Sleep 3
}

# The emulator's virtual Wi-Fi can lose its IPv4 route ("Network is unreachable").
# Its mobile-data connection uses the same laptop internet and is reliable, so use that.
Invoke-Adb -s $Serial shell svc wifi disable | Out-Null
# A fresh start can leave Location switched off on the virtual phone.
Invoke-Adb -s $Serial shell cmd location set-location-enabled true | Out-Null

Step "Installing and launching the app"
Invoke-Adb -s $Serial install -r -g $Apk
Invoke-Adb -s $Serial emu geo fix 101.6869 3.139 | Out-Null   # place the virtual phone in Kuala Lumpur
Invoke-Adb -s $Serial shell am force-stop com.roadpulse.app | Out-Null
Invoke-Adb -s $Serial shell am start -n com.roadpulse.app/.MainActivity
# Send the position again once the app is listening, so it gets a GPS fix right away.
Start-Sleep 5
Invoke-Adb -s $Serial emu geo fix 101.6869 3.139 | Out-Null

Write-Host "`nDone. RoadPulse is running in the emulator." -ForegroundColor Green
Write-Host "Next time: open the folder in Android Studio and press Run, or run this script again."
