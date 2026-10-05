--[[
    Viewpoint Geometry Fix - diagnostic build (client only).

    Does NOT change rendering, gameplay, save games, Viewpoint or game files.

    Bedienung ohne Tasten (F-Tasten belegt die Debug-Version von PZ selbst):
      * Fenster "Viewpoint Geometry Fix" erscheint beim Laden eines Spielstands,
        verschiebbar, mit Schaltflaechen.
      * Rechtsklick in die Welt: Eintrag "VPGeometryFix" (Fenster oeffnen,
        angeklicktes Tile untersuchen, als Ziel fixieren).
      * Optional: zwei Tasten in Optionen > Tastenbelegung > [VPGeometryFix],
        standardmaessig NICHT belegt.
      * Debug-Konsole: VPGF.showPanel(), VPGF.inspect(), VPGF.inspectAt(x,y,z),
        VPGF.setTarget(x,y,z), VPGF.clearTarget(), VPGF.inventory("viewpoint"|"game"),
        VPGF.viewpointState(), VPGF.dumpStatics("viewpoint.core.View")

    console.txt bekommt nur: den Startblock, TILE-Zeilen einer Untersuchung und
    den Berichtspfad. Alles Weitere steht im Fenster bzw. in den Berichtsdateien.
    Kosten im Normalbetrieb: Zeichnen des Fensters (nur solange offen); Hover
    nutzt OnTick nur solange eingeschaltet.
]]

VPGF = VPGF or {}
VPGF.VERSION = "0.3.1-test"
VPGF.PREFIX = "[VPGeometryFix] "
-- First line in console.txt: proves the Lua file was loaded at all.
print(VPGF.PREFIX .. "Lua loaded " .. VPGF.VERSION)

VPGF.facingDistance = VPGF.facingDistance or 1   -- tiles ahead of the player
VPGF.columnBelow = VPGF.columnBelow or 1         -- inspect z-1 .. z+columnAbove
VPGF.columnAbove = VPGF.columnAbove or 2         -- roofs usually sit 1-2 levels above the player
VPGF.hoverInterval = VPGF.hoverInterval or 20    -- ticks between hover checks

-- Optional key bindings, unbound by default (key = 0). Pattern verified on 42.21 by PeekAView.
VPGF.bindings = {
    { action = "panel", name = "VPGF Panel" },
    { action = "inspect", name = "VPGF Inspect Target" },
}
if keyBinding then
    table.insert(keyBinding, { value = "[VPGeometryFix]" })
    for _, b in ipairs(VPGF.bindings) do
        table.insert(keyBinding, { value = b.name, key = 0 })
    end
end

local luaDebug = false
local pinned = nil
local hoverOn = false
local hoverTick = 0
local lastHoverKey = nil
local reported = false
local results = {}          -- last result lines shown in the panel
local panel = nil
local overlayOn = false

local function log(msg)
    print(VPGF.PREFIX .. tostring(msg))
end

local function try(fn, ...)
    local ok, res = pcall(fn, ...)
    if ok then return res end
    return nil
end

local function floor(v) return math.floor(tonumber(v) or 0) end

-- True when the Java part (ZombieBuddy-loaded JAR) registered its globals.
function VPGF.java()
    return VPGF_javaAvailable ~= nil and try(VPGF_javaAvailable) == true
end

function VPGF.isDebug()
    if VPGF.java() then return try(VPGF_isDebug) == true end
    return luaDebug
end

-- Replaces the result area of the panel (and the fallback overlay).
local function setResults(lines)
    results = {}
    for _, l in ipairs(lines) do
        if #results >= 8 then table.insert(results, "...") break end
        table.insert(results, tostring(l))
    end
end

local function splitLines(s)
    local out = {}
    for line in string.gmatch(tostring(s or ""), "[^\n]+") do table.insert(out, line) end
    return out
end

-------------------------------------------------------------------------------
-- Status
-------------------------------------------------------------------------------

