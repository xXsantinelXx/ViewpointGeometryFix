#!/usr/bin/env python3
"""Runs the mod's Lua against mocked Project Zomboid globals in real Lua 5.1
(Kahlua is a Lua 5.1 dialect). Requires: pip install lupa

Checks: syntax, startup block (with and without the Java part), hotkey gating,
target resolution and the Java bridge call sequence. It cannot prove that the
real game API behaves like the mocks; see docs/DIAGNOSTICS.md.
"""
import pathlib
import sys

from lupa import lua51

ROOT = pathlib.Path(__file__).resolve().parents[2]
LUA_DIR = ROOT / "resources/mod/42/media/lua/client"

MOCKS = r"""
OUT = {}
CALLS = {}
function print(s) table.insert(OUT, tostring(s)) end
local function call(name, ...) table.insert(CALLS, {name, ...}) end

Keyboard = { KEY_HOME = 199, KEY_END = 207, KEY_PRIOR = 201, KEY_NEXT = 209,
  getKeyName = function(c) return "K" .. c end }
keyBinding = {}
BOUND = {}  -- user rebinds: name -> code
UIFont = { Small = 1 }
DRAWN = {}
function getTextManager() return { DrawString = function(self, f, x, y, t) table.insert(DRAWN, t) end } end
NOW = 0
function getTimestampMs() return NOW end
local function event()
  local e = { list = {} }
  function e.Add(f) table.insert(e.list, f) end
  function e.Remove(f) for i, g in ipairs(e.list) do if g == f then table.remove(e.list, i) return end end end
  function e.fire(...) for _, f in ipairs(e.list) do f(...) end end
  return e
end
Events = { OnGameBoot = event(), OnGameStart = event(), OnKeyPressed = event(), OnTick = event(), OnPostUIDraw = event() }

MODS = { "\\ZombieBuddy", "\\Viewpoint", "\\ViewpointGeometryFix" }
function getActivatedMods()
  return { size = function(self) return #MODS end, get = function(self, i) return MODS[i + 1] end }
end
local core = { getVersion = function(self) return "42.21.0" end,
  getKey = function(self, name)
    if BOUND[name] then return BOUND[name] end
    for _, e in ipairs(keyBinding) do if e.value == name then return e.key end end
    return 0
  end }
function getCore() return core end


local vec = { getX = function(self) return 1 end, getY = function(self) return 0 end }
PLAYER = {
  getX = function(self) return 100.5 end, getY = function(self) return 200.5 end, getZ = function(self) return 0 end,
  getForwardDirection = function(self) return vec end, getPlayerNum = function(self) return 0 end,
}
function getPlayer() return PLAYER end
local sprite = { getName = function(self) return "roofs_01_12" end }
local obj = { getSprite = function(self) return sprite end, getObjectName = function(self) return "Roof" end }
local objs = { size = function(self) return 1 end, get = function(self, i) return obj end }
function getCell()
  return { getGridSquare = function(self, x, y, z)
    return { x = x, y = y, z = z, getObjects = function(self) return objs end }
  end }
end

function install_java()
  JDEBUG = false
  function VPGF_javaAvailable() return true end
  function VPGF_isDebug() return JDEBUG end
  function VPGF_setDebug(b) JDEBUG = b; call("setDebug", b) end
  function VPGF_startupReport(...) call("startupReport", ...) end
  function VPGF_viewpointFirstPerson() return "true" end
  function VPGF_viewpointState() return "View.enabled=true" end
  function VPGF_reportBegin(...) call("reportBegin", ...) end
  function VPGF_reportSquare(sq, label) call("reportSquare", sq and (sq.x .. "," .. sq.y .. "," .. sq.z) or "nil", label) end
  function VPGF_reportEnd() call("reportEnd") return "C:/x/report.txt" end
  function VPGF_inventory(w) call("inventory", w) return "C:/x/inv.txt" end
end
"""


class Failure(Exception):
    pass


def check(cond, msg):
    if not cond:
        raise Failure(msg)


def fresh(with_java):
    lua = lua51.LuaRuntime(unpack_returned_tuples=True)
    lua.execute(MOCKS)
    if with_java:
        lua.execute("install_java()")
    for f in sorted(LUA_DIR.glob("*.lua")):
        lua.execute(f.read_text(encoding="utf-8"))
    return lua


def out(lua):
    return list(lua.eval("OUT").values())


def calls(lua):
    return [list(c.values()) for c in lua.eval("CALLS").values()]


def t_startup_without_java():
    lua = fresh(False)
    lua.execute("Events.OnGameBoot.fire()")
    lines = out(lua)
    expected = ["[VPGeometryFix] Loaded", "[VPGeometryFix] PZ version: 42.21.0 (from Lua)",
                "[VPGeometryFix] Viewpoint detected: mod enabled (\\Viewpoint), Java state unknown",
                "[VPGeometryFix] ZombieBuddy detected: no", "[VPGeometryFix] Debug mode: OFF (Lua only)"]
    check(lines == expected, f"fallback block mismatch: {lines}")
    lua.execute("Events.OnGameStart.fire()")
    check(sum(1 for l in out(lua) if l == "[VPGeometryFix] Loaded") == 1, "startup block must print only once")


