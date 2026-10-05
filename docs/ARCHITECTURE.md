# Architecture

## The shape of the mod

```
VPGeometryFix (mod folder)
├── mod.info                      root descriptor, require=ZombieBuddy
└── 42.21/
    ├── mod.info                  javaJarFile + javaPkgName + ZBVersionMin
    └── media/
        ├── java/client/vpgeometryfix.jar    client-only Java
        └── lua/client/*.lua                 options, keybinds, startup log
```

`media/java/client/` is deliberate: ZombieBuddy skips a `client/` JAR on a
dedicated server, so a server never loads diagnosis code it has no use for.

## Java side (`src/pzmod/vpgeometryfix`, package `pzmod.vpgeometryfix`)

ZombieBuddy requires the `Main` class and every patch class to sit directly in
the package named by `javaPkgName`, so the package is flat.

| Class | Responsibility |
|---|---|
| `Main` | ZombieBuddy calls `main(String[])` when the JAR loads, before the game is up. Logs what is already knowable. Registers nothing. |
| `Environment` | Read-only facts: game version via `Core`, ZombieBuddy version reflectively, Viewpoint presence by resolving the audited `viewpoint.*` class names without initialising them, JAR identity by SHA-256 against known hashes. |
| `Api` | The `@Exposer.LuaClass` surface, reachable from Lua as `VPGeometryFix`: `startupReport`, `status`, `setDebug`, `isDebug`, `setViewpointModVersion`, `viewpointClasses`, `identifyGameJar`, `probe`, `probeColumn`, `lastReport`. |
| `TileProbe` | Reads one square and its objects through vanilla getters and formats a report. Every getter is wrapped, so one failure costs one field, not the report. |
| `Reports` | Writes a report under `<Zomboid cache>/VPGeometryFix/`. |
| `Log` | `[VPGeometryFix]` prefix; debug lines gated on debug mode. |

**No `@Patch` class exists in this release, and `tests/run.sh` fails the build
if one appears.** That is the mechanical guarantee behind "changes no
rendering": without a patch, ZombieBuddy weaves nothing, and the only code of
ours that runs is the startup block and whatever a keypress triggers.

## Lua side (`resources/mod/42.21/media/lua/client`)

| File | Responsibility |
|---|---|
| `VPGeometryFix_KeyBinding.lua` | Registers `[VPGeometryFix]` with *Probe Tile* (F10) and *Toggle Debug* (F11). |
| `VPGeometryFix_Options.lua` | Options → Mods → Viewpoint Geometry Fix: the debug tick box, three coordinate fields and a probe button. State lives in Java; this is only the UI. |
| `VPGeometryFix_Diagnostics.lua` | `OnGameStart`: reads the Viewpoint version from the mod list, hands it to Java, prints the startup block. `OnKeyPressed`: the two keys. |

Lua has to supply the Viewpoint version because the mod list is the only place
that string exists and Java cannot read it before the game is loaded. Java in
turn has to do the class and JAR identification, because Lua cannot see
`viewpoint.*` classes at all.

## Why both halves exist

- Lua alone cannot see the renderer: `viewpoint.*` is not exposed to Lua.
- Java alone cannot offer an options screen, a rebindable key or the mod list.
- ZombieBuddy's `@Exposer.LuaClass` is the bridge, and the later fix will need
  the Java half anyway (see `VIEWPOINT-RENDERING.md`), so the split is already
  the one the fix will use.

## Performance

Nothing runs per frame or per tick. `OnGameStart` fires once; the key handler
only on a keypress; a probe reads one or two squares. With debug mode off the
probe key refuses and logs one line. No patch means no woven code on any
render path.

## Deliberate non-goals in 0.1.0

No rendering change, no shader change, no change to `projectzomboid.jar`, to
Viewpoint's files, to ZombieBuddy's files or to any savegame, and no gameplay
change. The only file this mod writes is its own report under the Zomboid
cache directory.

## Planned next steps

1. Run 0.1.0 in the game, probe one broken roof and one working roof of the
   same building, and compare the two reports.
2. From that difference, decide which of the four candidate sites in
   `VIEWPOINT-RENDERING.md` is responsible, and pin the Viewpoint JAR hash the
   finding applies to.
3. Only then add the first `@Patch`, behind the existing debug flag, with the
   before/after reports as its justification.
