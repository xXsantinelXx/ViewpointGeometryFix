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
Events = { OnGameBoot = event(), OnGameStart = event(), OnKeyPressed = event(), OnTick = event(), OnPostUIDraw = event(),
  OnFillWorldObjectContextMenu = event(), OnMainMenuEnter = event() }

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

-- Minimal ISUI doubles: enough to create the panel and press its buttons.
function install_isui()
  ISPanel = {}
  ISPanel.__index = ISPanel
  function ISPanel:derive(name) local c = setmetatable({}, { __index = self }); c.__index = c; c.Type = name; return c end
  function ISPanel:new(x, y, w, h) local o = setmetatable({ x = x, y = y, w = w, h = h, children = {} }, self); return o end
  function ISPanel:initialise() end
  function ISPanel:instantiate() self:createChildren() end
  function ISPanel:createChildren() end
  function ISPanel:addChild(c) table.insert(self.children, c) end
  function ISPanel:render() end
  function ISPanel:drawText(t) table.insert(DRAWN, t) end
  function ISPanel:getHeight() return self.h end
  function ISPanel:setHeight(h) self.h = h end
  function ISPanel:addToUIManager() self.inUI = true end
  function ISPanel:removeFromUIManager() self.inUI = false end
  function ISPanel:setVisible(v) self.visible = v end
  function ISPanel:getIsVisible() return self.visible end
  ISButton = {}
  ISButton.__index = ISButton
  function ISButton:new(x, y, w, h, title, target, onclick)
    local b = setmetatable({ title = title, target = target, onclick = onclick }, self)
    table.insert(BUTTONS, b); return b
  end
  function ISButton:initialise() end
  function ISButton:instantiate() end
  function ISButton:setY(y) self.y = y end
  function ISButton:click() self.onclick(self.target, self) end
end
BUTTONS = {}
function button(title)
  for _, b in ipairs(BUTTONS) do if b.title == title then return b end end
  error("no button " .. title)
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
  function VPGF_status() return "Java-Teil: OK\nPZ: 42.21.0\nViewpoint: erkannt 0.1.5a-hotfix\nZombieBuddy: 2.3.4" end
  function VPGF_lastSummary() return "TILE 101,200,0 Objects#0 IsoObject sprite=roofs_01_12 kind~ROOF" end