def t_startup_with_java():
    lua = fresh(True)
    lua.execute("Events.OnGameBoot.fire()")
    c = calls(lua)
    check(c == [["startupReport", "42.21.0", True, "\\Viewpoint"]], f"bridge call mismatch: {c}")


def t_keybindings_registered():
    lua = fresh(False)
    vals = [e["value"] for e in lua.eval("keyBinding").values()]
    check(vals == ["[VPGeometryFix]", "VPGF Toggle Debug", "VPGF Inspect Target", "VPGF Hover Mode",
                   "VPGF Class Inventory"], f"keyBinding: {vals}")
    keys = [e["key"] for e in list(lua.eval("keyBinding").values())[1:]]
    check(keys == [199, 207, 201, 209], f"defaults: {keys}")


def t_debug_gate_and_feedback():
    lua = fresh(True)
    lua.execute("Events.OnKeyPressed.fire(Keyboard.KEY_END)")
    check(calls(lua) == [], "inspect must be ignored while debug is off")
    check(any("Debug ist AUS" in l for l in out(lua)), f"should explain why: {out(lua)}")
    lua.execute("Events.OnPostUIDraw.fire()")
    check(any("Debug ist AUS" in t for t in lua.eval("DRAWN").values()), "message drawn on screen")
    lua.execute("NOW = 10000; Events.OnPostUIDraw.fire()")
    check(lua.eval("#Events.OnPostUIDraw.list") == 0, "overlay removed after message expires (no cost)")


def t_inspect_sequence():
    lua = fresh(True)
    lua.execute("Events.OnKeyPressed.fire(Keyboard.KEY_HOME)")
    check(lua.eval("#Events.OnPostUIDraw.list") == 1, "overlay on while debug on")
    lua.execute("Events.OnKeyPressed.fire(Keyboard.KEY_END)")
    c = calls(lua)
    check(c[0] == ["setDebug", True], f"debug toggle: {c}")
    # first person -> facing target: floor(100.5 + 1) = 101, floor(200.5) = 200
    check(c[1] == ["reportBegin", "facing+1", 101, 200, 0], f"begin: {c[1]}")
    labels = [x[2] for x in c[2:6]]
    check(labels == ["z-1", "z+0", "z+1", "z+2"], f"column labels: {labels}")
    check(c[3][1] == "101,200,0", f"square coords: {c[3]}")
    check(c[6] == ["reportEnd"], f"end: {c[6]}")
    lua.execute("Events.OnPostUIDraw.fire()")
    drawn = list(lua.eval("DRAWN").values())
    check(any("VPGeometryFix DEBUG" in t for t in drawn), f"debug banner: {drawn}")
    check(any("report.txt" in t for t in drawn), f"report path on screen: {drawn}")


def t_rebind_from_options():
    lua = fresh(True)
    lua.execute('BOUND["VPGF Toggle Debug"] = 59; Events.OnKeyPressed.fire(Keyboard.KEY_HOME)')
    check(calls(lua) == [], "old default must not fire after rebinding")
    lua.execute("Events.OnKeyPressed.fire(59)")
    check(calls(lua) == [["setDebug", True]], f"rebound key: {calls(lua)}")


def t_pinned_target_and_inventory():
    lua = fresh(True)
    lua.execute("VPGF.setDebug(true); VPGF.setTarget(5.9, 6.1, 1); VPGF.inspect(); VPGF.inventory()")
    c = calls(lua)
    check(["reportBegin", "pinned", 5, 6, 1] in c, f"pinned: {c}")
    check(c[-1] == ["inventory", "viewpoint"], f"inventory: {c}")


def t_hover_toggle():
    lua = fresh(True)
    lua.execute("VPGF.setDebug(true); Events.OnKeyPressed.fire(Keyboard.KEY_PRIOR)")
    check(lua.eval("#Events.OnTick.list") == 1, "hover adds OnTick")
    lua.execute("for i = 1, 20 do Events.OnTick.fire() end")
    check(any(l.startswith("[VPGeometryFix] hover facing+1 101,200,0: [0] Roof:roofs_01_12") for l in out(lua)),
          f"hover summary: {out(lua)}")
    lua.execute("Events.OnKeyPressed.fire(Keyboard.KEY_HOME)")  # debug off also stops hover
    check(lua.eval("#Events.OnTick.list") == 0, "hover removed when debug turns off")


def t_game_start_message():
    lua = fresh(False)
    lua.execute("Events.OnGameStart.fire(); Events.OnPostUIDraw.fire()")
    drawn = list(lua.eval("DRAWN").values())
    check(any("aktiv (OHNE Java-Teil)" in t and "K199" in t for t in drawn), f"start message: {drawn}")


def main():
    tests = [v for k, v in globals().items() if k.startswith("t_")]
    failed = 0
    for t in tests:
        try:
            t()
            print("ok   lua:" + t.__name__)
        except Exception as e:  # noqa: BLE001
            failed += 1
            print("FAIL lua:" + t.__name__ + ": " + str(e))
    print(f"lua passed: {len(tests) - failed}, failed: {failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
