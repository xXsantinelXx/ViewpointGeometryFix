# Working agreements for this repository

## The rule that matters most

**Do not work speculatively.** Every class, method, field or flag this project
relies on is listed in `docs/CLASSES.md` with a status: *verified* (checked
against a named source and dated), *reported* (a third party documents it;
not confirmed here) or *unknown*. If something cannot be verified, it is
marked unknown and another source is sought. A plausible-sounding method name
that nobody checked does not go into the code.

Reproducible diagnosis comes before any fix.

## Scope of the current release (0.1.0)

Diagnosis only:

- no rendering change, no shader change, no `@Patch` class at all
- no modification of `projectzomboid.jar`, of Viewpoint's files or of
  ZombieBuddy's files
- no savegame change, no gameplay change
- nothing per frame or per tick; normal play must stay unaffected
- the only file written is this mod's own report under the Zomboid cache
  directory

`tests/run.sh` enforces the `@Patch` ban and the `mod.info` invariants. Keep it
that way: when the first patch eventually lands, that check changes in the same
commit that justifies it, with the probe reports as the justification.

## Repository layout

```
src/        Java, package pzmod.vpgeometryfix (flat: ZombieBuddy requires it)
resources/  the mod folder: mod.info files and media/lua
build/      build.sh, build.local.example (build.local is git-ignored)
docs/       architecture, class inventory, Viewpoint findings, build, diagnostics
tests/      run.sh, the offline harness and the compile stubs
```

## Conventions

- Log lines start with `[VPGeometryFix]`. The five startup lines are a
  contract with the user and with `docs/BUILD.md`; do not reword them.
- Debug output goes through `Log.debug`, which is gated on debug mode.
- Game getters are wrapped: on a half-loaded square one failing getter must
  cost one field in the report, not the report.
- Lua holds the UI (options, keybind, halo), Java holds the facts (versions,
  class probing, tile reports). The bridge is `@Exposer.LuaClass` on `Api`.
- New Java facts belong in `Environment`, new report content in `TileProbe`,
  new Lua-callable methods in `Api` — and a new `Api` method gets a test.
- Comments explain why, not what. See the existing ones for the level.

## Third-party sources

Named, licence-checked, never copied:

- ZombieBuddy (permissive) — the loader and patch API we build on
- PeekAView (MIT) — reference for mod layout and ZombieBuddy conventions
- Umbrella — the 42.21.0 API stubs used to verify vanilla names
- project-viewpoint-vr (**no licence file**) — class and method *names* only,
  treated like a bug report

No code from any of them is in this repository, and Project Zomboid, Viewpoint
and ZombieBuddy files are never redistributed.

## Before you commit

```bash
tests/run.sh
```

and, when a game is available, `build/build.sh` plus one in-game launch that
shows the startup block in `console.txt`.
