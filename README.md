# Viewpoint Geometry Fix

Experimentelle Begleit-Mod für **Project Viewpoint** (First-Person-Renderer für
Project Zomboid Build 42.21). Ziel ist, falsch, unvollständig, verschoben oder
gar nicht gerenderte Dächer, Dachkanten, Wände und Objekte zu untersuchen und
später zu beheben.

**Aktueller Stand: 0.2.2-test – Diagnose + erster, schaltbarer Dach-Fix-Test.**
Ändert nichts an Gameplay, Savegames, `projectzomboid.jar` oder Viewpoint-Dateien.

### Dach-Fix (Test, vom Nutzer freigegeben am 2026-10-05)

Befund: In `tileGeometry.txt` des Spiels haben nur 84 von 1 426 Dach-Tiles eine
3D-Form; Viewpoint muss den Rest raten. Der Fix gibt Dach-Tiles ohne Form die
Form des gleichen Tiles aus dem Schwester-Tileset (`roofs_02…05 ← roofs_01`,
`roofs_30_02…10 ← roofs_30_01`). Zwei unabhängige Varianten:

* **B (Code):** ZombieBuddy-Advice auf `viewpoint.world.TileMeshes.geometryFor`
  – ersetzt nur ein *leeres* Ergebnis. Standard **an**; Schalter „Dach-Fix“ im
  Fenster oder `roofFixB=false` in `Zomboid\VPGeometryFix\config.properties`.
  Volle Wirkung nach Neustart des Spiels.
* **A (Datei):** `VPGF-RoofData.bat` (im Doctor-Zip) schreibt aus *deiner*
  Spieldatei eine Ergänzung nach `Zomboid\mods\ViewpointGeometryFix\42\media\tileGeometry.txt`.
  Entfernen: `VPGF-RoofData.bat -Remove`. Ob Spiel/Viewpoint Mod-Geometrie
  lesen, ist noch unbekannt – genau das wird getestet.

Getestet nur offline (Java-/Lua-/PowerShell-Tests mit Nachbauten), nicht im Spiel.

## Funktionen

* erkennt Project-Zomboid-Version, ZombieBuddy und Viewpoint (Klassen, JAR,
  `mod.info`, SHA-256 gegen auditierte Builds)
* schreibt beim Spielstart den eindeutigen Block `[VPGeometryFix] Loaded` …
* **Fenster im Spiel** (erscheint beim Laden eines Spielstands) mit Status
  (Java-Teil, PZ-, Viewpoint-, ZombieBuddy-Version) und Schaltflächen:
  Diagnose an/aus, Tile untersuchen, Ziel fixieren, Hover, Klasseninventar
* **Rechtsklick in die Welt → VPGeometryFix**: angeklicktes Tile untersuchen/fixieren
* **Keine Standard-Tasten** (die PZ-Debug-Version belegt F-Tasten u. a.);
  optional selbst belegbar unter Optionen → Tastenbelegung → `[VPGeometryFix]`
* Inspektion einer Tile-Säule z-1…z+2 mit Bericht nur der render-relevanten Felder
* sparsame Logs: Startblock + eine `TILE`-Zeile pro Objekt; Details nur in Dateien

Details: [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md) ·
Recherche: [docs/RESEARCH.md](docs/RESEARCH.md) ·
Architektur: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Wenn im Spiel nichts passiert: VPGF Doctor

Eigenständiges Prüfprogramm, läuft **außerhalb** des Spiels, nur lesend:

1. `build/dist/VPGF-Doctor.zip` irgendwohin entpacken (z. B. Desktop).
2. `VPGF-Doctor.bat` doppelklicken. Braucht **kein Java**, nur die in Windows
   enthaltene PowerShell; Steam wird über die Registry gefunden. Falls nicht →
   `VPGF-Doctor.bat -SteamLib "D:\SteamLibrary"`.
3. Es öffnet sich `VPGF-Report.txt`: Spielversion, ZombieBuddy (inkl. ob der
   `-javaagent` eingetragen ist), Viewpoint-Version und -Klassen, ob diese Mod
   am richtigen Ort liegt, die relevanten Zeilen aus `console.txt` und unter
   **ERGEBNIS** die gefundenen Probleme.
4. Diesen Bericht und `VPGF-Viewpoint-Geometry.txt` (Methoden-/Feldnamen der
   Viewpoint-Geometrie- und Sichtbarkeitsklassen, ab Doctor 0.3.0) an den
   Entwickler schicken – nicht öffentlich posten. `VPGF-Viewpoint-Classes.txt`
   (alle Klassennamen) bleibt lokal.

## Voraussetzungen

* Project Zomboid **42.21.x**
* **ZombieBuddy ≥ 2.3.4** (oder 2.3.2 mit dem 42.21-Fix) bzw. 3.x –
  2.3.2 ohne Fix lädt unter 42.21 keine Java-Mods
* Project Viewpoint (optional – ohne Viewpoint meldet die Mod `Viewpoint detected: no`)

## Installation (Windows 11)

1. Spiel schließen.
2. `build/dist/ViewpointGeometryFix-0.2.2-test.zip` nach
   `%USERPROFILE%\Zomboid\mods\` entpacken. Ergebnis:
   `%USERPROFILE%\Zomboid\mods\ViewpointGeometryFix\42\mod.info`.
3. Spiel starten → Mods → **Viewpoint Geometry Fix (Diagnostics)** aktivieren
   (ZombieBuddy muss aktiv sein).
4. ZombieBuddy fragt beim nächsten Start, ob das (unsignierte) JAR geladen
   werden darf → zulassen.
5. Spielstand laden: oben links erscheint das Fenster „Viewpoint Geometry Fix“.
   Zusätzlich steht in `%USERPROFILE%\Zomboid\console.txt` `[VPGeometryFix] Loaded`.

Deinstallation: Mod deaktivieren oder den Ordner löschen; zusätzlich ggf.
`%USERPROFILE%\Zomboid\VPGeometryFix\` (nur Logs/Berichte).

## Bauen

Nur ein JDK ≥ 17 nötig – kein Spiel-JAR, kein ZombieBuddy-JAR, kein Netz
(alle Spielzugriffe sind reflektiv; eine Compile-Stub-Annotation ersetzt
Kahluas `@LuaMethod` und wird nicht mitgepackt).

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File build\build.ps1            # baut build\out + build\dist\*.zip
powershell -ExecutionPolicy Bypass -File build\build.ps1 -Install   # zusätzlich nach %USERPROFILE%\Zomboid\mods
```

```bash
# Linux/macOS/Git-Bash
build/build.sh --test     # baut und führt alle Tests aus (Tests: JDK + python3 + `pip install lupa`)
```

Der Build ist reproduzierbar (feste Zeitstempel).

## Projektstruktur

```
README.md  CLAUDE.md  LICENSE  VERSION
src/main/java/vpgeometryfix/        Java-Teil (ZombieBuddy-Main, Lua-Bridge, diag/)
src/stubs/java/                     Compile-Stubs, nie im JAR
resources/mod/                      Mod-Layout (common/, 42/mod.info, 42/media/lua)
build/                              build.ps1, build.sh; out/ (ignoriert), dist/ (Release-Zip)
docs/                               Recherche, Architektur, Diagnose-Handbuch
tests/                              Java-Tests (ohne Spiel) und Lua-Tests (Lua 5.1 mit Mocks)
```

## Lizenz

MIT, siehe [LICENSE](LICENSE). Kein fremder Code übernommen; referenzierte
Projekte und ihre Lizenzen siehe [docs/RESEARCH.md](docs/RESEARCH.md).
