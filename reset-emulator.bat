@echo off
rem Force-stops a frozen Android emulator and clears its leftover lock files.
echo Stopping the Android emulator and adb...
taskkill /F /T /IM qemu-system-x86_64.exe >nul 2>&1
taskkill /F /T /IM qemu-system-x86_64-headless.exe >nul 2>&1
taskkill /F /T /IM emulator.exe >nul 2>&1
taskkill /F /T /IM adb.exe >nul 2>&1
set AVD_DIR=%USERPROFILE%\.android\avd\RoadPulse_Pixel.avd
if exist "%AVD_DIR%" (
    for %%F in ("%AVD_DIR%\*.lock") do (
        del /F /Q "%%F" >nul 2>&1
        rmdir /S /Q "%%F" >nul 2>&1
    )
)
echo.
echo Done. Now double-click setup-windows.bat again.
pause
