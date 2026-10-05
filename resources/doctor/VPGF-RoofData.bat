@echo off
rem VPGF RoofData - Dach-Fix Variante A (experimentell).
rem Schreibt eine Ergaenzungsdatei mit Dach-Formen in den Ordner der Mod ViewpointGeometryFix.
rem Spiel vorher schliessen.  Rueckgaengig:  VPGF-RoofData.bat -Remove
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0VPGF-RoofData.ps1" %*
pause
