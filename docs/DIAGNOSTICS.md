# Producing and reading a report

## Producing one

1. Debug mode on: Options → Mods → *Viewpoint Geometry Fix* → tick, Apply, or
   press **F11** in game. With debug off the probe key refuses and says so.
2. Go to the broken spot, look at the tile, press **F10**.
3. Reports land in `console.txt` and in
   `%USERPROFILE%\Zomboid\<cache>\VPGeometryFix\probe-<timestamp>.txt`.
4. For a tile that is off screen, or to re-read the same tile after a change:
   type the coordinate into *Target X / Y / Z* and press *Probe this
   coordinate*.

A useful pair is **one broken and one working tile of the same building**, as
alike as possible — two roof tiles of one roof, where one draws and one does
not. A single report says what a tile is; a pair says what is different about
the one that fails.

Coordinates: the probe prints the square it read, so note them from the report
rather than guessing. The game's own debug display (if you use it) and the
report agree on the coordinate system: map X, map Y, floor level Z, where 0 is
ground.

## What one keypress reads

Three labelled targets, each on the current floor and the one above:

| Label | Square |
|---|---|
| `player square` | where the player stands |
| `square the player faces` | 1.5 tiles along `IsoGameCharacter.getForwardDirection()` |
| `cursor square via vanilla screenToIso` | what the vanilla screen-to-world mapping puts under the mouse |

Under Viewpoint's camera the third one can point somewhere other than where the
cursor appears to be. That is not a bug in the report; it is why all three are
read and labelled, and the mismatch itself is a finding worth noting.

## Reading a report

```
[VPGeometryFix] column 10700,9400 z 0..1
  mod=0.1.0 pz=42.21.0 (GameVersion 42.21.0) zb=2.3.2
  viewpoint=yes (20/20 audited classes, mod version Viewpoint 0.1.5a-hotfix, jar Viewpoint 0.1.5a-hotfix [Viewpoint.jar])
square 10700,9400,1
  outside=false solidFloor=true slopedRoof=true slopedRoofN=true slopedRoofW=false
  eave=false elevatedFloor=false stairs=false roofHideBuilding=null
  room=12 building=present floorObject=roofs_01_12 wallObject=none
  squareProperties: flags[HasSlopedRoof] props[RoofDirection=N]
  objects=2
  [0] zombie.iso.IsoObject
      sprite=roofs_01_12 tile=roofs_01_12 objectName=null type=normal
      alpha=1.0 targetAlpha=1.0 renderYOffset=0.0 offset=0.0/0.0
      spriteProperties: flags[WestRoofT] props[RoofDirection=N]
      spriteGrid=present tileType=normal activeModel=false
```

The header pins the environment, so a report stays meaningful after an update:
if `jar` reports a plain hash instead of a name, the finding belongs to a
Viewpoint build nobody has audited yet.

What to compare between a working and a broken tile, roughly in order of how
often it is the answer:

- **`objects=`** — is the object there at all? A roof that is missing from the
  square is a world-data question, not a renderer question.
- **`alpha` and `targetAlpha`** — a tile at alpha 0 is being faded out by
  something, which is a different bug from one that is never drawn.
- **`spriteGrid=present`** — multi-tile sprites are drawn as a grid; a roof
  that fails only at a building edge is suspicious here.
- **`renderYOffset` and `offset`** — the usual cause of *displaced* rather
  than missing geometry.
- **sprite flags and properties** (`WestRoofT/M/B`, `RoofDirection`,
  `HasSlopedRoof`) — differences here point at how Viewpoint reads tile
  geometry.
- **`room=` and `roofHideBuilding=`** — both feed room hiding, which
  `viewpoint.visibility.Rooms` is the Viewpoint-side counterpart of.
- **`activeModel=`** — whether a 3D model is involved instead of a sprite.

`<error …>` in a field means that one getter threw, usually on a square that
is not fully loaded. The rest of the report is still valid.

## What a report cannot tell you

It reads the *game's* state, not Viewpoint's. It cannot say whether
`ChunkWalk.inView` rejected the chunk or whether the tile reached the snapshot
at all — that needs a patch, which this release does not have. What it does is
separate "the game has no such tile" from "the game has it and Viewpoint is
dropping it", and that split decides where the fix goes.

## Attaching a report to an issue

The report file contains map coordinates, version strings, sprite names and
flags. No user name, no file path outside the mod's own folder, no save data.
It is safe to paste whole.
