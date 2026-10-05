# Viewpoint Geometry Fix

A Project Zomboid **B42.21** mod that investigates the rendering problems of
[Project Viewpoint](https://steamcommunity.com/sharedfiles/filedetails/?id=3807334881):
roofs, roof edges, walls, building objects and other tiles that draw wrongly,
incompletely, displaced or not at all in Viewpoint's first-person view.

**Version 0.1.0 is a diagnosis build. It changes no rendering.** There is not
a single bytecode patch in it — `tests/run.sh` fails if one appears. What it
does is tell you exactly what your install is and what the game thinks a given
tile looks like, so the later fix has evidence behind it instead of guesses.

## What it does today

- Detects whether Viewpoint is active, by resolving the audited `viewpoint.*`
  class names and by identifying the JAR via SHA-256.
- Reads the Project Zomboid version and the ZombieBuddy version.
- Writes one unmistakable block to `console.txt` at game start:

  ```
  [VPGeometryFix] Loaded
  [VPGeometryFix] PZ version: ...
  [VPGeometryFix] Viewpoint detected: ...
  [VPGeometryFix] ZombieBuddy detected: ...
  [VPGeometryFix] Debug mode: ...
  ```

- Has a debug mode, off by default, in Options → Mods → *Viewpoint Geometry
  Fix* and on **F11**.
- With debug on, **F10** collects everything the game will tell us about the
  tiles you are pointing at: sprite and tile name, object type, alpha and
  target alpha, render offsets, sprite flags and properties, roof and eave
  flags, room and building, and the whole object stack of the square — for the
  player's square, the square the player faces and the cursor square, on this
  floor and the one above.
- Lets you probe a fixed X/Y/Z from the options screen, so the same
  known-broken tile can be re-read after every change.
- Writes each report to `<Zomboid cache>/VPGeometryFix/probe-<timestamp>.txt`.

## What it deliberately does not do

No rendering change. No change to `projectzomboid.jar`, to Viewpoint's files or
to ZombieBuddy's files — ZombieBuddy patches in memory, so nothing on disk is
touched. No savegame change, no gameplay change. Nothing runs per frame or per
tick, so normal play is unaffected; with debug off the mod's entire footprint
is five lines at startup.

## Requirements

- Project Zomboid 42.21
- [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy) 2.3.2 or newer, set
  up once (Workshop item `3619862853`)
- Project Viewpoint 0.1.5a-hotfix or whatever version you run — needed to
  diagnose, not to build
- JDK 17+ to build

## Build, install, use

See [docs/BUILD.md](docs/BUILD.md). Short version:

```bash
cp build/build.local.example build/build.local   # set PZ_DIR
build/build.sh                                   # builds and installs
tests/run.sh                                     # offline checks, no game needed
```

## Documentation

| File | Contents |
|---|---|
| [docs/VIEWPOINT-RENDERING.md](docs/VIEWPOINT-RENDERING.md) | Viewpoint's pipeline as far as it is verified, where the evidence comes from, and where a fix will probably have to go |
| [docs/CLASSES.md](docs/CLASSES.md) | Every class and method this project relies on, each marked verified, reported or unknown, with the known JAR hashes |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the mod is put together and why it is split between Java and Lua |
| [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md) | How to produce a useful report and how to read it |
| [docs/BUILD.md](docs/BUILD.md) | Build, install, verify, uninstall |
| [CLAUDE.md](CLAUDE.md) | Working agreements for this repository |

## Status of the investigation

The strongest suspects for missing roofs and walls are Viewpoint's visibility
decisions — `viewpoint.visibility.Rooms.hiding` and
`viewpoint.world.ChunkWalk.inView` — which run *before* the renderer sees a
tile, plus the fact that Viewpoint consumes a snapshot of the scene, so a tile
that never entered the snapshot cannot be recovered later. None of that is
confirmed for this bug yet, and no roof-specific Viewpoint class is known.
Details, sources and the open questions are in
[docs/VIEWPOINT-RENDERING.md](docs/VIEWPOINT-RENDERING.md).

Next step: run this build, probe one broken and one working roof tile of the
same building, and compare the reports.

## Licence

MIT, see [LICENSE](LICENSE). This project contains no Project Zomboid,
Viewpoint or ZombieBuddy code and redistributes none of those files.
