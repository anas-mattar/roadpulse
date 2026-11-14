@echo off
rem Copies RoadPulse debug drive logs (CSV) from the emulator or a USB-connected phone into .\navlogs
set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%~dp0navlogs" mkdir "%~dp0navlogs"
%ADB% pull /sdcard/Android/data/com.roadpulse.app/files/navlogs/. "%~dp0navlogs"
echo.
echo Logs are in %~dp0navlogs
pause
