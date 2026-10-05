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
    vp = lib / "steamapps/workshop/content/108600/999/mods/Viewpoint/42"
    (vp / "media/java/client").mkdir(parents=True)
    (vp / "mod.info").write_text("name=Project Viewpoint\nid=Viewpoint\nmodversion=0.1.5a-hotfix\n")
    with zipfile.ZipFile(vp / "media/java/client/viewpoint.jar", "w") as z:
        for n in ["viewpoint/render/WorldRenderer.class", "viewpoint/world/ChunkWalk.class",
                  "viewpoint/input/Look.class", "viewpoint/render/WorldRenderer$1.class"]:
            z.writestr(n, b"\xca\xfe\xba\xbe")
    zb = t / "Zomboid"
    good = zb / "mods/ViewpointGeometryFix/42"
    (good / "media/lua/client").mkdir(parents=True)
    (good / "mod.info").write_text("id=ViewpointGeometryFix\nmodversion=0.1.3-diag\njavaJarFile=media/java/client/ViewpointGeometryFix.jar\n")
    (good / "media/lua/client/VPGeometryFix_Main.lua").write_text("--")
    (zb / "mods/ViewpointGeometryFix/common").mkdir()
    (zb / "console.txt").write_text("LOG : General > loading Viewpoint\nLOG : Lua > [VPGeometryFix] Lua loaded 0.1.3-diag\n"
                                     "LOG : General > mods: ViewpointGeometryFix\nERROR: something broke\n")
    out = t / "out/VPGF-Report.txt"
    out.parent.mkdir()
    r = subprocess.run([pwsh, "-NoProfile", "-File", str(PS1), "-SteamLib", str(lib), "-Zomboid", str(zb), "-Out", str(out)],
                       capture_output=True, text=True)
    check(r.returncode == 0, f"exit {r.returncode}: {r.stderr}")
    check(r.stderr.strip() == "", f"stderr: {r.stderr}")
    rep = out.read_text(encoding="utf-8-sig") if out.exists() else ""
    for needle in ["ProjectZomboid64.json: javaagent NICHT eingetragen",
                   "Kein -javaagent in ProjectZomboid64.json",
                   "Mod: id=Viewpoint name=Project Viewpoint modversion=0.1.5a-hotfix",
                   "Klassen gesamt: 4, davon mit Geometrie-/Render-Stichwort: 3",
                   "      viewpoint.render.WorldRenderer", "      viewpoint.world.ChunkWalk",
                   "    Lua: vorhanden", "    JAR: FEHLT",
                   "Mod-ID erwaehnt: ja, Mod-Lua geladen: ja, Startblock: NEIN, ZombieBuddy-Zeilen: NEIN",
                   "Keine ZombieBuddy-Zeilen in console.txt", "=== ERGEBNIS ==="]:
        check(needle in rep, f"missing {needle!r}\n{rep}")
    check("viewpoint.input.Look" not in rep.split("=== 5")[0].split("Klassen gesamt")[1], "non-keyword class listed")
    check("Mod liegt an falscher Stelle" not in rep, "correct path reported as wrong")
    cls = out.parent / "VPGF-Viewpoint-Classes.txt"
    check(cls.exists() and "viewpoint.input.Look" in cls.read_text(encoding="utf-8-sig"), "class list file")

print(f"doctor ps1: {'ok' if failed == 0 else str(failed) + ' failed'}")
sys.exit(1 if failed else 0)
