# Compile stubs

Minimal stand-ins for the game, ZombieBuddy and Viewpoint types this mod
compiles against. They exist so `tests/run.sh` can compile and exercise the
mod's own code on a machine without Project Zomboid installed. Each
signature mirrors the real one as verified in `docs/CLASSES.md`; the bodies
return harmless values and are never shipped.

The real build (`build/build.sh`) compiles against the installed
`projectzomboid.jar` and `ZombieBuddy.jar` instead and ignores this tree.
