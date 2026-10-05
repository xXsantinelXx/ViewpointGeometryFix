# Architektur: Viewpoint Geometry Fix

## Leitplanken

* Keine Änderung an `projectzomboid.jar`, Viewpoint-Dateien, Savegames oder
  Spieleinstellungen. Alles läuft zur Laufzeit im Speicher.
* Keine globalen Gameplay-Änderungen.
* Normaler Spielbetrieb praktisch unverändert: keine Patches, keine
  Per-Frame-Arbeit in der Diagnoseversion.
* Jeder Zugriff auf Spiel- und Viewpoint-Klassen erfolgt reflektiv; ein
  fehlendes Mitglied ergibt „unknown“, nie eine Exception im Spiel.

## Phasen

| Phase | Inhalt | Status |
|---|---|---|
| 0 Diagnose | Erkennung, Logzeilen, Debug-Modus, Inspektion, Klassen-Inventar | **diese Version** |
| 1 Lokalisierung | Mit Inventar + Inspektionsberichten den Mesh-Builder und Culling-Pfad für Dächer/Kanten/Wände identifizieren; reproduzierbare Testorte (X/Y/Z) sammeln | offen |
| 2 Beobachtung | Nur-lesende ZombieBuddy-Advices (`@Patch.OnExit`, Rückgabe unverändert) an den identifizierten Methoden; zählt/protokolliert Entscheidungen für ein Ziel-Tile | offen |
| 3 Fix | Minimale, versionsgeprüfte Eingriffe (Rückgabewert-Korrektur oder Mesh-Ergänzung), schaltbar | offen |

## Komponenten (Phase 0)

```
Lua  (media/lua/client/VPGeometryFix_Main.lua)
 ├─ Events.OnGameBoot  → Startblock (einmal)
 ├─ Events.OnGameStart → Fenster (ISPanel + ISButton), Rückfall: Text-Einblendung (OnPostUIDraw)
 ├─ Events.OnFillWorldObjectContextMenu → Rechtsklick-Menü „VPGeometryFix“
 ├─ Events.OnKeyPressed → nur optional selbst belegte Tasten (Standard: keine)
 ├─ Zielermittlung: gepinnt > Viewpoint-Pick [U, TODO] > Maus (nur isometrisch) > Blickrichtung
 ├─ Hover-Modus: OnTick nur solange aktiv, gedrosselt, nur bei Zielwechsel
 └─ Fallback ohne Java: Startblock + Lua-Zusammenfassung
        │ globale Funktionen VPGF_* (von ZombieBuddy registriert)
Java (vpgeometryfix, JAR ohne Link-Abhängigkeiten)
 ├─ Main            ZB-Einstieg: Config laden, eine Logzeile
 ├─ LuaBridge       @LuaMethod(global=true)-Fassade, fängt alle Fehler
 └─ diag/
     ├─ Env            PZ-Version, ZombieBuddy, Viewpoint (Klassen ohne Init, JAR, mod.info, SHA-256)
     ├─ KnownBinaries  SHA-256-Pins (PZ 42.21.0, Viewpoint 0.1.5a-hotfix, ZB 2.3.2)
     ├─ SquareInspector Bericht je Inspektion (Säule z-1..z+2)
     ├─ ObjectDumper   reflektiver, nur-lesender Feld-Dump mit Tiefen-/Zeilenlimit
     ├─ Classifier     heuristische Tile-Art aus Klasse + Sprite-Name
     ├─ ViewpointProbe Viewpoint-Statusfelder (on demand), Pick-Platzhalter
     ├─ ClassInventory Klassenliste + Signaturen eines JARs (lokal, nicht veröffentlichen)
     ├─ MeshProbe      (0.2.3) Viewpoint-Konstanten, Projektionstest, Mesh-Cache-Auszug – nur lesend, auf Knopfdruck
     ├─ GeometrySource (0.2.3) Herkunft der Dachformen (eigene / zugeordnete / nur Viewpoint)
     ├─ Config / Log / Paths / Reflect
```

Ausgabe: Konsole (`console.txt`) mit Präfix `[VPGeometryFix]` sowie
`<Zomboid>/VPGeometryFix/` (`VPGeometryFix.log`, `inspect/`, `inventory/`,
optional `config.properties`).

