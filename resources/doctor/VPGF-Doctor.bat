@echo off
rem VPGF Doctor - prueft die Installation ausserhalb des Spiels (nur lesend).
rem Doppelklick genuegt. Optional: VPGF-Doctor.bat --steam-lib "D:\SteamLibrary"
setlocal
cd /d "%~dp0"

set "JAVA="
for %%D in ("%ProgramFiles(x86)%\Steam" "%ProgramFiles%\Steam" "C:\SteamLibrary" "D:\SteamLibrary" "E:\SteamLibrary" "D:\Steam" "E:\Steam") do (
  if not defined JAVA if exist "%%~D\steamapps\common\ProjectZomboid\jre64\bin\java.exe" set "JAVA=%%~D\steamapps\common\ProjectZomboid\jre64\bin\java.exe"
)
if not defined JAVA (
  where java >nul 2>nul && set "JAVA=java"
)
if not defined JAVA (
  echo Kein Java gefunden. Bitte Pfad der Steam-Bibliothek pruefen oder Java 17+ installieren.
  pause
  exit /b 1
)

echo Verwende Java: %JAVA%
"%JAVA%" -jar "%~dp0VPGF-Doctor.jar" %*
if errorlevel 1 (
  echo.
  echo Der Doctor ist mit einem Fehler beendet worden. Bitte die Ausgabe oben abfotografieren.
  pause
  exit /b 1
)
start "" notepad "%~dp0VPGF-Report.txt"
pause
