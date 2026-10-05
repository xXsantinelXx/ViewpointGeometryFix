# CLAUDE.md – Arbeitsregeln für dieses Repository

Lies zuerst README.md, docs/RESEARCH.md, docs/ARCHITECTURE.md.

## Harte Regeln

* Niemals `projectzomboid.jar`, Viewpoint- oder ZombieBuddy-Dateien,
  Savegames oder Spieleinstellungen verändern. Eingriffe nur zur Laufzeit.
* Keine globalen Gameplay-Änderungen. Normaler Spielbetrieb muss praktisch
  unverändert performant bleiben: keine Per-Frame-Arbeit ohne ausdrückliche
  Aktivierung durch den Nutzer.
* Rendering-Änderungen erst ab Phase 3 (siehe ARCHITECTURE.md) und nur, wenn
  der Nutzer es ausdrücklich freigibt. Freigegeben (2026-10-05): der schaltbare
  Dach-Fix (Varianten A und B, `fix/RoofFallback`, `Patch_RoofGeometry`,
  `VPGF-RoofData`). Zweite Freigabe (2026-10-05, Nutzer: „JA; ALLES FIXEN“):
  Dach-Korrekturen, die auch vorhandene Viewpoint-Ergebnisse ändern – gespiegelte
  hintere Dachhälften inkl. Mesh/Textur (`fix/RoofMirror`, `Patch_TileMeshCreate`,
  `Patch_Recipe*`), zugeschnittene Dachplatten und Giebelleisten
  (`fix/RoofShapes`). Nur für Dach-Sprites (`roofs_*`), alles schaltbar.
  Andere Eingriffe (Wände, Objekte, Sichtbarkeit) brauchen eine neue Freigabe.
* `@Patch`-Klassen (ZombieBuddy-2.x-API, Stub in `src/stubs`) müssen direkt im
  Paket `vpgeometryfix` liegen. Sie dürfen vorhandene Viewpoint-Ergebnisse nur
  im freigegebenen Umfang (Dächer, s. o.) und nur über Kopien ändern – nie
  Spiel- oder Viewpoint-Objekte selbst verändern – und müssen bei jedem
  Fehler das Original unverändert lassen.
* Nicht spekulieren: jede Aussage über Spiel-/Viewpoint-Interna mit
  Belegstufe [V1]/[V2]/[U]/[H] versehen (Definition in RESEARCH.md).
  Unverifizierte Namen nur reflektiv und abgesichert verwenden.
* Kein fremder Code ohne Lizenzprüfung. kilroy94/project-viewpoint-vr hat
  keine Lizenz → nur Fakten zitieren. Dekompilate, Klasseninventare und
  proprietäre JARs nie committen (Ordner `local/`).
* Spiel nicht starten und nichts in Spiel-/Workshop-/Zomboid-Ordner schreiben;
  In-Game-Tests macht der Nutzer.

## Code

* Java 17 (`--release 17`), Paket `vpgeometryfix`. Globale Lua-Funktionen
  (`@LuaMethod(global = true)`) müssen direkt in `vpgeometryfix` liegen.
* Keine Link-Abhängigkeit auf Spiel/Viewpoint/ZombieBuddy in der
  Diagnoseversion – alles über `diag/Reflect`. Jeder LuaBridge-Einstieg
  fängt `Throwable`.
* Logzeilen immer über `diag/Log` (Präfix `[VPGeometryFix] `). Der Startblock
  (`Loaded`, `PZ version`, `Viewpoint detected`, `ZombieBuddy detected`,
  `Debug mode`) ist eine stabile Schnittstelle – Wortlaut nicht ändern.
* Lua: Kahlua ≈ Lua 5.1; alle Spiel-API-Aufrufe mit `pcall` absichern.

## Prüfen vor jedem Commit

```bash
build/build.sh --test
```

Baut mit `-Xlint:all -Werror`, prüft dass keine Stubs ins JAR gelangen,
führt Java- (tests/java), Lua- (tests/lua, benötigt `pip install lupa`) und
Doctor-Tests (tests/doctor, benötigt `pwsh`; ohne `pwsh` übersprungen) aus.
`VPGF-Doctor.ps1` muss mit Windows PowerShell 5.1 laufen: kein `??`, kein
Ternary, kein `Join-Path` auf evtl. fehlende Laufwerke.
Nach Änderungen an `src/` oder `resources/` das Release-Zip in `build/dist/`
neu bauen und mitcommitten.
