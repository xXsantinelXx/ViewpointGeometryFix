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
