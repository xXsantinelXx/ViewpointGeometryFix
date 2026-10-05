# Diagnose-Handbuch (0.2.0-test)

## Zuerst: VPGF Doctor (außerhalb des Spiels)

Wenn im Spiel nichts zu sehen ist, `VPGF-Doctor.bat` aus
`build/dist/VPGF-Doctor.zip` starten (siehe README; PowerShell, kein Java nötig). Der Bericht
zeigt, ob das Spiel die Mod überhaupt lädt, ob ZombieBuddy aktiv ist und
welche Viewpoint-Klassen für Geometrie in Frage kommen – ganz ohne Tasten,
UI oder ZombieBuddy. Die Klassenliste sind nur die Eintragsnamen im
Viewpoint-JAR (ZIP-Verzeichnis, kein Dekompilieren).

## Ist die Mod aktiv?

1. **Hauptmenü:** oben links steht gelb „VPGeometryFix 0.2.0-test geladen – Java-Teil: OK“
   (oder „NICHT geladen“, dann lädt ZombieBuddy das JAR nicht).
2. **console.txt:** erste Zeile der Mod ist `[VPGeometryFix] Lua loaded 0.2.0-test`.
   Fehlt sie, wird die Mod gar nicht geladen (Ordnerstruktur / Mod nicht aktiviert).
   Fehler der Mod erscheinen als `[VPGeometryFix] ERROR in …`.
3. **Im Spielstand:** das Fenster (unten).

Beim Laden eines Spielstands öffnet sich oben links das Fenster
**„Viewpoint Geometry Fix 0.2.0-test“** (verschiebbar, mit X schließbar).
Es zeigt:

```
Java-Teil: OK                         ← rot „NICHT geladen“, wenn ZombieBuddy das JAR nicht lädt
PZ: 42.21.0
Viewpoint: erkannt 0.1.5a-hotfix (auditierter Build)
ZombieBuddy: 2.3.4
Diagnose: AUS
[Diagnose AN] [Tile untersuchen] [Ziel fixieren] [Hover AN] [Inventar] [X]
```

Erscheint das Fenster nicht, ist die Mod in diesem Spielstand nicht aktiv
(B42: Mod-Liste pro Spielstand prüfen). Falls die Spiel-UI-Klassen
`ISPanel`/`ISButton` nicht verfügbar sind, erscheint derselbe Text als
einfache Einblendung ohne Schaltflächen.

Fenster wieder öffnen: **Rechtsklick in die Welt → VPGeometryFix → Fenster öffnen**
oder in der Debug-Konsole `VPGF.showPanel()`.

## Bedienung – keine Standard-Tasten

Die Debug-Version von Project Zomboid belegt F-Tasten und weitere Tasten
selbst. Deshalb hat die Mod **keine voreingestellten Tasten**.

| Weg | Wirkung |
|---|---|
| Fenster-Schaltflächen | Diagnose an/aus, Tile vor dem Spieler (bzw. fixiertes Ziel) untersuchen, Ziel fixieren/lösen, Hover, Viewpoint-Klasseninventar |
| Rechtsklick in die Welt → VPGeometryFix | Fenster öffnen; „Dieses Tile untersuchen (x,y,z)“; „Dieses Tile als Ziel fixieren“ |
| Optionen → Tastenbelegung → `[VPGeometryFix]` | optional selbst belegen: „VPGF Panel“ (Fenster ein/aus), „VPGF Inspect Target“ |
| Debug-Konsole (PZ mit `-debug`) | `VPGF.showPanel()`, `VPGF.setDebug(true)`, `VPGF.inspect()`, `VPGF.inspectAt(x,y,z)`, `VPGF.setTarget(x,y,z)`, `VPGF.clearTarget()`, `VPGF.inventory("viewpoint"\|"game")`, `VPGF.viewpointState()`, `VPGF.dumpStatics("viewpoint.core.View")` |

In der Viewpoint-First-Person-Ansicht ist die Maus gefangen. Zum Klicken mit
**O** in die normale Ansicht wechseln, dann zurück. Das „Tile vor dem Spieler“
bleibt dabei dasselbe; ein fixiertes Ziel sowieso.

## Was wird geloggt (bewusst wenig)

**console.txt** bekommt nur:

1. den Startblock (einmal pro Spielstart):
   ```
   [VPGeometryFix] Loaded
   [VPGeometryFix] PZ version: 42.21.0 [projectzomboid.jar: projectzomboid.jar 42.21.0]
   [VPGeometryFix] Viewpoint detected: yes (version: …; mod id=Viewpoint; activated in mod list=true)
   [VPGeometryFix] ZombieBuddy detected: yes (version 2.3.4, javaagent=true, jar: …)
   [VPGeometryFix] Debug mode: OFF (source: default)
   ```
2. pro Untersuchung je Objekt eine Zeile plus den Berichtspfad:
   ```
   [VPGeometryFix] TILE 101,200,1 Objects#0 IsoObject sprite=roofs_01_12 kind~ROOF spriteType=WestRoofB
   [VPGeometryFix] TILE 101,200,2 empty
   [VPGeometryFix] report -> C:\Users\…\Zomboid\VPGeometryFix\inspect\inspect_…_101_200_0.txt
   ```
3. Fehler.

Pfade, SHA-256-Werte, JVM-Version usw. stehen nur in
`%USERPROFILE%\Zomboid\VPGeometryFix\VPGeometryFix.log`. Hover schreibt
nichts ins Log, nur ins Fenster.

Kurzfilter (PowerShell):
```powershell
Select-String -Path "$env:USERPROFILE\Zomboid\console.txt" -Pattern '\[VPGeometryFix\]'
```

