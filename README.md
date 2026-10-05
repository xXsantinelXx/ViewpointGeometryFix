# Viewpoint Geometry Fix

Experimentelle Begleit-Mod für **Project Viewpoint** (First-Person-Renderer für
Project Zomboid Build 42.21). Ziel ist, falsch, unvollständig, verschoben oder
gar nicht gerenderte Dächer, Dachkanten, Wände und Objekte zu untersuchen und
später zu beheben.

**Aktueller Stand: 0.1.2-diag – reine Diagnoseversion.**
Sie ändert **nichts** am Rendering, am Gameplay, an Savegames, an
`projectzomboid.jar` oder an Viewpoint-Dateien und installiert keine Patches.

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

## Voraussetzungen

* Project Zomboid **42.21.x**
* **ZombieBuddy ≥ 2.3.4** (oder 2.3.2 mit dem 42.21-Fix) bzw. 3.x –
  2.3.2 ohne Fix lädt unter 42.21 keine Java-Mods
* Project Viewpoint (optional – ohne Viewpoint meldet die Mod `Viewpoint detected: no`)

## Installation (Windows 11)

1. Spiel schließen.
2. `build/dist/ViewpointGeometryFix-0.1.2-diag.zip` nach
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
