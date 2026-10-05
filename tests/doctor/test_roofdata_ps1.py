#!/usr/bin/env python3
"""Runs resources/doctor/VPGF-RoofData.ps1 on a synthetic tileGeometry.txt (pwsh; skipped without)."""
import os, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
PS1 = ROOT / "resources/doctor/VPGF-RoofData.ps1"
pwsh = os.environ.get("PWSH") or shutil.which("pwsh") or ("/tmp/pwsh/pwsh" if os.path.exists("/tmp/pwsh/pwsh") else None)
if not pwsh:
    print("skip roofdata test: no pwsh")
    sys.exit(0)

def tile(xy, shape=False, comment=None):
    out = []
    if comment:
        out.append(f"        /* {comment} */")
    out += ["        tile", "        {", f"            xy = {xy},", ""]
    if shape:
        out += ["            box", "            {", "                translate = 0x3982x0,",
                "                rotate = 392394x0x0,", "                min = -10000x0x-10000,",
                "                max = 10000x500x10000,", "            }", ""]
    out += ["            properties", "            {", "                OpaquePixelsOnly = true,", "            }", "        }", ""]
    return out

def tileset(name, tiles):
    return ["    tileset", "    {", f"        name = {name},", ""] + sum(tiles, []) + ["    }", ""]

geo = ["tileGeometry", "{", "    VERSION = 2,", ""]
geo += tileset("furniture_01", [tile("0x0", True)])
geo += tileset("roofs_01", [tile("0x0", True, "roofs_01_0"), tile("1x0", False, "roofs_01_1"), tile("2x0", True, "roofs_01_2")])
geo += tileset("roofs_02", [tile("0x0", False, "roofs_02_0"), tile("1x0", False), tile("2x0", False), tile("3x0", False)])
geo += tileset("roofs_04", [tile("0x0", True)])
geo += ["}"]

failed = 0
def check(c, m):
    global failed
    if not c:
        failed += 1
        print("FAIL roofdata:", m)

with tempfile.TemporaryDirectory() as t:
    t = pathlib.Path(t)
    game = t / "PZ"
    (game / "media").mkdir(parents=True)
    (game / "media/tileGeometry.txt").write_text("\r\n".join(geo) + "\r\n")
    zb = t / "Zomboid"
    media = zb / "mods/ViewpointGeometryFix/42/media"
    media.mkdir(parents=True)
    r = subprocess.run([pwsh, "-NoProfile", "-File", str(PS1), "-GameDir", str(game), "-Zomboid", str(zb)],
                       capture_output=True, text=True)
    check(r.returncode == 0 and r.stderr.strip() == "", f"run: {r.returncode} {r.stderr}")
    check("roofs_02: 2 Tiles von roofs_01 uebernommen" in r.stdout, r.stdout)
    check("roofs_04" not in r.stdout, "tileset that already has shapes must be skipped")
    out = (media / "tileGeometry.txt").read_text() if (media / "tileGeometry.txt").exists() else ""
    check(out.startswith("tileGeometry\r\n{\r\n    VERSION = 2,") or out.startswith("tileGeometry\n{\n    VERSION = 2,"), out[:60])
    check(out.count("        tile\n") + out.count("        tile\r\n") == 2, out)
    check("/* roofs_02 xy=0x0 <- roofs_01 (VPGeometryFix) */" in out and "xy=2x0" in out and "xy=1x0" not in out, out)
    check(out.count("rotate = 392394x0x0") == 2 and out.count("OpaquePixelsOnly = true") == 2, out)
    check(out.count("{") == out.count("}"), "balanced braces")
    check("furniture" not in out, "only roof tilesets")
    (game / "media/tileGeometry.txt").read_text()  # game file untouched (exists)
    r = subprocess.run([pwsh, "-NoProfile", "-File", str(PS1), "-Zomboid", str(zb), "-Remove"], capture_output=True, text=True)
    check(r.returncode == 0 and not (media / "tileGeometry.txt").exists(), "remove")

print(f"roofdata ps1: {'ok' if failed == 0 else str(failed) + ' failed'}")
sys.exit(1 if failed else 0)