end
"""


class Failure(Exception):
    pass


def check(cond, msg):
    if not cond:
        raise Failure(msg)


def fresh(with_java, with_isui=True):
    lua = lua51.LuaRuntime(unpack_returned_tuples=True)
    lua.execute(MOCKS)
    if with_java:
        lua.execute("install_java()")
    if with_isui:
        lua.execute("install_isui()")
    for f in sorted(LUA_DIR.glob("*.lua")):
        lua.execute(f.read_text(encoding="utf-8"))
    first = list(lua.eval("OUT").values())
    check(first == ["[VPGeometryFix] Lua loaded 0.2.1-test"], f"load line: {first}")
    lua.execute("OUT = {}")
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


def drawn(lua):
    return list(lua.eval("DRAWN").values())


def t_keybindings_optional_and_unbound():
    lua = fresh(False)
    entries = list(lua.eval("keyBinding").values())
    check([e["value"] for e in entries] == ["[VPGeometryFix]", "VPGF Panel", "VPGF Inspect Target"], "entries")
    check([e["key"] for e in entries[1:]] == [0, 0], "no default keys (F-keys etc. belong to PZ debug)")
    lua.execute("for k = 1, 255 do Events.OnKeyPressed.fire(k) end")
    check(calls(lua) == [] and out(lua) == [], "no key does anything by default")


def t_panel_at_game_start():
    lua = fresh(True)
    lua.execute("Events.OnGameBoot.fire(); Events.OnGameStart.fire()")
    check(len(out(lua)) == 0, f"Java prints the startup block, Lua adds nothing: {out(lua)}")
    lua.execute("BUTTONS[1].target:render()")
    d = drawn(lua)
    check(d[0] == "Viewpoint Geometry Fix 0.2.1-test", f"title: {d}")
    check("Viewpoint: erkannt 0.1.5a-hotfix" in d and "Diagnose: AUS" in d, f"status: {d}")
    check(any("Diagnose AN" in t for t in d), f"hint: {d}")


def t_panel_buttons_inspect():
    lua = fresh(True)
    lua.execute("Events.OnGameStart.fire(); CALLS = {}")
    lua.execute('button("Tile untersuchen"):click()')
    check(calls(lua) == [], f"inspect blocked while diagnose is off: {calls(lua)}")
    lua.execute('button("Diagnose AN"):click()')
    lua.execute('BUTTONS[1].target:render()')
    lua.execute('button("Tile untersuchen"):click()')
    c = calls(lua)
    check(c[0] == ["setDebug", True], f"debug: {c}")
    # facing target: floor(100.5 + 1) = 101, floor(200.5) = 200
    check(c[1] == ["reportBegin", "vor dem Spieler", 101, 200, 0], f"begin: {c[1]}")
    check([x[2] for x in c[2:6]] == ["z-1", "z+0", "z+1", "z+2"], f"column: {c}")
    check(c[6] == ["reportEnd"], f"end: {c}")
    lua.execute("DRAWN = {}; BUTTONS[1].target:render()")
    d = drawn(lua)
    check("TILE 101,200,0 Objects#0 IsoObject sprite=roofs_01_12 kind~ROOF" in d, f"result in panel: {d}")
    check("Bericht: C:/x/report.txt" in d, f"report path: {d}")
    check(out(lua) == [], f"Lua must not add console lines when Java is present: {out(lua)}")


def t_pin_target_and_close():
    lua = fresh(True)
    lua.execute('VPGF.setDebug(true); Events.OnGameStart.fire(); VPGF.setTarget(5.9, 6.1, 1); VPGF.inspect()')
    check(["reportBegin", "fixiertes Ziel", 5, 6, 1] in calls(lua), f"pinned: {calls(lua)}")
    lua.execute("BUTTONS[1].target:render()")
    lua.execute('button("Ziel loesen"):click(); button("X"):click()')
    check(lua.eval("BUTTONS[1].target.inUI") is False, "X closes the panel")


def t_context_menu():
    lua = fresh(True)
    lua.execute("""
      OPTS = {}
      local ctx = { addOption = function(self, name, target, fn) table.insert(OPTS, {name = name, fn = fn}); return {} end }
      local sq = { getX = function() return 7 end, getY = function() return 8 end, getZ = function() return 2 end }
      local obj = { getSquare = function() return sq end }
      VPGF.setDebug(true)
      Events.OnFillWorldObjectContextMenu.fire(0, ctx, { obj }, false)
      for _, o in ipairs(OPTS) do if string.find(o.name, "untersuchen", 1, true) then o.fn() end end
    """)
    names = [o["name"] for o in lua.eval("OPTS").values()]
    check("Dieses Tile untersuchen (7,8,2)" in names, f"menu: {names}")
    check(["reportBegin", "Rechtsklick", 7, 8, 2] in calls(lua), f"inspect via menu: {calls(lua)}")


def t_fallback_overlay_without_isui():
    lua = fresh(False, with_isui=False)
    lua.execute("Events.OnGameStart.fire(); Events.OnPostUIDraw.fire()")
    d = drawn(lua)
    check(any("Java-Teil: NICHT geladen" in t for t in d), f"overlay shows missing Java: {d}")
    lua.execute("VPGF.hidePanel()")
    check(lua.eval("#Events.OnPostUIDraw.list") == 0, "overlay removed when closed")


def t_hover_updates_panel_only():
    lua = fresh(True)
    lua.execute('VPGF.setDebug(true); VPGF.setHover(true)')
    check(lua.eval("#Events.OnTick.list") == 1, "hover adds OnTick")
    lua.execute("for i = 1, 20 do Events.OnTick.fire() end; VPGF.setDebug(false)")
    check(out(lua) == [], f"hover must not log: {out(lua)}")
    check(lua.eval("#Events.OnTick.list") == 0, "hover removed when diagnose turns off")


def t_rebind_from_options():
    lua = fresh(True)
    lua.execute('Events.OnGameStart.fire(); BOUND["VPGF Panel"] = 25; Events.OnKeyPressed.fire(25)')
    check(lua.eval("BUTTONS[1].target.inUI") is False, "bound key toggles the panel")


def t_main_menu_badge():
    lua = fresh(False)
    lua.execute("Events.OnMainMenuEnter.fire(); Events.OnPostUIDraw.fire()")
    d = list(lua.eval("DRAWN").values())
    check(any("VPGeometryFix 0.2.1-test geladen - Java-Teil: NICHT geladen" in t for t in d), f"badge: {d}")
    lua.execute("Events.OnGameStart.fire()")
    check(lua.eval("#Events.OnPostUIDraw.list") == 0, "badge removed in game (panel uses ISPanel)")


def t_handler_errors_are_reported():
    lua = fresh(True)
    lua.execute("getCell = function() error('boom') end; VPGF.setDebug(true); CALLS = {}")
    lua.execute("Events.OnFillWorldObjectContextMenu.fire(0, nil, {}, false)")  # nil context is ignored
    lua.execute("""
      local ctx = { addOption = function() error('menu broken') end }
      Events.OnFillWorldObjectContextMenu.fire(0, ctx, {}, false)
      Events.OnFillWorldObjectContextMenu.fire(0, ctx, {}, false)
    """)
    errs = [l for l in out(lua) if "ERROR in ContextMenu" in l]
    check(len(errs) == 1 and "menu broken" in errs[0], f"error reported once: {out(lua)}")


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
