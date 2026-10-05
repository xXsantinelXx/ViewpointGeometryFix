@echo off
rem VPGF Doctor - prueft die Installation ausserhalb des Spiels (nur lesend).
rem Braucht kein Java, nur die in Windows enthaltene PowerShell.
rem Doppelklick genuegt. Optional: VPGF-Doctor.bat -SteamLib "D:\SteamLibrary"
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0VPGF-Doctor.ps1" %*
if exist "%~dp0VPGF-Report.txt" (
  start "" notepad "%~dp0VPGF-Report.txt"
) else (
  echo.
  echo Es wurde kein Bericht erzeugt. Bitte die Ausgabe oben abfotografieren.
)
pause
