# Recherche: B42-Modding, ZombieBuddy, Viewpoint-Renderer

Stand: 2026-10-05. Ziel: Grundlage für einen späteren Geometrie-Fix. **Kein** Fix
ist implementiert.

## Belegstufen

Jede Aussage trägt eine Stufe. Bitte beim Weiterarbeiten beibehalten.

| Stufe | Bedeutung |
|---|---|
| **[V1]** | Selbst im Quellcode gelesen (öffentliches Repo, Lizenz geprüft). |
| **[V2]** | Sekundärquelle: Dritte haben die Binärdateien (Viewpoint 0.1.5a-hotfix, PZ 42.21.0) per Bytecode/Decompiler auditiert und Namen/Signaturen veröffentlicht. Von uns **nicht** gegen das Viewpoint-JAR verifiziert (kein Zugriff). |
| **[U]** | Unbekannt. Muss lokal mit dem Inventar-Werkzeug (Schaltfläche „Inventar“ im Mod-Fenster) ermittelt werden. |
| **[H]** | Hypothese, ausdrücklich unbelegt. |

## Quellen

| Quelle | Lizenz | Verwendung |
|---|---|---|
| [zed-0xff/ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) (Tags v2.3.2, v2.3.4, HEAD 3.0.0-beta1) | MIT | Loader-/API-Verhalten gelesen [V1]. Kein Code kopiert. |
| [zed-0xff/ZBHelloWorld](https://github.com/zed-0xff/ZBHelloWorld), [ZBBetterFPS](https://github.com/zed-0xff/ZBBetterFPS) | MIT | mod.info-/JAR-Layout, Mehr-JAR-Muster [V1]. |
| [armakupub/PeekAView](https://github.com/armakupub/PeekAView) | MIT | Vanilla-Render-Hookpunkte in 42.21 [V1]. Kein Code kopiert. |
| [kilroy94/project-viewpoint-vr](https://github.com/kilroy94/project-viewpoint-vr) | **keine Lizenzdatei** → nur Fakten zitiert, **nichts** übernommen | Audit von Viewpoint 0.1.5a-hotfix: Klassen-, Methoden- und Feldnamen, SHA-256-Pins [V2]. |
| [daaag0n00969/pz-42.21-compat](https://github.com/daaag0n00969/pz-42.21-compat) | MIT | API-Änderungen 42.21 (`loadMods(List)`, `getDoor(boolean)` entfernt) [V1 für die Doku, V2 für das Spiel]. |
| Viewpoint selbst | proprietär, Workshop; kein öffentlicher Quellcode gefunden | Beschreibung: eigener OpenGL-Renderer mit PBR-Pipeline, Tasten O / Umschalt+O / Entf ([Skymods](https://catalogue.smods.ru/archives/487860), [top-mods](https://top-mods.com/mods/project-zomboid/gameplay/16416-project-viewpoint.html)). |

## 1. B42-Modstruktur [V1]

```
Zomboid/mods/<ModId>/
├── common/                 (in B42 vorhanden, darf leer sein)
└── 42/                     (oder 42.21 usw. – versionsspezifisch)
    ├── mod.info
    └── media/
        ├── lua/{client,server,shared}/...
        └── java/client/<Mod>.jar      (nur mit ZombieBuddy)
```

mod.info-Felder für Java-Mods (ZombieBuddy-Doku `doc/ModdingGuide.md`):
`require=\ZombieBuddy`, `javaJarFile=`, `javaPkgName=` (Pflicht), optional
`ZBVersionMin/Max`, ab ZB 3 zusätzlich nummerierte Varianten (`javaJarFile1=`…).
Ein Pfad mit `media/java/client/` wird auf Dedicated Servern übersprungen.

## 2. Spielklassen (Vanilla, PZ 42.21)

| Klasse | Relevanz | Beleg |
|---|---|---|
| `zombie.iso.IsoCell` (`renderInternal`, `doBuildingInternal`, `update`) | Vanilla-Weltrender, Gebäude-/Cutaway-Ermittlung | [V1] PeekAView patcht diese Methoden in 42.21 |
| `zombie.iso.fboRenderChunk.FBORenderCell` (`renderInternal`, `isPotentiallyObscuringObject`, `renderPlayers`) | Vanilla-Chunk-FBO-Renderer | [V1] PeekAView |
| `zombie.iso.fboRenderChunk.FBORenderCutaways` (+ `$OrphanStructures`) | Wand-/Dach-Cutaway | [V1] PeekAView |
| `zombie.iso.fboRenderChunk.FBORenderTrees` | Baum-Rendering | [V1] PeekAView |
| `zombie.iso.IsoObject` (`getAlpha`, `getSprite`) | Basisklasse aller Tile-Objekte | [V1] PeekAView |
| `zombie.iso.IsoGridSquare` | Tile; `getObjects()`, `getSpecialObjects()`; `getDoor/getWindow/getWindowFrame(boolean)` wurden in 42.21 durch Varianten mit `GridSquareEdgeFacingDirection` ersetzt | [V1 Doku pz-42.21-compat] |
| `zombie.iso.IsoWorld` (`renderInternal`), `zombie.iso.IsoCamera` | Welt/Kamera | [V1] PeekAView |
| `zombie.iso.sprite.IsoSprite`, `IsoSpriteInstance` | Tile-Sprite (Name z. B. `roofs_01_12`), Properties | [V1] Import in PeekAView |
| `zombie.ui.UIManager.getTileFromMouse(…, …, double z)` | Maus→Tile (isometrisch) | [V1] PeekAView; volle Signatur [U] |
| `zombie.core.Core` `getVersion()` / `getGameVersion()` | Spielversion | [V1 Doku pz-42.21-compat] |
| `zombie.ZomboidFileSystem.loadMods(List<String>)` | in 42.21 von `ArrayList` auf `List` geändert → ZB 2.3.2 lädt ohne Fix keine Java-Mods | [V1] ZB v2.3.4-Quelle, pz-42.21-compat |
| Dach-spezifische Vanilla-Klasse (z. B. eigener „Roof“-Typ) | – | [U] – Dächer sind nach bisherigem Wissen normale `IsoObject`s mit Dach-Sprites; per Spiel-Inventar (`VPGF.inventory("game")`) prüfen |

## 3. Wie ZombieBuddy eingreift [V1]

* Java-Agent (`-javaagent`, `Premain-Class: me.zed_0xff.zombie_buddy.Agent`) mit
  ByteBuddy. Lädt die in `mod.info` deklarierten JARs während
  `ZomboidFileSystem.loadMods`.
* Pro Mod: optionales `<javaPkgName>.Main.main(String[])` wird aufgerufen.
* `@Patch(className, methodName)`-Klassen im Paket werden als ByteBuddy-Advice
  (OnEnter/OnExit, Argument-/Return-Rewrite, `skipOn`) oder Delegation
  angewendet – auf **beliebige** geladene Klassen, also auch auf Viewpoint-Klassen;
  bereits geladene Klassen werden retransformiert.
* Lua-Exposition: `@Exposer.LuaClass` oder statische Methoden mit
  `@LuaMethod(global = true)` **direkt im Paket `javaPkgName`** (nicht in
  Unterpaketen) – in v2.3.2 (`PatchEngine`) und v3 (`Exposer`) gleich.
* **API-Bruch 2.x → 3.x:** `@Patch` liegt in 2.x unter
  `me.zed_0xff.zombie_buddy.Patch`, in 3.x unter
  `me.zed_0xff.zombie_buddy.annotations.Patch` (+ `@Shadow`). Deshalb liefern
  ZB-Mods zwei JARs (`*-2x.jar` / `*.jar`). Unsere Diagnoseversion benutzt
  **keine** ZB-API und läuft daher unverändert mit 2.x und 3.x.
* Freigabe: unsignierte JARs muss der Spieler beim Start bestätigen
  (Policy `prompt`). Signatur (`.zbs`, Ed25519) ist optional.

## 4. Viewpoint-Renderarchitektur [V2]

Quelle aller Namen: Audit von Viewpoint **0.1.5a-hotfix**
(SHA-256 `94fedda3…cab41f`) in kilroy94/project-viewpoint-vr
(`docs/VIEWPOINT-PLAN.md`, `viewpoint/NATIVE-STAGES.md`,
`viewpoint/src/viewpointvr/instrument/*.java`, Test-Doubles in
`viewpoint/stage-fixtures`). Nicht stabile API; jede Viewpoint-Version kann
sie ändern.

```
Spiel-Thread                                   Render-Thread (SpriteRenderer)
────────────                                   ──────────────────────────────
viewpoint.FP.renderWorld                       viewpoint.SceneDrawer.render
  ├─ Sichtbarkeit (Produzent):                   └─ drawFrame
  │   world.ChunkWalk.inView(FFFFFF)Z                ├─ Retirement / Kamera wählen
  │   visibility.Rooms  (static boolean hiding)      ├─ render.WorldRenderer.begin(SceneData, Matrix4f view, x, y, z)
  │   models.ModelCull.wanted(IsoMovingObject,       │    ├─ PackLinks.follow, FrameStream.frameStarted
  │        IsoPlayer)Z                               │    ├─ TemporalPass.begin, FrameUniforms.set
  │   models.Characters.behind(IsoMovingObject,      │    ├─ FloorBakes.update(scene)   ← Böden
  │        FFFF)Z                                    │    ├─ ShellGround.update(scene)  ← Gebäudehülle/Boden?
  │   models.CorpseView.sees(FFF)Z                   │    ├─ PackModels.upload, Meshes.prepare(scene) ← Meshes
  │   core.CameraSquares                             │    ├─ ModelPass.prepare(scene)   ← 3D-Modelle
  └─ Snapshot in einen von 3 core.Frame             │    └─ drawWorld: FarPass.prepare(frame, shadow),
     → render.SceneData                              │         ShadowPass.draw, MousePick.read, WeatherMap.draw
     → SceneDrawer eingereiht                        ├─ drawOutlines / drawTranslucent / drawIndirect / drawWeather
                                                     ├─ finish → passes (Composite, TAA, Final), models.endFrame
                                                     └─ postRender: Charakter-/Modell-Ressourcen freigeben
```

Weitere Klassen [V2]: `viewpoint.core.View` (`static boolean enabled`),
`viewpoint.input.{Look, Camera, ThirdPerson, FreeCam}`,
`viewpoint.platform.{SettingsWindow, IrisPacks, HotReload, PackPass}`,
`viewpoint.render.{FrameContext, TemporalPass, Targets, Retirement, Latency}`,
`viewpoint.models.{Models, Corpses}`, `viewpoint.interact.LootPanel`,
`viewpoint.Hooks`, `viewpoint.platform.ImGuiFrame`.
Kamera: Welt-Verschiebung → Szene `(-dx, sqrt(6)*dz, -dy)`, Y-up, rechtshändig;
Near/Far 0.05/400 [V2].

### Zuständigkeit nach Geometrieart

| Geometrie | Kandidat | Stufe |
|---|---|---|
| Normale Boden-Tiles | `render.FloorBakes.update` | [V2] Name; Inhalt [U] |
| Dach-Tiles | **unbekannt**; wahrscheinlich im Mesh-/Hüllenaufbau (`Meshes.prepare`, `ShellGround`, `FarPass`-Shell) | [U]/[H] |
| Dachkanten | **unbekannt** | [U] |
| Wände | **unbekannt** (Mesh-Aufbau); Raum-Ausblendung über `visibility.Rooms.hiding` | [U] / Rooms [V2] |
| Objekte (Möbel, Fixtures) | **unbekannt**; vermutlich `Meshes` bzw. `PackModels` | [U] |
| 3D-Modelle (Charaktere, Leichen, Fahrzeuge?) | `render.ModelPass`, `models.Models`, `models.ModelCull`, `models.Characters`, `models.CorpseView` | [V2] |
| Fern-Geometrie | `render.FarPass` (Shell-/Cell-Uploads, Tree-Baking, Frustum/Masken) | [V2] |
| Chunk-Culling | `world.ChunkWalk.inView` | [V2] |

### Update 2026-10-05: Klassen aus dem installierten Viewpoint-JAR [V1]

Der VPGF Doctor hat beim Nutzer das JAR mit SHA-256 `94fedda3…` (= auditierter
Build 0.1.5a-hotfix) gelesen: 654 Klassen, Paket `viewpoint`, geladen über
ZombieBuddy 2.3.4 als Java-Mod mit eigenen `viewpoint.Patch_*`-Klassen
(u. a. `Patch_GameRender`, `Patch_ZombieCull`, `Patch_CullAnimals`,
`Patch_Pick*`). Viewpoint meldet selbst „compat: all 61 patch targets and 61
private members found“. **Existenz** der folgenden Klassen ist damit [V1];
ihre **Rolle** ergibt sich bisher nur aus dem Namen und bleibt [H], bis die
Signaturen (`VPGF-Viewpoint-Geometry.txt`, Doctor ≥ 0.3.0) vorliegen.

| Bereich | Klassen (Existenz [V1], Rolle [H]) |
|---|---|
| Nahwelt-Meshing (Kandidaten für Dach, Dachkante, Wand) | `world.WorldMesher`, `world.WallMesher`, `world.TileMesh`, `world.TileMeshes`, `world.MeshBuilder`, `world.MeshRecorder`, `world.Recipe`, `world.RecipeCodec`, `world.Cook`, `world.EarClip`, `world.Facades`, `world.FacadeColours` |
| Chunk-Verwaltung | `world.ChunkBuilds`, `world.ChunkCache`, `world.ChunkBudget`, `world.ChunkWalk`, `world.DrawList`, `world.MutationInbox` |
| Böden | `world.FloorGather`, `world.FloorSpans`, `world.FloorPages`, `world.FloorDecals`, `render.FloorBaker`, `render.FloorBakes`, `render.FloorLayers` |
| Modellpakete (Sprite → 3D-Modell, z. B. PZVoxelStudio) | `world.PackGather`, `packs.ModelPacks`, `render.PackModels`, `render.PackDraws`, `render.PackArena` |
| Sichtbarkeit | `visibility.Rooms`, `visibility.Portals`, `visibility.Apertures`, `visibility.Edges`, `visibility.Topology`, `visibility.SectorGraph`, `visibility.GraphBuilder`, `visibility.Selection`, `visibility.DrawPlans`, `visibility.Capture`, `visibility.Owner`, `visibility.Glass`, `visibility.Fragment` |
| Fernwelt (Gebäudehüllen inkl. Dächer in der Ferne) | `far.ShellMesher`, `far.ShellBlock`, `far.FarShell`, `far.FarMesher`, `far.FarTiles`, `far.ShellInteriors` |
| GPU-Seite | `render.Meshes`, `render.ChunkMeshData`, `render.MeshArena`, `render.ShellArena`, `render.SurfacePass` |

Die Fehlerbilder aus `docs/OBSERVATIONS.md` (Dachfläche zu hoch, Giebelkanten
ragen heraus, Dach fehlt, gestufte Vordächer) treten in der Nahwelt auf; erste
Kandidaten sind daher `world.WorldMesher`/`WallMesher`/`TileMesh(es)`/`Recipe`
[H]. Ob ein Modellpaket (PZVoxelStudio, Viewpoint2Dto3D) die Dach-Sprites
ersetzt, ist zu prüfen [U].

### Update 2026-10-05 (2): Nahwelt-Pipeline aus den Signaturen

Quelle: Methoden-/Feldsignaturen von 88 Klassen aus dem JAR des Nutzers
(Doctor 0.3.0). Die **Signaturen** sind [V1]; die **Abfolge** ist aus Namen und
Typen abgeleitet [H], aber in sich schlüssig:

```
ChunkWalk.walk / ChunkCache.update(Frame, IsoCell, int)
  → ChunkBuilds.queue / buildQueued / rebuild(Level, IsoChunk, level, …)
    → WorldMesher.gather(IsoChunk, level, …) : Recipe          (Hauptthread)
        → WorldMesher.square(IsoGridSquare, x, y, z, boolean[], boolean)
            → TileMeshes.get(IsoSprite, Texture, IsoSprite) : TileMesh  (Cache)
                → TileMeshes.create(…) → geometryFor(IsoSprite) : ArrayList
                    → TileMeshes.lookup(String, int)
                    → MeshBuilder.add(zombie.tileDepth.TileGeometryFile$Geometry)
                         → box(Box) / cylinder(Cylinder) / polygon(Polygon) → build() : TileMesh
                → (ohne Geometrie) TileMeshes.edgeQuads(IsoSprite) / wall(boolean…) / floor()
            → WallMesher.wall(IsoSprite, TileMesh, Texture, IsoGridSquare, x, y, z, boolean[])
            → WorldMesher.rise(IsoObject) : float, pixelScale(Texture), overlays(…)
            → Recipe.place / placeFace / placeCaps → Recipe$Op (EMIT, FACE, CAPS, RAW, PLANT, MODEL)
            → PackGather.object(…) (Modellpakete ersetzen Sprites durch 3D-Modelle)
  → Cook.mesh(Recipe) : ChunkMeshData                      (Cook-Threads)
  → Meshes.prepare / draw → GPU (MeshArena)
Sichtbarkeit: Rooms.plan(SceneData, …, ChunkMeshData, …) → DrawPlans.leaveOut(…)
Fernwelt:     FarTiles.classify(String) (Kinds u. a. ROOF, WALL) → ShellMesher.mesh(…) → FarShell
```

**Kernbefund [H, stark]:** Die 3D-Form eines Tiles (Dachfläche, Dachkante,
Giebel) stammt aus `zombie.tileDepth.TileGeometryFile` – den Tile-Geometrien,
die das Spiel in B42 für die Tiefensortierung der **isometrischen** Ansicht
nutzt. Diese Daten müssen nur aus der festen Iso-Kamera stimmen; aus der
Ich-Perspektive fallen Abweichungen auf. Das passt zu den Fehlerbildern A–D
(`OBSERVATIONS.md`): Dachflächen mit falscher Höhe, überstehende
Kanten-Polygone, fehlende Flächen (keine Geometrie → Rückfall auf
`edgeQuads`/leer), gestufte Vordächer.

Zu prüfen [U]: (1) welche Datei die Tile-Geometrie enthält und ob Mods sie
überschreiben können (Doctor 0.3.1 sucht danach), (2) was
`TileMeshes.geometryFor` für die betroffenen Dach-Sprites liefert
(Inspektion 0.1.4 schreibt es in jede TILE-Zeile: `vpGeom=…`).

### Update 2026-10-05 (3): Datenquelle gefunden [V1]

Doctor 0.3.1 beim Nutzer: Im Spiel liegen `media/tileGeometry.txt` (6,2 MB)
und `media/tileDepthTextureAssignments.txt` (1,7 MB) sowie der Debug-Editor
`media/lua/client/DebugUIs/TileGeometryEditor/*.lua`. **Kein** Workshop-Mod
(auch nicht Viewpoint2Dto3D/PZVoxelStudio) bringt eigene Dateien dieser Art mit.
Damit ist `tileGeometry.txt` die wahrscheinliche Quelle der Dach-/Kantenformen
in Viewpoint [H, stark]. Offen [U]: Dateiformat der Dach-Einträge und ob das
Spiel solche Dateien auch aus Mods lädt (Doctor 0.3.2, Abschnitt 4c).

**Update (Doctor 0.3.2, Abschnitt 4c) [V1]:** `tileGeometry.txt` hat das Format
`tileGeometry { VERSION = 2, tileset { name = …, /* sprite */ tile { xy = CxR,
box|polygon|cylinder { translate, rotate, min, max … }, properties { … } } } }`
(331 376 Zeilen). Die ersten Dach-Tiles (`roofs_03_0…4`) enthalten **nur**
`properties { OpaquePixelsOnly = true }` – **keine Form**. Der Debug-Editor
arbeitet pro Mod: `TileGeometryManager.getInstance():getModIDs()`,
`getTileGeometryState():fromLua1("writeGeometryFile", modID)`, Standard
`modID = "game"`. ⇒ (a) Für Dächer liefert `geometryFor` vermutlich nichts, und
Viewpoint baut sie über einen Rückfallweg [H] – Kandidat für Fehlerbilder A–D;
(b) das Spiel kennt Tile-Geometrie pro Mod, ein Daten-Fix per Mod ist damit
plausibel [H; ob Viewpoint Mod-Geometrie liest: U]. Doctor 0.3.3 zählt für
alle Dach-Tilesets, wie viele Tiles überhaupt eine Form haben.

Außerdem existiert `viewpoint.render.MousePick$Hit` [V1] – vermutlich das
Ergebnis von Viewpoints Fadenkreuz-Pick; Kandidat, um später das anvisierte
Tile direkt zu inspizieren.

Die Klassen, die aus einzelnen `IsoObject`/Sprite-Typen Dreiecke erzeugen
(„Mesh-Builder“ für Dach/Wand/Kante), sind in keiner öffentlichen Quelle
benannt. Das Diagnose-Werkzeug erzeugt dafür lokal ein Inventar
(`inventory_viewpoint.txt`: alle Klassennamen + Signaturen der Klassen mit
Schlüsselwörtern render/cull/visib/mesh/vertex/model/roof/wall/tile/…).

## 5. Andere B42-Mods, die Rendering/Sichtbarkeit per ZombieBuddy ändern [V1]

* **PeekAView** (MIT): Wand-Cutaway-Reichweite, Baum-Fade, Treppenansicht – patcht
  Vanilla-Renderer (Tabelle oben). Greift **nicht** in Viewpoint ein.
* **ZBBetterFPS** (MIT): Renderdistanz (`IsoChunkMap`), Shader/VBO/FBO-Patches.
* **project-viewpoint-vr** (ohne Lizenz): einziges gefundenes Projekt, das in
  den Viewpoint-Renderer eingreift – per eigenem ASM-Transformer mit
  SHA-256-Pins, Return-Wrappern an `ChunkWalk.inView`, `ModelCull.wanted`,
  `Characters.behind`, `CorpseView.sees` und an Lesezugriffen auf
  `Rooms.hiding`. Belegt, dass Viewpoints Sichtbarkeitsentscheidungen
  in genau diesen Methoden fallen [V2].
* **pz-42.21-compat**: Kompatibilitätspatches ZB 2.3.2 / PZ3D für 42.21.

## 6. Lua, Java oder beides?

* Lua allein reicht **nicht**: Viewpoint ist Java-Code mit eigenem
  OpenGL-Renderer; Lua sieht dessen Klassen nicht und kann keine Methoden
  umlenken.
* Java über ZombieBuddy ist **nötig** für jeden Fix.
* Empfohlen: **Kombination** – Java für Hooks/Analyse, Lua für Tasten,
  Optionen, Konsolen-API und als Fallback, wenn der Java-Teil nicht geladen ist.
  Genau so ist die Diagnoseversion gebaut.
