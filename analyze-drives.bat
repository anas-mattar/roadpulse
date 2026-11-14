@echo off
rem Builds a field-test report (navlogs\report.md) from the drive logs. Needs Python 3.
rem Run pull-drive-logs.bat first (after pressing Stop in the app).
where py >nul 2>&1 && (py -3 "%~dp0tools\analyze_drives.py" "%~dp0navlogs" %* & goto done)
where python >nul 2>&1 && (python "%~dp0tools\analyze_drives.py" "%~dp0navlogs" %* & goto done)
echo Python 3 is not installed. Install it with:  winget install Python.Python.3.12
echo then open a new window and run this file again.
:done
pause
