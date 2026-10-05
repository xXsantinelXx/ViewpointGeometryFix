# Viewpoint's rendering architecture, as far as it is verified

Last checked 2026-10-05, against Project Zomboid 42.21, Viewpoint
0.1.5a-hotfix and ZombieBuddy 2.3.2 / 3.0.0-beta1.

## Where the evidence comes from

Nothing in this file was read out of a decompiled Viewpoint JAR by this
project. Two sources are used, and every claim says which:

1. **ZombieBuddy's own repository**
   ([zed-0xff/ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy), MIT-like
   permissive licence) — the loader, the patch API, the Lua bridge. Read at
   tag `v2.3.2` and at `master` (3.0.0-beta1).
2. **Project Viewpoint VR**
   ([kilroy94/project-viewpoint-vr](https://github.com/kilroy94/project-viewpoint-vr))
   — a third-party project that audited the Viewpoint 0.1.5a-hotfix JAR with
   `javap` and Vineflower and published the resulting *binary contract*: class
   names, method names, descriptors and call order. It ships no Viewpoint code
   and has **no licence file**, so nothing is copied from it. It is used the
   way a bug report is used: as a list of names to verify ourselves.

Everything below that is marked **unverified** has not been confirmed against
a JAR on this machine. The diagnosis build exists precisely to replace those
marks with evidence from a running game.

## The pipeline, in the order a frame goes through it

Source: the contract in `viewpoint/inspect_contract.py` and
`docs/VIEWPOINT-PLAN.md` of the VR project, which lists six methods and 23
checked invocations.

| Step | Class and method | What it does there |
|---|---|---|
| 1 | `viewpoint.FP.renderWorld` | Snapshots the game's scene into one of three frames and enqueues a `SceneDrawer` through the vanilla `SpriteRenderer`. |
| 2 | `viewpoint.SceneDrawer.drawFrame()` | Consumes that snapshot: collects retirement, picks the camera, calls `WorldRenderer.begin`, releases model drawers, draws effects, finishes, marks the frame drawn. |
| 3 | `viewpoint.render.WorldRenderer.begin(SceneData, Matrix4f, float, float, float)` | Shared preparation: resets `FrameStream`, advances the frame index, temporal state and uniforms, uploads scene assets, prepares `Meshes` and `ModelPass`, then calls `drawWorld`. |
| 4 | `viewpoint.render.WorldRenderer.drawWorld()` | `FarPass.prepare(FrameContext, boolean)`, `ShadowPass.draw`, `ModelPass.gbuffer`, `FarPass.gbuffer`. |
| 5 | `viewpoint.render.WorldRenderer.passes()` | `TemporalPass.resolve`, `TemporalPass.remember`, `TemporalPass.present`. |
| 6 | `viewpoint.render.WorldRenderer.finish(SceneData)` | `passes()`, then `ModelPass.endFrame()`. |
| 7 | `viewpoint.SceneDrawer.postRender()` | Releases captured character, corpse and model resources. |

The frame is *captured on the game thread and consumed later*. That is the
single most important fact for our bug: per-eye or per-camera view matrices
cannot recover anything that was never put into the snapshot. The VR project
states this explicitly ("Per-eye view matrices cannot recover objects missing
from the snapshot"), and it is the reason the geometry classes below matter
more than the shader passes.

## Visibility and culling: where geometry goes missing

Source: `VisibilityTransform.TARGETS` and `NATIVE-STAGES.md` of the VR
project, which names five classes it has to neutralise to get all-direction
visibility, with exact method descriptors.

| Class | Method (descriptor) | Role |
|---|---|---|
| `viewpoint.world.ChunkWalk` | `inView(FFFFFF)Z` | Chunk-level view test: decides whether a chunk's geometry is walked at all. |
| `viewpoint.visibility.Rooms` | static field `hiding` (`Z`) | Room hiding: the mechanism behind walls and roofs being removed. |
| `viewpoint.models.ModelCull` | `wanted(IsoMovingObject, IsoPlayer)Z` | Per-model culling. |
| `viewpoint.models.Characters` | `behind(IsoMovingObject, FFFF)Z` | Character occlusion test. |
| `viewpoint.models.CorpseView` | `sees(FFF)Z` | Corpse visibility cone. |

Note what these say about the reported symptom. Roofs, roof edges, walls and
building objects disappearing in first person is the expected result of a
culling and room-hiding set that was written for a camera looking *down* at a
scene: `ChunkWalk.inView` and `Rooms.hiding` both decide before the renderer
ever sees the tile. The VR project reached the same conclusion from the other
end — it had to override exactly these five to keep geometry on screen — but
whether our bug is in these classes, in the snapshot step, or in the tile data
itself is **not established** and is what the diagnosis build must answer.

Also relevant, same source: far-shell preparation
(`viewpoint.render.FarPass.prepare`) mixes asynchronous uploads with the eye
frustum and projection, and sky and cloud reconstruction assumes a symmetric
projection. Geometry near the screen edge is therefore not equivalent to
geometry in the centre.

## The vanilla side of the same picture

Source: [Umbrella](https://github.com/asledgehammer/Umbrella) type stubs at
`42.21.0`, which are generated from the game's own exposed API. Verified:
each name below exists in that release.

- `zombie.iso.IsoGridSquare` — one tile: `getObjects()`, `getFloor()`,
  `getWall()`, `getWall(boolean)`, `getWallNW/SE()`, `getProperties()`,
  `HasSlopedRoof()`, `HasSlopedRoofNorth()`, `HasSlopedRoofWest()`,
  `HasEave()`, `HasElevatedFloor()`, `HasStairs()`, `getRoofHideBuilding()`,
  `isOutside()`, `isSolidFloor()`, `getRoom()`, `getRoomID()`,
  `getBuilding()`.
- `zombie.iso.IsoObject` — one object on a tile: `getSprite()`,
  `getSpriteName()`, `getTileName()`, `getObjectName()`, `getType()`,
  `getAlpha()`, `getTargetAlpha()`, `getRenderYOffset()`, `getOffsetX/Y()`,
  `getProperties()`, `getSpriteGrid()`, `getObjectRenderEffects()`.
- `zombie.iso.sprite.IsoSprite` — the sprite behind an object:
  `getName()`, `getProperties()`, `getTileType()`, `getRoofProperties()`,
  `getSpriteGrid()`, `getSurface()`, `getSlopedSurfaceDirection()`,
  `getFasciaEdge()`, `hasActiveModel()`, `isWallSE()`.
- `zombie.iso.SpriteDetails.IsoObjectType` — includes `wall`, `WestRoofB`,
  `WestRoofM`, `WestRoofT`, `doorN/W`, `windowFN/FW`, `stairsTN…`, `tree`.
- `zombie.iso.IsoCell.getGridSquare(int,int,int)`,
  `zombie.iso.IsoWorld.instance`, `zombie.core.Core.getInstance()` with
  `getVersionNumber()` and `getGameVersion()`.
- `zombie.iso.fboRenderChunk.FBORenderCell`, `FBORenderCutaways`,
  `FBORenderTrees` — the vanilla chunk renderer and cutaway system. PeekAView
  patches these for its wall cutaway; Viewpoint replaces this stage with its
  own renderer, so for us they are a comparison baseline, not a fix site.

`IsoObject.isRoof()` and `PropertyContainer.Val(...)`, which older guides
mention, are **not** in the 42.21.0 stubs. Roof detection goes through the
sprite's roof properties and the object type instead.

## Which mod type the fix needs

- A **pure Lua mod cannot do it**. Viewpoint's renderer lives in
  `viewpoint.*` Java classes that are not exposed to Lua at all; there is no
  Lua hook between the snapshot and the draw.
- A **Java mod through ZombieBuddy** can: ZombieBuddy's `@Patch` annotations
  patch a named class and method at runtime through ByteBuddy, with
  `@Patch.OnEnter` / `@Patch.OnExit`, argument and field bindings, and
  `skipOn` to suppress the original. That is the only published mechanism
  that reaches a `viewpoint.*` method.
- A **combination** is what the fix will be: Java for the renderer side, Lua
  for the options screen, the keybind and the halo messages, bridged by
  `@Exposer.LuaClass` — which is what this diagnosis build already does.
- A **bytecode patch of projectzomboid.jar or of Viewpoint's JAR on disk is
  not needed and not acceptable** here. ZombieBuddy patches in memory at
  class load; the files on disk stay untouched.

One caveat with evidence: ZombieBuddy issue #13, quoted in PeekAView's own
source, says woven advice outlives the mod being removed from the mod list
within one JVM run. Any later patch of ours needs the same self-check
PeekAView does (ask the mod list whether we are still active) and a game
restart for clean removal.

## Where a fix will probably have to go, and why that is still a guess

Ordered by what the evidence supports, strongest first:

1. **`viewpoint.visibility.Rooms.hiding` and
   `viewpoint.world.ChunkWalk.inView`** — if whole roofs and walls are
   missing rather than wrongly shaded, a visibility decision taken before the
   draw is the likeliest cause, and these two are the audited names for that
   decision.
2. **`viewpoint.render.FarPass.prepare` / frustum handling** — if the loss
   depends on distance or on where on the screen the tile is.
3. **The snapshot in `viewpoint.FP.renderWorld`** — if the tile is absent
   from the frame altogether, no later hook can bring it back.
4. **Tile data itself** — if the probe shows a roof tile whose sprite
   properties or offsets differ from a working neighbour, the bug may be in
   how Viewpoint reads vanilla tile geometry, not in culling.

Which of these it is cannot be settled from documents. It needs a report from
a running game: the same tile probed where it renders and where it does not.
That is what version 0.1.0 produces and all it does.