local function viewpointModIds()
    local found = {}
    local mods = getActivatedMods and try(getActivatedMods)
    if not mods then return found end
    for i = 0, mods:size() - 1 do
        local id = tostring(mods:get(i))
        local lower = string.lower((id:gsub("^\\", "")))
        if lower == "viewpoint" or (string.find(lower, "viewpoint", 1, true)
                and lower ~= "viewpointgeometryfix" and lower ~= "projectviewpointvr") then
            table.insert(found, id)
        end
    end
    return found
end

local function luaPzVersion()
    local core = getCore and getCore()
    if not core then return "" end
    return tostring(try(function() return core:getVersion() end) or "")
end

function VPGF.statusLines()
    local lines
    if VPGF.java() then
        lines = splitLines(try(VPGF_status) or "Java-Teil: OK")
    else
        local ids = viewpointModIds()
        lines = {
            "Java-Teil: NICHT geladen (ZombieBuddy-Freigabe pruefen)",
            "PZ: " .. luaPzVersion(),
            "Viewpoint: " .. ((#ids > 0) and "Mod aktiv (nur Mod-Liste)" or "nicht in Mod-Liste"),
        }
    end
    table.insert(lines, "Diagnose: " .. (VPGF.isDebug() and "AN" or "AUS")
        .. (pinned and string.format("   Ziel fixiert: %d,%d,%d", pinned.x, pinned.y, pinned.z) or "")
        .. (hoverOn and "   Hover: AN" or ""))
    return lines
end

function VPGF.startupReport()
    local ids = viewpointModIds()
    local idList = table.concat(ids, ",")
    if VPGF.java() then
        try(VPGF_startupReport, luaPzVersion(), #ids > 0, idList)
        return
    end
    local zb = "no"
    if ZombieBuddy ~= nil then
        local v = try(function() return ZombieBuddy.getVersion() end)
        zb = "yes (version " .. tostring(v) .. ") but VPGeometryFix Java part NOT loaded - check ZombieBuddy approval"
    end
    log("Loaded")
    log("PZ version: " .. luaPzVersion() .. " (from Lua)")
    log("Viewpoint detected: " .. (#ids > 0 and ("mod enabled (" .. idList .. "), Java state unknown") or "no"))
    log("ZombieBuddy detected: " .. zb)
    log("Debug mode: " .. (luaDebug and "ON" or "OFF") .. " (Lua only)")
end

-------------------------------------------------------------------------------
-- Target resolution
-------------------------------------------------------------------------------

local function facingTarget(player)
    local fd = try(function() return player:getForwardDirection() end)
    local fx, fy = 0, 0
    if fd then
        fx = tonumber(try(function() return fd:getX() end)) or 0
        fy = tonumber(try(function() return fd:getY() end)) or 0
    end
    local d = VPGF.facingDistance
    return floor(player:getX() + fx * d), floor(player:getY() + fy * d), floor(player:getZ()), "vor dem Spieler"
end

-- Returns x, y, z, source. Priority: pinned > in front of the player.
-- (Viewpoint's crosshair pick is not known yet; see ViewpointProbe.pickedTarget.)
function VPGF.resolveTarget()
    if pinned then return pinned.x, pinned.y, pinned.z, "fixiertes Ziel" end
    local player = getPlayer and getPlayer()
    if not player then return nil end
    return facingTarget(player)
end

function VPGF.setTarget(x, y, z)
    pinned = { x = floor(x), y = floor(y), z = floor(z) }
    setResults({ string.format("Ziel fixiert: %d,%d,%d", pinned.x, pinned.y, pinned.z) })
end

function VPGF.clearTarget()
    pinned = nil
    setResults({ "Ziel geloest - untersucht wird wieder das Tile vor dem Spieler" })
end

-------------------------------------------------------------------------------
-- Actions
-------------------------------------------------------------------------------

function VPGF.setDebug(on)
    luaDebug = on and true or false
    if VPGF.java() then
        try(VPGF_setDebug, luaDebug)
    else
        log("Debug mode: " .. (luaDebug and "ON" or "OFF") .. " (runtime toggle, Lua only)")
    end
    if not luaDebug then VPGF.setHover(false) end
    setResults({ "Diagnose " .. (luaDebug and "AN - jetzt 'Tile untersuchen' moeglich" or "AUS") })
end

-- Lua-only one-line summary of a square (hover mode and fallback without Java).
local function luaSquareSummary(sq)
    if not sq then return "nicht geladen" end
    local parts = {}
    local objs = try(function() return sq:getObjects() end)
    if objs then
        for i = 0, objs:size() - 1 do
            local o = objs:get(i)
            local spr = try(function() return o:getSprite():getName() end)
            table.insert(parts, "#" .. i .. " " .. tostring(spr))
        end
    end
    return (#parts > 0) and table.concat(parts, "  ") or "leer"
end

function VPGF.inspectAt(x, y, z, source)
    if not VPGF.isDebug() then
        setResults({ "Diagnose ist AUS - zuerst 'Diagnose AN' klicken" })
        return
    end
    local cell = getCell and getCell()
    if not cell then setResults({ "kein Spielstand geladen" }) return end
    x, y, z = floor(x), floor(y), floor(z)
    source = source or "manuell"
    local lines = { string.format("Tile %d,%d,%d (%s):", x, y, z, source) }
    if VPGF.java() then
        try(VPGF_reportBegin, source, x, y, z)
        for dz = -VPGF.columnBelow, VPGF.columnAbove do
            try(VPGF_reportSquare, cell:getGridSquare(x, y, z + dz), "z" .. (dz >= 0 and "+" or "") .. dz)
        end
        local path = try(VPGF_reportEnd)
        for _, l in ipairs(splitLines(try(VPGF_lastSummary))) do table.insert(lines, l) end
        table.insert(lines, path and ("Bericht: " .. tostring(path)) or "Bericht konnte nicht geschrieben werden")
    else
        for dz = -VPGF.columnBelow, VPGF.columnAbove do
            local line = "TILE " .. x .. "," .. y .. "," .. (z + dz) .. " " .. luaSquareSummary(cell:getGridSquare(x, y, z + dz))
            log(line)
            table.insert(lines, line)
        end
    end
    setResults(lines)
end

function VPGF.inspect()
    local x, y, z, src = VPGF.resolveTarget()
    if not x then setResults({ "kein Ziel (kein Spieler?)" }) return end
    VPGF.inspectAt(x, y, z, src)
end

function VPGF.inventory(which)
    if not VPGF.isDebug() then setResults({ "Diagnose ist AUS" }) return end
    if not VPGF.java() then setResults({ "Inventar braucht den Java-Teil (ZombieBuddy)" }) return end
    local path = try(VPGF_inventory, which or "viewpoint")
    setResults({ path and ("Inventar: " .. tostring(path)) or "Inventar fehlgeschlagen (Viewpoint gefunden?)",
        "Nur lokal - nicht veroeffentlichen" })
    return path
end

-- Roof fix variant B (Java advice on Viewpoint's TileMeshes.geometryFor). Status lines come from VPGF_status.
function VPGF.roofFix()
    return VPGF.java() and try(VPGF_isRoofFix) == true
end

function VPGF.setRoofFix(on)
    if not VPGF.java() then setResults({ "Dach-Fix braucht den Java-Teil (ZombieBuddy)" }) return end
    try(VPGF_setRoofFix, on and true or false)
    setResults({ "Dach-Fix " .. (on and "AN" or "AUS") .. " - wirkt fuer neu aufgebaute Bereiche,",
        "fuer die volle Wirkung das Spiel neu starten (Einstellung bleibt gespeichert)." })
end

-- "Dach-Daten": one Java report on Viewpoint's roof meshes and shape sources (no patch, on demand).
function VPGF.roofData()
    if not VPGF.java() then setResults({ "Dach-Daten brauchen den Java-Teil (ZombieBuddy)" }) return end
    local s = try(VPGF_roofData)
    local lines = { "Dach-Daten:" }
    for _, l in ipairs(splitLines(s or "fehlgeschlagen")) do table.insert(lines, l) end
    setResults(lines)
    return s
end

function VPGF.viewpointState()
    if not VPGF.java() then setResults({ "braucht den Java-Teil (ZombieBuddy)" }) return end
    local s = try(VPGF_viewpointState)
    setResults({ "Viewpoint: " .. tostring(s) })
    return s
end

function VPGF.dumpStatics(className)
    if not VPGF.java() then return end
    try(VPGF_dumpStatics, className)
end

-------------------------------------------------------------------------------
-- Hover: panel shows the target square whenever it changes (no console spam).
-------------------------------------------------------------------------------

local function onHoverTick()
    hoverTick = hoverTick + 1
    if hoverTick < VPGF.hoverInterval then return end
    hoverTick = 0
    local x, y, z, src = VPGF.resolveTarget()
    if not x then return end
    local key = x .. "," .. y .. "," .. z
    if key == lastHoverKey then return end
    lastHoverKey = key
    local cell = getCell and getCell()
    if not cell then return end
    local lines = { "Hover " .. key .. " (" .. src .. "):" }
    for dz = 0, VPGF.columnAbove do
        table.insert(lines, "z+" .. dz .. ": " .. luaSquareSummary(cell:getGridSquare(x, y, z + dz)))
    end
    setResults(lines)
end

function VPGF.setHover(on)
    on = on and true or false
    if on and not VPGF.isDebug() then setResults({ "Diagnose ist AUS" }) return end
    if on == hoverOn then return end
    hoverOn = on
    lastHoverKey = nil
    if on then Events.OnTick.Add(onHoverTick) else Events.OnTick.Remove(onHoverTick) end
end

-------------------------------------------------------------------------------
-- Panel (vanilla ISPanel/ISButton). Fallback: plain text overlay.
-------------------------------------------------------------------------------

local PANEL_W, LINE_H, BTN_H = 560, 16, 22

local function buttonDefs()
    return {
        { function() return VPGF.isDebug() and "Diagnose AUS" or "Diagnose AN" end,
          function() VPGF.setDebug(not VPGF.isDebug()) end },
        { function() return "Tile untersuchen" end, function() VPGF.inspect() end },
        { function() return pinned and "Ziel loesen" or "Ziel fixieren" end,
          function()
              if pinned then VPGF.clearTarget() return end
              local x, y, z = VPGF.resolveTarget()
              if x then VPGF.setTarget(x, y, z) end
          end },
        { function() return hoverOn and "Hover AUS" or "Hover AN" end, function() VPGF.setHover(not hoverOn) end },
        { function() return VPGF.roofFix() and "Dach-Fix AUS" or "Dach-Fix AN" end,
          function() VPGF.setRoofFix(not VPGF.roofFix()) end },
        { function() return "Dach-Daten" end, function() VPGF.roofData() end },
        { function() return "X" end, function() VPGF.hidePanel() end },
    }
end
VPGF.buttonDefs = buttonDefs

local function textLines()
    local lines = { { "Viewpoint Geometry Fix " .. VPGF.VERSION, 1, 0.8, 0.2 } }
    for _, l in ipairs(VPGF.statusLines()) do
        local bad = string.find(l, "NICHT", 1, true) or string.find(l, "nicht gefunden", 1, true)
        table.insert(lines, { l, bad and 1 or 0.85, bad and 0.35 or 0.85, bad and 0.35 or 0.85 })
    end
    for _, l in ipairs(results) do table.insert(lines, { l, 0.45, 1, 0.45 }) end
    return lines
end
VPGF.textLines = textLines

local function createPanel()
    if not ISPanel or not ISButton then return nil end
    local P = ISPanel:derive("VPGFPanel")

    function P:createChildren()
        ISPanel.createChildren(self)
        self.vpgfButtons = {}
        local x = 6
        local defs = buttonDefs()
        for i, def in ipairs(defs) do
            local w = (i == #defs) and 24 or 84
            local b = ISButton:new(x, 0, w, BTN_H, def[1](), self, function() def[2]() end)
            b:initialise()
            b:instantiate()
            self:addChild(b)
            self.vpgfButtons[i] = { button = b, label = def[1] }
            x = x + w + 4
        end
    end

    function P:render()
        ISPanel.render(self)
        local y = 6
        for _, l in ipairs(textLines()) do
            self:drawText(l[1], 8, y, l[2], l[3], l[4], 1, UIFont.Small)
            y = y + LINE_H
        end
        y = y + 4
        for _, e in ipairs(self.vpgfButtons or {}) do
            e.button:setY(y)
            e.button.title = e.label()
        end
        local h = y + BTN_H + 6
        if self:getHeight() ~= h then self:setHeight(h) end
    end

    local p = P:new(20, 120, PANEL_W, 140)
    p.moveWithMouse = true
    p.backgroundColor = { r = 0, g = 0, b = 0, a = 0.8 }
    p.borderColor = { r = 1, g = 0.8, b = 0.2, a = 1 }
    p:initialise()
    p:instantiate()
    return p
end

local function drawOverlay()
    local tm = getTextManager and getTextManager()
    if not tm then return end
    local y = 120
    for _, l in ipairs(textLines()) do
        try(function() tm:DrawString(UIFont.Small, 21, y + 1, l[1], 0, 0, 0, 1) end)
        try(function() tm:DrawString(UIFont.Small, 20, y, l[1], l[2], l[3], l[4], 1) end)
        y = y + LINE_H
    end
end

function VPGF.showPanel()
    if panel == nil then
        local ok, res = pcall(createPanel)
        if not ok then log("panel unavailable, using text overlay: " .. tostring(res)) end
        panel = (ok and res) or false
    end
    if panel then
        try(function() panel:addToUIManager() panel:setVisible(true) end)
    elseif not overlayOn and Events.OnPostUIDraw then
        overlayOn = true
        Events.OnPostUIDraw.Add(drawOverlay)
    end
end

function VPGF.hidePanel()
    if panel then try(function() panel:setVisible(false) panel:removeFromUIManager() end) end
    if overlayOn then
        overlayOn = false
        Events.OnPostUIDraw.Remove(drawOverlay)
    end
end

function VPGF.togglePanel()
    local visible = (panel and try(function() return panel:getIsVisible() end)) or overlayOn
    if visible then VPGF.hidePanel() else VPGF.showPanel() end
end

-------------------------------------------------------------------------------
-- Events
-------------------------------------------------------------------------------

local function onFillWorldObjectContextMenu(playerNum, context, worldobjects, test)
    if test or not context then return end
    local sq
    for _, o in ipairs(worldobjects or {}) do
        sq = try(function() return o:getSquare() end)
        if sq then break end
    end
    local option = context:addOption("VPGeometryFix", nil, nil)
    local sub = ISContextMenu and try(function() return ISContextMenu:getNew(context) end)
    local menu = sub or context
    if sub then context:addSubMenu(option, sub) end
    menu:addOption("Fenster oeffnen", nil, function() VPGF.showPanel() end)
    if sq then
        local x, y, z = sq:getX(), sq:getY(), sq:getZ()
        menu:addOption(string.format("Dieses Tile untersuchen (%d,%d,%d)", x, y, z), nil,
            function() VPGF.showPanel() VPGF.inspectAt(x, y, z, "Rechtsklick") end)
        menu:addOption("Dieses Tile als Ziel fixieren", nil, function() VPGF.showPanel() VPGF.setTarget(x, y, z) end)
    end
end

local function onKeyPressed(key)
    if not key or key == 0 or not getCore then return end
    for _, b in ipairs(VPGF.bindings) do
        local code = try(function() return getCore():getKey(b.name) end)
        if code and code ~= 0 and code == key then
            if b.action == "panel" then VPGF.togglePanel() else VPGF.inspect() end
            return
        end
    end
end

local function onGameBoot()
    if reported then return end
    reported = true
    VPGF.startupReport()
end

-- One automatic "Dach-Daten" report per game start, after VPGF.autoRoofDataMinutes
-- in-game minutes (EveryOneMinute: no per-frame work), so the Doctor always has data.
VPGF.autoRoofDataMinutes = 40
local autoMinutes = 0
local function onEveryMinuteRoofData()
    autoMinutes = autoMinutes + 1
    if autoMinutes < VPGF.autoRoofDataMinutes then return end
    if Events.EveryOneMinute and Events.EveryOneMinute.Remove then Events.EveryOneMinute.Remove(onEveryMinuteRoofData) end
    if VPGF.java() then VPGF.roofData() end
end

local function onGameStart()
    onGameBoot() -- fallback if OnGameBoot ran before this file was loaded
    setResults({ "Mod aktiv. 'Diagnose AN' klicken, dann 'Tile untersuchen'.",
        "Rechtsklick in die Welt -> VPGeometryFix. In First-Person ggf. mit O in die",
        "normale Ansicht wechseln, um die Maus fuer das Fenster freizubekommen." })
    VPGF.showPanel()
    if VPGF.java() and Events.EveryOneMinute and Events.EveryOneMinute.Add then
        autoMinutes = 0
        Events.EveryOneMinute.Add(onEveryMinuteRoofData)
    end
end

-- Main menu: small text so the user sees the mod is loaded before entering a save.
local menuBadge = false
local function drawMenuBadge()
    local tm = getTextManager and getTextManager()
    if not tm then return end
    local text = "VPGeometryFix " .. VPGF.VERSION .. " geladen - Java-Teil: "
        .. (VPGF.java() and "OK" or "NICHT geladen")
    try(function() tm:DrawString(UIFont.Small, 11, 11, text, 0, 0, 0, 1) end)
    try(function() tm:DrawString(UIFont.Small, 10, 10, text, 1, 0.8, 0.2, 1) end)
end

local function onMainMenuEnter()
    if menuBadge or not Events.OnPostUIDraw then return end
    menuBadge = true
    Events.OnPostUIDraw.Add(drawMenuBadge)
end

local function removeMenuBadge()
    if not menuBadge then return end
    menuBadge = false
    Events.OnPostUIDraw.Remove(drawMenuBadge)
end

-- Every handler runs protected: an error is reported once with our prefix
-- (console.txt + panel) instead of silently breaking the mod.
local function safe(name, fn)
    local reportedError = false
    return function(...)
        local ok, err = pcall(fn, ...)
        if not ok and not reportedError then
            reportedError = true
            log("ERROR in " .. name .. ": " .. tostring(err))
            setResults({ "FEHLER in " .. name .. ": " .. tostring(err) })
        end
    end
end

local function register(eventName, name, fn)
    local ev = Events and Events[eventName]
    if ev and ev.Add then
        ev.Add(safe(name, fn))
    else
        log("event " .. eventName .. " not available")
    end
end

register("OnGameBoot", "OnGameBoot", function() onGameBoot() onMainMenuEnter() end)
register("OnMainMenuEnter", "OnMainMenuEnter", onMainMenuEnter)
register("OnGameStart", "OnGameStart", function() removeMenuBadge() onGameStart() end)
register("OnKeyPressed", "OnKeyPressed", onKeyPressed)
register("OnFillWorldObjectContextMenu", "ContextMenu", onFillWorldObjectContextMenu)
