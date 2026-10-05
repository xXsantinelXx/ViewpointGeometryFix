# Diagnose-Handbuch

## Startblock

Beim Spielstart (Lua `OnGameBoot`, also nachdem alle Mods inkl. Viewpoint
geladen sind) erscheint in `%USERPROFILE%\Zomboid\console.txt` genau einmal:

```
[VPGeometryFix] Java component loaded via ZombieBuddy (debug=false, source=default)   ← früher, beim Laden des JARs
...
[VPGeometryFix] Loaded
[VPGeometryFix] PZ version: 42.21.0 [projectzomboid.jar: projectzomboid.jar 42.21.0]
[VPGeometryFix] Viewpoint detected: yes (version: mod.info modversion=…, jar sha256=94fedda302ab... (Viewpoint 0.1.5a-hotfix); mod id=Viewpoint; activated in mod list=true)
[VPGeometryFix] ZombieBuddy detected: yes (version 2.3.4, javaagent=true, jar: not an audited build)
[VPGeometryFix] Debug mode: OFF (source: default)
```

Mit Debug an kommen `[VPGeometryFix] [debug] …`-Zeilen mit Pfaden und
vollständigen SHA-256-Werten dazu.

Fehlt die Java-Zeile, aber der Block erscheint mit `(from Lua)` /
`Java part NOT loaded`, hat ZombieBuddy das JAR nicht geladen (nicht
freigegeben, ZB < 2.3.4 ohne 42.21-Fix, oder ZB fehlt).

Kurzfilter (PowerShell):
```powershell
Select-String -Path "$env:USERPROFILE\Zomboid\console.txt" -Pattern '\[VPGeometryFix\]'
```

## Tasten (einzeln, ohne Strg/Umschalt; änderbar unter Optionen → Tastenbelegung → `[VPGeometryFix]`)

| Standardtaste | Wirkung | braucht Debug |
|---|---|---|
| Pos1 / Home | Debug-Modus an/aus | nein |
| Ende / End | Ziel inspizieren → Bericht-Datei | ja |
| Bild↑ / PageUp | Hover-Modus an/aus (Zusammenfassung bei Zielwechsel) | ja |
| Bild↓ / PageDown | Viewpoint-Klasseninventar schreiben | ja |

Rückmeldung erscheint oben links auf dem Bildschirm (zusätzlich in
console.txt). Beim Betreten eines Spielstands zeigt die Mod 10 Sekunden lang
„VPGeometryFix: aktiv …“ – fehlt diese Meldung, ist die Mod in diesem
Spielstand nicht aktiv (B42: Mods pro Spielstand prüfen). Bei aktivem Debug
steht oben links dauerhaft ein gelber Hinweis mit den Tasten.

Hinweis 0.1.0-diag: Dort waren es Strg+Umschalt+F8…F11. Diese Abfrage
(`isCtrlKeyDown`/`isShiftKeyDown`) war für 42.21 unverifiziert und hat beim
Nutzer nicht reagiert; ab 0.1.1-diag wird das in 42.21 belegte
PeekAView-Muster (`keyBinding`-Tabelle + `getCore():getKey`) verwendet.

Debug dauerhaft: `%USERPROFILE%\Zomboid\VPGeometryFix\config.properties`
mit `debug=true`, oder Startoption `-Dvpgf.debug=true`.

## Ziel (welches Tile wird untersucht?)

1. **Gepinnt**: `VPGF.setTarget(x, y, z)` – reproduzierbar, empfohlen für
   Fehlerberichte. `VPGF.clearTarget()` hebt auf.
2. **Viewpoint-Fadenkreuz**: noch **nicht** verfügbar – Viewpoints Pick-Ergebnis
   (`MousePick.read`) ist in seiner Speicherung unbekannt
   (`ViewpointProbe.pickedTarget()` ist ein Platzhalter).
3. **Maus**: nur wenn Viewpoint die First-Person-Ansicht **nicht** aktiv hat
   (`View.enabled=false`).
4. **Blickrichtung**: Tile `VPGF.facingDistance` (Standard 1) vor dem Spieler.

Untersucht wird immer eine Säule `z-1 … z+2` (`VPGF.columnBelow/Above`),
weil Dächer meist 1–2 Ebenen über dem Spieler liegen.

Koordinaten eines Tiles findet man z. B. im Vanilla-Debugmodus
(`-debug`) oder per `VPGF.inspect()` mit Blickrichtung und dann Pinnen.

## Bericht

`%USERPROFILE%\Zomboid\VPGeometryFix\inspect\inspect_<Zeit>_<x>_<y>_<z>.txt`:

* Kopf: Zeit, Grund/Quelle, Ziel, PZ-Version, Viewpoint-Version, Viewpoint-Status
  (`View.enabled`, `ThirdPerson.active`, `FreeCam.active`, `IrisPacks.active`, `Rooms.hiding`)
* je Ebene: `getObjects()`, `getSpecialObjects()`, `getMovingObjects()`,
  `getStaticMovingObjects()`, `getWorldObjects()` mit
  * Klasse + Sprite-Name + heuristische Art (`kind~ROOF|WALL|FLOOR|…`)
  * vollständigem Feld-Dump (Sprite, Properties, Texture bis Tiefe 2)
* alle Felder des `IsoGridSquare`

Die Konsole erhält eine Zusammenfassung pro Ebene.

## Reproduzierbares Vorgehen für einen Fehlerfall

1. Viewpoint-, ZB- und Spielversion aus dem Startblock notieren.
2. Im First-Person zum fehlerhaften Dach/zur Wand gehen; Pos1 (Debug an).
3. `VPGF.inspect()` (Ende) mit Blick auf das Objekt; Koordinaten aus der
   Konsolenzeile `inspect target X,Y,Z` notieren.
4. `VPGF.setTarget(X, Y, Z)` und aus mehreren Positionen/Blickwinkeln Ende –
   gleiche Säule, verschiedene Viewpoint-Zustände.
5. Zum Vergleich mit `O` in die isometrische Ansicht wechseln und erneut Ende.
6. Ein Fehlerbericht enthält: Startblock, Koordinaten, Screenshot FP und
   isometrisch, die Berichtsdateien.

## Klassen-Inventar

Bild↓/PageDown bzw. `VPGF.inventory("viewpoint")` /
`VPGF.inventory("game")` (Paket `zombie.iso.`) schreibt nach
`…\VPGeometryFix\inventory\`. Enthält nur Klassennamen und
Methoden-/Feldsignaturen (kein Bytecode). Klassen werden dafür ohne
Initialisierung geladen. **Diese Dateien beschreiben proprietären Code –
nicht veröffentlichen, nicht ins Repo committen** (`local/` ist ignoriert).

Gezielt Statik-Felder einer Klasse ansehen:
`VPGF.dumpStatics("viewpoint.visibility.Rooms")`.

## Einschränkungen

* Das Lesen statischer Viewpoint-Felder initialisiert die Klasse, falls sie es
  noch nicht war. Deshalb nur auf Tastendruck, nie beim Start.
* Die Art-Klassifikation basiert nur auf Namen; sie sagt nichts darüber, wie
  Viewpoint das Objekt einordnet.
* Lua-Mock-Tests (tests/lua) beweisen nicht, dass die echte Spiel-API identisch
  reagiert; die Spiel-API-Aufrufe sind deshalb alle mit `pcall` abgesichert.
* Nicht im Spiel getestet (in dieser Umgebung nicht möglich). Erster
  In-Game-Lauf durch den Nutzer steht aus.