## Dach-Fix testen (0.2.0-test)

1. Spiel starten, Spielstand laden. Fensterzeilen „Dach-Fix B (Code): AN, Patch
   aktiv, ersetzt N Formen“ – N > 0 heißt: Viewpoint hat für Dächer ohne Form die
   Schwester-Form bekommen. „Patch noch nicht aufgerufen“ nach einer Weile heißt:
   ZombieBuddy hat den Patch nicht angewendet (console.txt: `patching
   viewpoint.world.TileMeshes.geometryFor`).
2. Vorher/Nachher vergleichen: Schalter „Dach-Fix AUS“ + Spiel neu starten, dieselbe
   Stelle ansehen; dann wieder AN.
3. Variante A getrennt testen: B ausschalten, `VPGF-RoofData.bat` ausführen,
   Spiel neu starten. Zeile „Dach-Fix A (Datei): vorhanden“.
4. In der TILE-Zeile zeigt `fixB=an<-roofs_01_5`, welches Schwester-Tile verwendet
   wird.

## Bericht

`%USERPROFILE%\Zomboid\VPGeometryFix\inspect\inspect_<Zeit>_<x>_<y>_<z>.txt`
für die Säule z-1 … z+2 (Dächer liegen meist 1–2 Ebenen über dem Spieler):

* Kopf: Zeit, Quelle, Ziel, PZ- und Viewpoint-Version, Viewpoint-Status
  (`View.enabled`, `ThirdPerson.active`, `FreeCam.active`, `IrisPacks.active`, `Rooms.hiding`)
* je Ebene alle Objekte (`getObjects`, `getSpecialObjects`, bewegliche/Welt-Objekte
  nur als Anzahl in der Konsole) mit Klasse, Sprite, heuristischer Art, Sprite-Typ
* **nur render-relevante Felder** (Name enthält alpha, offset, sprite, type, roof,
  wall, hid, visib, cutaway, render, overlay, attach, child, dir, north, height,
  solid, flag, prop, name, room, building, outside, light oder ist x/y/z)
* Zusammenfassung (die TILE-Zeilen) am Ende

Mit Viewpoint enthält jede TILE-Zeile zusätzlich `vpGeom=N(Formen)` und
`rise=…`: die Tile-Geometrie, die Viewpoint für diesen Sprite verwendet
(`TileMeshes.geometryFor`), und die Anhebung (`WorldMesher.rise`). Der Bericht
listet die Formen mit ihren Werten. Klassen der Spiel-Geometrie:
`VPGF.inventory("zombie.tileDepth.")`.

Alle Felder: `fullDump=true` in `%USERPROFILE%\Zomboid\VPGeometryFix\config.properties`.

## Reproduzierbares Vorgehen für einen Fehlerfall

1. Startblock (oder Fensterkopf) notieren: PZ-, Viewpoint-, ZB-Version.
2. In First-Person vor das fehlerhafte Dach / die Wand stellen, Blick darauf.
3. Mit **O** in die normale Ansicht, im Fenster **Diagnose AN**, dann
   **Ziel fixieren** (fixiert das Tile vor dem Spieler) – oder direkt
   Rechtsklick auf das Tile → „Dieses Tile als Ziel fixieren“.
4. **Tile untersuchen**. Zurück in First-Person (O), aus anderen Positionen/
   Blickwinkeln erneut untersuchen (über eine selbst belegte Taste oder
   kurz O → Klick → O).
5. Fehlerbericht: Startblock, Koordinaten, Screenshot FP und isometrisch,
   die Berichtsdateien.

## Klassen-Inventar

Schaltfläche **Inventar** bzw. `VPGF.inventory("viewpoint")` /
`VPGF.inventory("game")` (Paket `zombie.iso.`) schreibt nach
`…\VPGeometryFix\inventory\`. Enthält nur Klassennamen und
Methoden-/Feldsignaturen (kein Bytecode); Klassen werden ohne
Initialisierung geladen. **Diese Dateien beschreiben proprietären Code –
nicht veröffentlichen, nicht ins Repo committen** (`local/` ist ignoriert).

## Debug-Modus dauerhaft

`config.properties` mit `debug=true` oder Startoption `-Dvpgf.debug=true`.
„Diagnose AN/AUS“ im Fenster schaltet denselben Modus zur Laufzeit.

## Einschränkungen

* Viewpoints eigenes Fadenkreuz-Ziel ist noch unbekannt [U]; es wird das
  Tile vor dem Spieler bzw. ein fixiertes/angeklicktes Ziel untersucht.
* Lesen statischer Viewpoint-Felder initialisiert die Klasse, falls nötig –
  deshalb nur bei einer Untersuchung, nie beim Start.
* Die Art-Klassifikation basiert nur auf Namen.
* `ISPanel`/`ISButton`/`ISContextMenu`/`OnFillWorldObjectContextMenu` sind
  Vanilla-UI-API, für 42.21 aber nicht von uns verifiziert [H]; alle Aufrufe
  sind abgesichert, mit Text-Einblendung als Rückfall.
* Lua-Tests laufen gegen nachgebaute Spielfunktionen, nicht gegen das Spiel.

## Versionshistorie der Bedienung

* 0.1.0: Strg+Umschalt+F8…F11 – reagierte nicht (Modifier-Abfrage für 42.21 unverifiziert).
* 0.1.1: Pos1/Ende/Bild↑/Bild↓ – kollidiert mit Tasten der PZ-Debug-Version.
* 0.1.2: Fenster + Rechtsklick-Menü, Tasten nur optional und unbelegt; Logs reduziert.
