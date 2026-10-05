# Viewpoint Geometry Fix

Experimentelle Begleit-Mod für **Project Viewpoint** (First-Person-Renderer für
Project Zomboid Build 42.21). Ziel ist, falsch, unvollständig, verschoben oder
gar nicht gerenderte Dächer, Dachkanten, Wände und Objekte zu untersuchen und
später zu beheben.

**Aktueller Stand: 0.3.1-test – Dach-Fix (schaltbar) + Diagnose.**
Ändert nichts an Gameplay, Savegames, `projectzomboid.jar` oder Viewpoint-Dateien;
alle Korrekturen wirken nur zur Laufzeit in Viewpoints First-Person-Ansicht.

### Dach-Fix (vom Nutzer freigegeben am 2026-10-05)

Befund aus den Logs des Nutzers: Viewpoint baut Dächer aus den 3D-Formen, die das
Spiel nur als Tiefenhilfe für die Iso-Ansicht hat. Dabei

* fehlt bei steilen Dächern die **hintere Hälfte**: `roofs_*_8…13` haben keine Form
  (und praktisch keine Grafik, weil sie in der Iso-Ansicht genau auf der Kante stehen),
* sind die Dachplatten **2 × 2 Tiles** groß (Kreuz am First, hängende Ränder),
* liegen **Giebelleisten** auf 0,3 dicken senkrechten Platten 0,3 hinter der Giebelwand,
* leihen sich viele Tiles über `tileDepthTextureAssignments.txt` die Form eines anderen Tiles.

Der Fix (Schalter „Dach-Fix“ im Fenster, Standard **an**, volle Wirkung nach Neustart):

1. **Dachkacheln ohne Form:** `roofs_*_8…13` (und vom Spiel zugeordnete wie 67–69)
   bekommen die Form ihres Partners aus `roofs_*_0…5` (gleiche Neigung, eigenes Bild;
   Partner automatisch per Bildhöhe). Seit 0.3.1; 0.3.0 hatte gespiegelt
   (`roofBackMode=mirror`, nur noch als Option).
2. **Platten:** auf ihr Tile zugeschnitten.
3. **Giebelleisten:** dünn in die Giebelebene gelegt.
4. **Fix B:** leere Dach-Tiles der Farbvarianten bekommen die Form aus `roofs_01`/`roofs_30_01`.

Einzelschalter in `Zomboid\VPGeometryFix\config.properties`: `roofFixB` (alles),
`roofFixMirror` (Kacheln 8–13), `roofFixClip`, `roofFixTrim`, `roofBackMode=same|mirror`,
`roofFixMesh` (nur für `mirror`); Zuordnung der
Hälften `roofBackMap=8=0z,9=1z,10=2z,11=3x,12=4x,13=5x` (leer = automatisch aus den
Bildhöhen der Sprites).
Variante A (`VPGF-RoofData.bat`) brachte nachweislich nichts und kann entfernt werden.

Getestet offline (Java-/Lua-/PowerShell-Tests; Einweben mit dem echten
ZombieBuddy-2.3.4-PatchEngine gegen Nachbauten der Viewpoint-Signaturen), noch nicht
im Spiel.

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
2. `build/dist/ViewpointGeometryFix-0.3.1-test.zip` nach
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
