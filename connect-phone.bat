@echo off
rem Lets a real phone use the RoadPulse servers on this laptop, through the USB cable
rem (or wireless debugging). Nothing is opened to the Wi-Fi network.
rem
rem   1. Phone: Developer options > USB debugging ON, connect the cable, accept "Allow USB debugging".
rem   2. Run this file (again after every reconnect or phone restart).
rem   3. In the app: Settings > Use my laptop's server ON > "Phone on USB cable".
rem
rem It maps the phone's own ports 8002 (routing), 8080 (reports, places, tolls) and
rem 8090 (offline map tiles) to the same ports on this laptop ("adb reverse").
set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist %ADB% set ADB=adb
%ADB% get-state >nul 2>nul
if errorlevel 1 (
  echo No phone found. Check the cable, USB debugging, and the "Allow USB debugging" prompt on the phone.
  pause
  exit /b 1
)
%ADB% reverse tcp:8002 tcp:8002
%ADB% reverse tcp:8080 tcp:8080
%ADB% reverse tcp:8090 tcp:8090
echo.
echo Connected ports:
%ADB% reverse --list
echo.
echo Done. Keep the cable connected while testing at your desk.
echo Driving test? The phone needs the laptop for routing; see docs\REAL_PHONE.md.
pause
