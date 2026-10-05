#!/usr/bin/env python3
"""Runs resources/doctor/VPGF-Doctor.ps1 against a fake Steam/Zomboid tree.
Needs PowerShell (pwsh). Skipped when none is found. Windows users run the
same script with Windows PowerShell 5.1; this test uses PowerShell 7."""
import os, pathlib, shutil, subprocess, sys, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
PS1 = ROOT / "resources/doctor/VPGF-Doctor.ps1"
pwsh = os.environ.get("PWSH") or shutil.which("pwsh") or ("/tmp/pwsh/pwsh" if os.path.exists("/tmp/pwsh/pwsh") else None)
if not pwsh:
    print("skip doctor ps1 test: no pwsh found (set PWSH=...)")
    sys.exit(0)

failed = 0
def check(cond, msg):
    global failed
    if not cond:
        failed += 1
        print("FAIL doctor:", msg)

with tempfile.TemporaryDirectory() as t:
    t = pathlib.Path(t)
    lib = t / "SteamLibrary"
    pz = lib / "steamapps/common/ProjectZomboid"
    pz.mkdir(parents=True)
    (pz / "projectzomboid.jar").write_text("x")
    (pz / "ProjectZomboid64.json").write_text('{"vmArgs": ["-Xmx4g"]}')
    # like the real Workshop item: mod.info in common/, JAR in 42/ (which has no mod.info)
    vproot = lib / "steamapps/workshop/content/108600/999/mods/Viewpoint"
    (vproot / "common").mkdir(parents=True)
    (vproot / "common/mod.info").write_text("name=Project Viewpoint\nid=Viewpoint\nmodversion=0.1.5a-hotfix\n")
    vp = vproot / "42"
    (vp / "media/java/client").mkdir(parents=True)
    addon = lib / "steamapps/workshop/content/108600/998/mods/ViewpointCar/42"
    addon.mkdir(parents=True)
    (addon / "mod.info").write_text("id=ViewpointCar\nmodversion=1.0\n")
    # real class files for the signature reader (compiled from small fixtures)
    src = t / "fx/viewpoint/world"
    src.mkdir(parents=True)
    (src / "WallMesher.java").write_text(
        "package viewpoint.world;\n"
        "public final class WallMesher {\n"
        "  static int built; private float[][] verts; long id = 5L; double d = 1.5;\n"
        "  public static boolean edge(int x, String s, java.util.List<int[]> l) { Runnable r = () -> {}; return x > 0; }\n"
        "  void roof(Object o, long[] a) {}\n"
        "  static final class Slope { int dz; }\n"
        "}\n")
    subprocess.run(["javac", "--release", "17", "-d", str(t / "fxc"), str(src / "WallMesher.java")], check=True)
    with zipfile.ZipFile(vp / "media/java/client/viewpoint.jar", "w") as z:
        for n in ["viewpoint/render/WorldRenderer.class", "viewpoint/world/ChunkWalk.class",
                  "viewpoint/input/Look.class", "viewpoint/render/WorldRenderer$1.class"]:
            z.writestr(n, b"\xca\xfe\xba\xbe")
        for f in sorted((t / "fxc").rglob("*.class")):
            z.write(f, f.relative_to(t / "fxc").as_posix())
    (pz / "media/tileGeometry").mkdir(parents=True)
    (pz / "media/tileGeometry/roofs_01.txt").write_text("x" * 3000)
    (pz / "media/tileGeometry.txt").write_text("VERSION = 1,\n" + "\n".join(f"line{i}" for i in range(50))
        + "\ntile\n{\n  tileset = roofs_01,\n  index = 12,\n  polygon { points = 0 0 1 1 }\n}\nfoo roofs_02_3\n")
    ed = pz / "media/lua/client/DebugUIs/TileGeometryEditor"
    ed.mkdir(parents=True)
    (ed / "TileGeometryEditor.lua").write_text("local a = 1\nfunction X:onSave() getTileGeometry():write(modID) end\nlocal b = 2\n")
    (pz / "media/scripts").mkdir(parents=True)
    (pz / "media/scripts/items.txt").write_text("x")
    (addon / "media").mkdir()
    (addon / "media/tileDepth_override.txt").write_text("x")
    zb = t / "Zomboid"
    good = zb / "mods/ViewpointGeometryFix/42"
    (good / "media/lua/client").mkdir(parents=True)
    (good / "mod.info").write_text("id=ViewpointGeometryFix\nmodversion=0.1.3-diag\njavaJarFile=media/java/client/ViewpointGeometryFix.jar\n")
    (good / "media/lua/client/VPGeometryFix_Main.lua").write_text("--")
    (zb / "mods/ViewpointGeometryFix/common").mkdir()
    (zb / "console.txt").write_text("\n".join(
        ["LOG : General > [ZB] ZombieBuddy v2.3.4 loading ViewpointGeometryFix.jar",
         "LOG : Lua > [VPGeometryFix] Lua loaded 0.1.3-diag",
         "LOG : Lua > [VPGeometryFix] Loaded",
         "LOG : Lua > [VPGeometryFix] PZ version: 42.21.0 (from Lua)",
         "LOG : Lua > [VPGeometryFix] Debug mode: OFF (Lua only)",
         "LOG : Lua > [VPGeometryFix] ERROR in OnGameStart: boom",
         "ERROR: General > attempted index: x of non-table",
         "LOG : General > [Viewpoint] renderer ready"]
        + ["LOG : General > [Viewpoint] 60 fps | gpu ms floors 0.01"] * 300
        + ["LOG : General > [ViewpointTurbo/VRAM] floor reserved"] * 50) + "\n")
    out = t / "out/VPGF-Report.txt"
    out.parent.mkdir()
    r = subprocess.run([pwsh, "-NoProfile", "-File", str(PS1), "-SteamLib", str(lib), "-Zomboid", str(zb), "-Out", str(out)],
                       capture_output=True, text=True)
    check(r.returncode == 0, f"exit {r.returncode}: {r.stderr}")
    check(r.stderr.strip() == "", f"stderr: {r.stderr}")
    rep = out.read_text(encoding="utf-8-sig") if out.exists() else ""
    for needle in ["ProjectZomboid64.json: javaagent NICHT eingetragen",
                   "Mod: id=Viewpoint name=Project Viewpoint modversion=0.1.5a-hotfix",
                   "viewpoint.jar: sha256",
                   "Viewpoint-Add-ons im Workshop-Ordner (1): ViewpointCar 1.0",
                   "Klassen gesamt: 6, davon mit Geometrie-/Render-Stichwort: 5",
                   "      viewpoint.render.WorldRenderer", "      viewpoint.world.ChunkWalk",
                   "    Lua: vorhanden", "    JAR: FEHLT",
                   "Mod-Lua geladen: ja, Startblock: ja, Java-Teil der Mod: NEIN, ZombieBuddy aktiv: ja, Fehlerzeilen der Mod: 1",
                   "--- Zeilen dieser Mod (6)", "ERROR in OnGameStart: boom", "attempted index: x of non-table",
                   "--- ZombieBuddy (1)", "--- Viewpoint (ohne Leistungsmeldungen) (1)", "[Viewpoint] renderer ready",
                   "Der Lua-Teil der Mod laeuft, der Java-Teil nicht", "Die Mod meldet 1 Fehler", "=== ERGEBNIS ==="]:
        check(needle in rep, f"missing {needle!r}\n{rep}")
    check("viewpoint.input.Look" not in rep.split("=== 5")[0].split("Klassen gesamt")[1], "non-keyword class listed")
    check("Mod liegt an falscher Stelle" not in rep, "correct path reported as wrong")
    check(rep.count("attempted index") == 1, "follow-up error listed once")
    check("ZombieBuddy nicht gefunden" not in rep, "no ZombieBuddy finding while it is active")
    check("local b = 2" not in rep, "unrelated editor lines listed")
    check("items.txt" not in rep, "unrelated media file listed")
    check("60 fps" not in rep and "ViewpointTurbo/VRAM" not in rep, "performance spam must be filtered")
    check("javaagent" not in rep.split("=== ERGEBNIS ===")[1], "javaagent is no finding when ZombieBuddy is evidently active")
    geo = out.parent / "VPGF-Viewpoint-Geometry.txt"
    gtext = geo.read_text(encoding="utf-8-sig") if geo.exists() else ""
    for needle in ["viewpoint.world.WallMesher extends java.lang.Object",
                   "  F static int built", "  F float[][] verts", "  F long id", "  F double d",
                   "  M static boolean edge(int, String, java.util.List)", "  M void roof(Object, long[])",
                   "viewpoint.world.WallMesher$Slope extends java.lang.Object", "  F int dz"]:
        check(needle in gtext, f"geometry signatures missing {needle!r}\n{gtext}")
    check("lambda$" not in gtext, "synthetic lambda methods must be skipped")
    check("viewpoint.world.ChunkWalk : nicht lesbar" in gtext, "broken class reported, not fatal")
    check("Signaturen von 2 Geometrie-/Sichtbarkeits-Klassen" in rep, "geometry summary line")
    cls = out.parent / "VPGF-Viewpoint-Classes.txt"
    check(cls.exists() and "viewpoint.input.Look" in cls.read_text(encoding="utf-8-sig"), "class list file")

print(f"doctor ps1: {'ok' if failed == 0 else str(failed) + ' failed'}")
sys.exit(1 if failed else 0)