## Dach-Fix (0.3.0-test, vom Nutzer freigegeben)

Alle Teile laufen nur während Viewpoint Meshes baut (Ladephase/neue Chunks),
nie pro Frame, und arbeiten auf Kopien; jeder Fehler lässt Viewpoints Ergebnis stehen.

| Teil | Eingriff [Signatur V1] | Wirkung |
|---|---|---|
| Fix B | `Patch_RoofGeometry` → `TileMeshes.geometryFor(IsoSprite)` OnExit | leere Farbvarianten bekommen die Schwester-Form |
| Platten | dito, `RoofShapes.clipSlab` | 2×2-Platten auf ±0,5 × ±0,5/cos(Neigung) zugeschnitten |
| Giebelleisten | dito, `RoofShapes.thinCard` | 0,3-Platte → 0,04-Karte in der Giebelebene (z/x = −0,5) |
| Hintere Hälften (Form) | dito, `RoofShapes.mirror` | leere `roofs_*_8…13` ← gespiegelte Vorderhälfte (Rückfall) |
| Hintere Hälften (Mesh) | `Patch_TileMeshCreate` → `TileMeshes.create(IsoSprite, Texture, IsoSprite)` OnExit | Mesh der Vorderhälfte bauen, Positionen/Normalen spiegeln, Dreiecke umdrehen |
| Hintere Hälften (Bild) | `Patch_Recipe{Place,PlaceFace,PlaceCaps,Placed}` → `Recipe.place*` OnEnter | für diese Meshes Seite (`TextureID`) und Abbildung der Vorder-Textur einsetzen |

`TileMesh`/`TextureID` sind Compile-Stubs (ByteBuddy braucht für schreibbare
Rückgabe/Argumente den exakten Typ). ZombieBuddy 2.3.4 behandelt beim
Methoden-Abgleich einen Array-Parameter (`float[] map`) wie seinen Elementtyp und
würde die `Recipe.place*`-Advices deshalb nie anwenden [V1, PatchEngine-Quelle,
MIT]; jede `Patch_Recipe*`-Klasse hat daher zusätzlich ein leeres OnExit mit
abgleichbarer Signatur. Offline geprüft mit dem unveränderten ZombieBuddy-2.3.4-
`PatchEngine` (ByteBuddy 1.18.8) gegen Nachbauten mit den echten Signaturen:
Mesh gespiegelt, alle vier `place*` tauschen die Textur, fremde Meshes unberührt.

Sicherungen: Der Mesh-Teil greift nur, wo der Form-Teil die leere hintere Hälfte
gefüllt hat; das Vorder-Mesh muss auf die Tile-Mitte zentriert sein; gleiche Länge
und Eckenzahl wie Viewpoints eigene Meshes; schwache Identitäts-Schlüssel (kein
Speicherleck). Die Zuordnung 8–13 → 0–5 wird je Tileset aus der Höhe der Bild-
streifen bestimmt (sonst Standard), Tiles mit Zuordnung auf 8–13 in
`tileDepthTextureAssignments.txt` (z. B. 67 → 11) werden mitbehandelt.

## VPGF Doctor (außerhalb des Spiels)

`resources/doctor/VPGF-Doctor.ps1` + `.bat` → `build/dist/VPGF-Doctor.zip`.
Windows-PowerShell-5.1-kompatibel, kein Java. Nur lesend; findet Steam per
Registry/`libraryfolders.vdf`/feste Laufwerke, prüft `projectzomboid.jar`
(SHA-256), `-javaagent` in `ProjectZomboid64.json`, ZombieBuddy, Viewpoint
(mod.info, JAR-SHA-256, Klassennamen aus dem JAR-Verzeichnis), die
Installation dieser Mod und `console.txt`. Unabhängig von Mod-Laden,
ZombieBuddy und UI. Test: `tests/doctor/test_doctor_ps1.py` (PowerShell 7).

## Eingriffspunkte für den späteren Fix

