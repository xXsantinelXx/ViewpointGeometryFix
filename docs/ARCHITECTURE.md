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
 ├─ Events.OnKeyPressed → Strg+Umschalt+F8/F9/F10/F11
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
     ├─ Config / Log / Paths / Reflect
```

Ausgabe: Konsole (`console.txt`) mit Präfix `[VPGeometryFix]` sowie
`<Zomboid>/VPGeometryFix/` (`VPGeometryFix.log`, `inspect/`, `inventory/`,
optional `config.properties`).

## Eingriffspunkte für den späteren Fix

Reihenfolge nach Wahrscheinlichkeit, alle über ZombieBuddy-Advice auf
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
