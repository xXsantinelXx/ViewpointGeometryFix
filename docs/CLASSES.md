# Class and method inventory

Status values: **verified** — the name exists in a source checked on
2026-10-05 (named per row). **reported** — a third party documents it from a
JAR audit, not confirmed here. **unknown** — not established; do not build on
it without checking first.

## Viewpoint (mod id `Viewpoint`, version 0.1.5a-hotfix)

Source for every row: the published binary contract of
[project-viewpoint-vr](https://github.com/kilroy94/project-viewpoint-vr)
(`viewpoint/inspect_contract.py`, `src/viewpointvr/instrument/*.java`,
`NATIVE-STAGES.md`). Status **reported** throughout; version 0.1.0 of this mod
confirms presence of the class names at runtime and reports the result.

### Entry and frame lifecycle

| Class | Member | Status |
|---|---|---|
| `viewpoint.FP` | `renderWorld` (snapshot + enqueue) | reported |
| `viewpoint.SceneDrawer` | `private void drawFrame()` | reported |
| `viewpoint.SceneDrawer` | `public void postRender()` | reported |
| `viewpoint.core.Frame` | frame record passed to release calls | reported |
| `viewpoint.render.SceneData` | captured scene argument type | reported |
| `viewpoint.render.Retirement` | `collect()`, `drawn(J)` | reported |

### World renderer

| Class | Member (descriptor) | Status |
|---|---|---|
| `viewpoint.render.WorldRenderer` | `begin(Lviewpoint/render/SceneData;Lorg/joml/Matrix4f;FFF)Z` | reported |
| `viewpoint.render.WorldRenderer` | `private static void drawWorld()` | reported |
| `viewpoint.render.WorldRenderer` | `private static void passes()` | reported |
| `viewpoint.render.WorldRenderer` | `finish(Lviewpoint/render/SceneData;)V` | reported |
| `viewpoint.render.FarPass` | `prepare(Lviewpoint/render/FrameContext;Z)V`, `gbuffer(…)` | reported |
| `viewpoint.render.TemporalPass` | `begin`, `resolve`, `remember`, `present` | reported |
| `viewpoint.render.FrameContext` | saved framebuffer / viewport / scissor state | reported |
| `viewpoint.render.Meshes` | `prepare(Lviewpoint/render/SceneData;)V` | reported |
| `viewpoint.render.ModelPass` | `prepare`, `gbuffer`, `endFrame` | reported |
| `viewpoint.render.ShadowPass` | `draw(Lviewpoint/render/FrameContext;)V` | reported |
| `viewpoint.render.FrameStream` | `frameStarted()V` | reported |
| `viewpoint.render.FrameUniforms` | `set(FrameContext, Targets)V` | reported |

### Visibility and culling — the primary suspects for missing geometry

| Class | Member (descriptor) | Status |
|---|---|---|
| `viewpoint.world.ChunkWalk` | `inView(FFFFFF)Z` | reported |
| `viewpoint.visibility.Rooms` | static field `hiding` `Z` | reported |
| `viewpoint.models.ModelCull` | `wanted(Lzombie/iso/IsoMovingObject;Lzombie/characters/IsoPlayer;)Z` | reported |
| `viewpoint.models.Characters` | `behind(Lzombie/iso/IsoMovingObject;FFFF)Z`, `release(Frame)` | reported |
| `viewpoint.models.CorpseView` | `sees(FFF)Z` | reported |
| `viewpoint.models.Models` | `release(Lviewpoint/core/Frame;)V` | reported |
| `viewpoint.models.Corpses` | `release(Lviewpoint/core/Frame;)V` | reported |

### Camera, input, platform

| Class | Member | Status |
|---|---|---|
| `viewpoint.input.Look` | yaw/pitch from the late mouse read | reported |
| `viewpoint.input.Camera` | `eye` anchor | reported |
| `viewpoint.input.FreeCam`, `viewpoint.input.ThirdPerson` | camera modes | reported |
| `viewpoint.core.View`, `viewpoint.core.CameraSquares` | view state | reported |
| `viewpoint.platform.SettingsWindow` | `panel(String, Runnable)` registration | reported |
| `viewpoint.platform.ImGuiFrame`, `PerformanceOverlay`, `IrisPacks`, `HotReload`, `PackPass` | UI and shader packs | reported |
| `viewpoint.Hooks`, `viewpoint.interact.LootPanel`, `viewpoint.render.Probe`, `viewpoint.render.Latency` | misc | reported |

**Roof-specific Viewpoint classes: unknown.** The audited list contains no
class whose name points at roofs, roof edges or fascia. Either roof geometry
goes through the general tile and mesh path, or the relevant class was not part
of what the VR project needed to audit. This gap is the first thing the probe
reports should narrow down.

## Project Zomboid 42.21.0

Source: [Umbrella](https://github.com/asledgehammer/Umbrella) stubs at tag
`42.21.0`, generated from the game's exposed API. Status **verified** unless
stated.

| Class | Members used by this mod |
|---|---|
| `zombie.core.Core` | `getInstance()`, `getVersionNumber()`, `getGameVersion()` |
| `zombie.core.GameVersion` | `toString()`, `getMajor()`, `getMinor()`, `getSuffix()` |
| `zombie.ZomboidFileSystem` | `instance`, `getCacheDir()`, `getModIDs()` (the latter verified through PeekAView's 42.21 build) |
| `zombie.iso.IsoWorld` | `instance`, `getCell()` |
| `zombie.iso.IsoCell` | `getGridSquare(int,int,int)`, `getMaxFloors()` |
| `zombie.iso.IsoGridSquare` | `getObjects()`, `getFloor()`, `getWall()`, `getProperties()`, `HasSlopedRoof()`, `HasSlopedRoofNorth()`, `HasSlopedRoofWest()`, `HasEave()`, `HasElevatedFloor()`, `HasStairs()`, `getRoofHideBuilding()`, `isOutside()`, `isSolidFloor()`, `getRoom()`, `getRoomID()`, `getBuilding()` |
| `zombie.iso.IsoObject` | `getSprite()`, `getSpriteName()`, `getTileName()`, `getObjectName()`, `getType()`, `getAlpha()`, `getTargetAlpha()`, `getRenderYOffset()`, `getOffsetX()`, `getOffsetY()`, `getProperties()` |
| `zombie.iso.sprite.IsoSprite` | `getName()`, `getProperties()`, `getTileType()`, `getSpriteGrid()`, `hasActiveModel()`, `getRoofProperties()`, `getSurface()`, `getSlopedSurfaceDirection()`, `getFasciaEdge()` |
| `zombie.core.properties.PropertyContainer` | `getFlagsList()`, `getPropertyNames()`, `get(String)`, `has(…)` |
| `zombie.iso.SpriteDetails.IsoObjectType` | `wall`, `WestRoofB/M/T`, `doorN/W`, `windowFN/FW`, `stairs*`, `tree`, `normal` |
| Lua globals | `getCore()`, `getPlayer()`, `getActivatedMods()`, `getModInfoByID(id)`, `getMouseX/Y()`, `screenToIsoX/Y(player,x,y,z)`, `isKeyDown`, `getTimestampMs`, `HaloTextHelper.addText(player,text)` |
| Lua events | `OnGameStart`, `OnKeyPressed`, `OnTick`, `OnPostRender`, `OnRenderTick` |
| `PZAPI.ModOptions` | `create(id,name)`, `addTickBox`, `addTextEntry`, `addButton`, `addDescription`, `addTitle` (optional), `getOption`, `save()` |

Not available in 42.21.0, contrary to older guides: `IsoObject.isRoof()`,
`PropertyContainer.Val(...)`, `IsoSprite.getTilesetName()`.

Vanilla renderer classes, **verified** to exist and patched by PeekAView, kept
here as a comparison baseline: `zombie.iso.fboRenderChunk.FBORenderCell`
(`renderInternal`, `isPotentiallyObscuringObject`, `renderPlayers`),
`FBORenderCutaways` (incl. `$OrphanStructures`), `FBORenderTrees`
(`addTree`, `calculateDepth`, `init`), `zombie.iso.IsoCell.renderInternal`,
`zombie.iso.IsoWorld.renderInternal`, `zombie.ui.UIManager.getTileFromMouse`.

## ZombieBuddy

Source: the ZombieBuddy repository at tag `v2.3.2` and `master`
(3.0.0-beta1). Status **verified**.

| Member | Note |
|---|---|
| `me.zed_0xff.zombie_buddy.ZombieBuddy.getVersion()` | static; this mod calls it reflectively |
| `me.zed_0xff.zombie_buddy.Exposer.LuaClass` | class-level annotation; exposure happens after `LuaManager$Exposer.exposeAll` |
| `me.zed_0xff.zombie_buddy.annotations.Patch` | `className`, `methodName`, `warmUp`, `isAdvice`, `strictMatch`; `OnEnter`/`OnExit`, `skipOn`, `This`, `Argument`, `AllArguments`, `Return`, `Thrown`, `Local`, `Field`, `VarHandle` |
| `mod.info` keys | `require=\ZombieBuddy`, `javaJarFile`, `javaPkgName`, `ZBVersionMin`, `ZBVersionMax`, `javaPreload` |
| Loader behaviour | `Main.main(String[])` is called if present; patch classes must be in `javaPkgName` exactly; `media/java/client/` is skipped on dedicated servers |
| `ZombieBuddy.getJavaModStatus(modId)` (Lua) | `loaded`, `reason`, `sha256`, `decision`, `persisted` |

In 3.0.0-beta1 `javaJarFile`/`ZBVersionMin`/`ZBVersionMax` also accept numeric
suffixes for several JARs per mod; 2.x ignores the suffixed keys. This mod uses
the single unsuffixed form, which both versions read.

## Known JAR hashes (SHA-256)

Taken from the VR project's `pins.json`; this mod compares them at runtime and
reports the hash itself when it does not match.

| Hash | Identifies |
|---|---|
| `94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f` | Viewpoint 0.1.5a-hotfix |
| `e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33` | Project Zomboid 42.21.0 |
| `6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6` | ZombieBuddy 2.3.2 |
| `dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283` | ZombieBuddy 2.3.2, B42.21 fix build |

## Licences of the sources used

| Project | Licence | How it is used here |
|---|---|---|
| ZombieBuddy | permissive (MIT-style), © Andrey "Zed" Zaikin | API documentation; no code copied |
| PeekAView | MIT, © armakupub | read as a reference for mod layout and ZombieBuddy conventions; no code copied |
| Umbrella | see its repository | API names verified against its stubs; no code copied |
| project-viewpoint-vr | **no licence file** | class and method *names* only, treated as a bug report; nothing copied |
| Project Viewpoint, Project Zomboid | proprietary | never modified, never redistributed |
