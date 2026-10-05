# Build and install

## Requirements

- Project Zomboid **42.21** (the install directory must contain
  `projectzomboid.jar`).
- **ZombieBuddy** installed and set up once: Workshop item `3619862853`, or
  the Windows installer from its
  [releases](https://github.com/zed-0xff/ZombieBuddy/releases). Setup adds the
  `-javaagent:ZombieBuddy.jar` launch option, and it leaves `ZombieBuddy.jar`
  in the game directory, which the build reads.
- **Project Viewpoint** installed — not needed to build, needed to diagnose.
- A **JDK 17 or newer** for building. The game itself runs on Java 25; the mod
  is compiled with `--release 17`, which both accept.
- Git Bash, WSL or any bash on Windows; the build script is bash.

Nothing in the game directory is written by the build. `projectzomboid.jar`
and `ZombieBuddy.jar` are only read, as compile classpath.

## Build

```bash
git clone https://github.com/xXsantinelXx/ViewpointGeometryFix
cd ViewpointGeometryFix
cp build/build.local.example build/build.local
# edit build/build.local: set PZ_DIR to your Project Zomboid directory
build/build.sh
```

Or without a config file:

```bash
PZ_DIR="/d/SteamLibrary/steamapps/common/ProjectZomboid" build/build.sh
```

The script compiles `src/`, packages `build/out/vpgeometryfix.jar`, stages the
mod folder and copies it to `%USERPROFILE%\Zomboid\mods\VPGeometryFix`. Close
the game first: a running game locks the installed JAR, and the script refuses
to start in that case.

Result:

```
%USERPROFILE%\Zomboid\mods\VPGeometryFix\
├── mod.info
└── 42.21\
    ├── mod.info
    └── media\
        ├── java\client\vpgeometryfix.jar
        └── lua\client\VPGeometryFix_*.lua
```

Set `MOD_INSTALL_ROOT` in `build/build.local` to install elsewhere.

## Offline checks

```bash
tests/run.sh
```

Compiles the mod against the stubs in `tests/stubs`, runs the probe harness,
and checks that `mod.info` and the Java package agree, that the JAR path is
client-only, and that **no `@Patch` annotation exists** — the mechanical
guarantee that this release changes no rendering. Needs only a JDK; no game,
no ZombieBuddy, nothing is launched.

## Enable it in the game

1. Start Project Zomboid with ZombieBuddy's launch option in place.
2. Mods: enable **ZombieBuddy**, **Project Viewpoint** and **Viewpoint
   Geometry Fix**, then load or start a save.
3. On the first launch after each build, ZombieBuddy shows a native dialog
   with this mod's JAR, its date and its SHA-256. Tick *Allow*. Because the
   hash changes with every build, the dialog reappears after a rebuild unless
   you let it persist the decision.

## Check that it loaded

`%USERPROFILE%\Zomboid\console.txt` must contain, at game start:

```
[VPGeometryFix] Loaded
[VPGeometryFix] PZ version: 42.21.0 (GameVersion 42.21.0)
[VPGeometryFix] Viewpoint detected: yes (20/20 audited classes, mod version Viewpoint 0.1.5a-hotfix, jar Viewpoint 0.1.5a-hotfix [Viewpoint.jar])
[VPGeometryFix] ZombieBuddy detected: yes (version 2.3.2)
[VPGeometryFix] Debug mode: off
```

The exact text of the three middle lines depends on your install. `Loaded`
is the line to grep for. Before that, when the JAR loads, there is also a
`[VPGeometryFix] Java mod loaded …` line.

If `Loaded` is missing: the JAR was not loaded. Usual causes are ZombieBuddy's
launch option not being set, the approval dialog being declined, or the mod not
being enabled in the mod list.

## Diagnose

1. Options → Mods → *Viewpoint Geometry Fix* → tick **Debug mode**, Apply. Or
   press **F11** in game.
2. Stand where the geometry is broken, look at the tile, press **F10**.
3. The report goes to `console.txt` and to
   `%USERPROFILE%\Zomboid\<cache>\VPGeometryFix\probe-<timestamp>.txt`.
4. To inspect a tile that is off screen, or the same tile repeatedly: type its
   coordinates into *Target X / Y / Z* in the options screen and press *Probe
   this coordinate*.

The probe reads three targets per keypress — the player's square, the square
the player faces, and the square the vanilla screen-to-world mapping puts under
the cursor — each labelled in the report, for both the current and the next
floor level. Under Viewpoint's own camera the cursor square may not be the tile
you are looking at; that is why all three are read and labelled.

## Uninstall

Remove the mod from the mod list and delete
`%USERPROFILE%\Zomboid\mods\VPGeometryFix`. Since this release patches
nothing, there is nothing to unwind; reports already written stay where they
are and can be deleted by hand.