**Stand 2026-10-05:** Mit den Signaturen aus dem Viewpoint-JAR (RESEARCH.md,
„Nahwelt-Pipeline“) ist der wahrscheinlichste Ansatzpunkt die Quelle der
Tile-Formen:

* **0. Daten statt Code [U]:** Falls die Spiel-Tile-Geometrie
  (`zombie.tileDepth`) aus Mod-Dateien ergänzt/überschrieben werden kann,
  lassen sich falsche Dach-/Kanten-Formen als Datenmod korrigieren – ohne
  Java-Hook. Wird zuerst geprüft.
* **Konkreter Vorschlag (2026-10-05, freigegeben und als 0.2.0-test umgesetzt):** Für
  Dach-Tiles ohne Form die Form des gleichen Tiles (gleiches `xy`) aus dem
  Schwester-Tileset übernehmen: `roofs_02…05 ← roofs_01`,
  `roofs_30_02…10 ← roofs_30_01`. Variante A als Daten-Mod
  (`media/tileGeometry.txt` in dieser Mod, nur wenn Spiel *und* Viewpoint
  Mod-Geometrie lesen), Variante B als ZombieBuddy-Advice auf
  `TileMeshes.geometryFor` (nur wenn das Ergebnis leer ist, abschaltbar).
* **0b. `viewpoint.world.TileMeshes.geometryFor(IsoSprite)` [V1-Signatur]:**
  `@Patch.OnExit` mit `@Patch.Return(readOnly = false)` liefert für bekannte
  Problem-Sprites korrigierte Geometrie. Klein, gezielt, pro Sprite.
* **0c. `WorldMesher.rise(IsoObject)` [V1-Signatur]:** falls Fehlerbild A
  (schwebende Dachfläche) eine falsche Anhebung ist.

Danach die ursprüngliche Liste, alle über ZombieBuddy-Advice auf
Viewpoint-Klassen (zur Laufzeit, keine Dateiänderung):

1. **Produzentenseitige Sichtbarkeit** [V2-Namen]:
   `viewpoint.world.ChunkWalk.inView(FFFFFF)Z`,
   `viewpoint.visibility.Rooms` (`hiding`),
   `viewpoint.models.ModelCull.wanted`, `Characters.behind`, `CorpseView.sees`.
   Symptom „Dach/Wand fehlt komplett, abhängig von Blickrichtung/Raum“ → hier.
   Mechanik: `@Patch.OnExit` + `@Patch.Return(readOnly = false)`.
2. **Mesh-/Geometrieaufbau** [U]: Klasse(n) hinter `Meshes.prepare`,
   `FloorBakes`, `ShellGround`, `FarPass`-Shell. Symptom „Tile falsch, verschoben,
   unvollständig, Dachkante fehlt“ → Sprite-/`IsoObjectType`-Zuordnung oder
   Höhen-/Offset-Berechnung. Mechanik: Advice an der Erzeugungsmethode je
   Objekt/Sprite, ggf. `@Patch.Argument(readOnly=false)`.
3. **Snapshot** (`FP.renderWorld`) [V2]: falls Objekte gar nicht erst in
   `SceneData` landen.
4. **Nicht** in den GL-Passes von `WorldRenderer` – zu spät, zu fragil.

### Hook-Technik

* Bevorzugt: ZombieBuddy-**Advice** (`@Patch.OnEnter/OnExit`), verkettbar mit
  anderen Mods; keine Delegation (nur eine pro Methode möglich).
* Für private Viewpoint-Felder: ZB 3 `@Shadow` / `@Patch.VarHandle`
  bzw. Reflexion (ZB 2.x).
* Ein eigener ASM-Transformer (wie project-viewpoint-vr) ist nur nötig,
  wenn eine Änderung **mitten** in einer Methode erfolgen muss (Call-Site-Umleitung).
  Kein dauerhafter Bytecode-Patch auf Platte.
* Jeder Hook prüft beim Aktivieren die Viewpoint-JAR-SHA-256 gegen eine
  auditierte Liste und deaktiviert sich bei unbekannter Version.
* 2.x/3.x-Problem: Hooks erfordern zwei JAR-Varianten (wie ZBHelloWorld) oder
  `ZBVersionMin=3.0.0`.
