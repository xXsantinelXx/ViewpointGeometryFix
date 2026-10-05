--[[
    Viewpoint Geometry Fix - diagnostic build (client only).

    Does NOT change rendering, gameplay, save games, Viewpoint or game files.
    All heavy work runs only on explicit user action (hotkeys / console calls).
    Normal play cost: one OnKeyPressed check per key press. The on-screen
    overlay (OnPostUIDraw) is registered only while debug mode is on or a
    message is showing; hover mode adds a throttled OnTick only while on.

    Hotkeys are single keys (no modifiers), listed and rebindable in
    Options > Key Bindings > [VPGeometryFix]. Registration uses the keyBinding
    table + getCore():getKey(name), the pattern PeekAView uses on 42.21.

    Console API (debug console / Lua):
        VPGF.setDebug(true|false)
        VPGF.inspect()                -- inspect current target (needs debug on)
        VPGF.inspectAt(x, y, z)       -- inspect a fixed square column
        VPGF.setTarget(x, y, z)       -- pin the target; VPGF.clearTarget()
        VPGF.inventory("viewpoint"|"game")
        VPGF.viewpointState()
        VPGF.dumpStatics("viewpoint.core.View")
]]

VPGF = VPGF or {}
VPGF.VERSION = "0.1.1-diag"
VPGF.PREFIX = "[VPGeometryFix] "

-- Key binding names (shown in Options > Key Bindings) and defaults.
-- Fallback numbers are the LWJGL key codes in case a Keyboard constant is missing.
local function kc(name, code)
    local v = Keyboard and Keyboard[name]
    return v or code
end
VPGF.bindings = {
    { action = "toggleDebug", name = "VPGF Toggle Debug", default = kc("KEY_HOME", 199) },
    { action = "inspect", name = "VPGF Inspect Target", default = kc("KEY_END", 207) },
    { action = "hover", name = "VPGF Hover Mode", default = kc("KEY_PRIOR", 201) },
    { action = "inventory", name = "VPGF Class Inventory", default = kc("KEY_NEXT", 209) },
}
if keyBinding then
    table.insert(keyBinding, { value = "[VPGeometryFix]" })
    for _, b in ipairs(VPGF.bindings) do
        table.insert(keyBinding, { value = b.name, key = b.default })
    end
end
VPGF.facingDistance = VPGF.facingDistance or 1   -- tiles ahead of the player
VPGF.columnBelow = VPGF.columnBelow or 1         -- also inspect z-1 .. z+columnAbove
VPGF.columnAbove = VPGF.columnAbove or 2         -- roofs usually sit 1-2 levels above the player
VPGF.hoverInterval = VPGF.hoverInterval or 20    -- ticks between hover checks

local luaDebug = false
local pinned = nil
local hoverOn = false
local hoverTick = 0
local lastHoverKey = nil
local reported = false
local messageText = nil
local messageUntil = 0
local overlayOn = false

local function log(msg)
    print(VPGF.PREFIX .. tostring(msg))
end

local function try(fn, ...)
    local ok, res = pcall(fn, ...)
    if ok then return res end
    return nil
end

-------------------------------------------------------------------------------
-- On-screen feedback (console.txt is not visible in game)
-------------------------------------------------------------------------------

local function nowMs()
    return (getTimestampMs and try(getTimestampMs)) or (os and os.time and os.time() * 1000) or 0
end

local function keyName(action)
    for _, b in ipairs(VPGF.bindings) do
        if b.action == action then
            local code = getCore and try(function() return getCore():getKey(b.name) end) or b.default
            local n = Keyboard and Keyboard.getKeyName and try(Keyboard.getKeyName, code)
            return n or tostring(code)
        end
    end
    return "?"
end

local updateOverlay

local function drawOverlay()
    local tm = getTextManager and getTextManager()
    if not tm then return end
    local y = 40
    local function line(text, r, g, b)
        try(function() tm:DrawString(UIFont.Small, 21, y + 1, text, 0, 0, 0, 1) end)
        try(function() tm:DrawString(UIFont.Small, 20, y, text, r, g, b, 1) end)
        y = y + 18
    end
    if VPGF.isDebug() then
        line("VPGeometryFix DEBUG  |  " .. keyName("inspect") .. ": untersuchen  " .. keyName("hover")
            .. ": hover  " .. keyName("inventory") .. ": Inventar  " .. keyName("toggleDebug") .. ": aus", 1, 0.85, 0.2)
        if not VPGF.java() then line("Java-Teil NICHT geladen (ZombieBuddy?) - nur Lua-Zusammenfassung", 1, 0.3, 0.3) end
    end
    if messageText then
        if nowMs() < messageUntil then
            line(messageText, 0.4, 1, 0.4)
        else
            messageText = nil
            updateOverlay()
        end
    end
end

updateOverlay = function()
    local want = VPGF.isDebug() or messageText ~= nil
    if want == overlayOn or not (Events and Events.OnPostUIDraw) then return end
    overlayOn = want
    if want then Events.OnPostUIDraw.Add(drawOverlay) else Events.OnPostUIDraw.Remove(drawOverlay) end
end

-- Shows a message on screen for a few seconds and in console.txt.
function VPGF.notify(text, seconds)
    print(VPGF.PREFIX .. tostring(text))
    messageText = "VPGeometryFix: " .. tostring(text)
    messageUntil = nowMs() + (seconds or 5) * 1000
    updateOverlay()
end

-- True when the Java part (ZombieBuddy-loaded JAR) registered its globals.
function VPGF.java()
    return VPGF_javaAvailable ~= nil and try(VPGF_javaAvailable) == true
end

function VPGF.isDebug()
    if VPGF.java() then return try(VPGF_isDebug) == true end
    return luaDebug
end

function VPGF.setDebug(on)
    luaDebug = on and true or false
    if VPGF.java() then
        try(VPGF_setDebug, luaDebug)
    else
        log("Debug mode: " .. (luaDebug and "ON" or "OFF") .. " (runtime toggle, Lua only)")
    end
    VPGF.notify("Debug " .. (luaDebug and "AN" or "AUS"), 3)
    if not luaDebug then VPGF.setHover(false) end
end

-- Mod ids that look like Viewpoint, excluding this mod and known add-ons.
local function viewpointModIds()
    local found = {}
    local mods = getActivatedMods and try(getActivatedMods)
    if not mods then return found end
    for i = 0, mods:size() - 1 do
        local id = tostring(mods:get(i))
        local plain = id:gsub("^\\", "")
        local lower = string.lower(plain)
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

function VPGF.startupReport()
    local ids = viewpointModIds()
    local idList = table.concat(ids, ",")
    if VPGF.java() then
        try(VPGF_startupReport, luaPzVersion(), #ids > 0, idList)
        return
    end
    -- Fallback: Java part missing (ZombieBuddy absent, JAR blocked or failed).
    local zb = "no"
    if type(ZombieBuddy) == "table" or type(ZombieBuddy) == "userdata" then
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

local function floor(v) return math.floor(tonumber(v) or 0) end

local function facingTarget(player)
    local fd = try(function() return player:getForwardDirection() end)
    local fx, fy = 0, 0
    if fd then
        fx = tonumber(try(function() return fd:getX() end)) or 0
        fy = tonumber(try(function() return fd:getY() end)) or 0
    end
    local d = VPGF.facingDistance
    return floor(player:getX() + fx * d), floor(player:getY() + fy * d), floor(player:getZ()), "facing+" .. d
end

local function mouseTarget(player)
    local z = floor(player:getZ())
    local mx = getMouseXScaled and try(getMouseXScaled) or (getMouseX and try(getMouseX))
    local my = getMouseYScaled and try(getMouseYScaled) or (getMouseY and try(getMouseY))
    if not mx or not my then return nil end
    if ISCoordConversion and ISCoordConversion.ToWorld then
        local ok, wx, wy = pcall(ISCoordConversion.ToWorld, mx, my, z)
        if ok and wx and wy then return floor(wx), floor(wy), z, "mouse(ISCoordConversion)" end
    end
    if screenToIsoX and screenToIsoY then
        local pn = try(function() return player:getPlayerNum() end) or 0
        local wx = try(screenToIsoX, pn, mx, my, z)
        local wy = try(screenToIsoY, pn, mx, my, z)
        if wx and wy then return floor(wx), floor(wy), z, "mouse(screenToIso)" end
    end
    return nil
end

-- Returns x, y, z, source. Priority: pinned > Viewpoint pick (unknown, TODO) > mouse (iso only) > facing.
function VPGF.resolveTarget()
    if pinned then return pinned.x, pinned.y, pinned.z, "pinned" end
    local player = getPlayer and getPlayer()
    if not player then return nil end
    local fp = VPGF.java() and try(VPGF_viewpointFirstPerson) or "unknown"
    if fp == "false" then
        local x, y, z, src = mouseTarget(player)
        if x then return x, y, z, src end
    end
    return facingTarget(player)
end

function VPGF.setTarget(x, y, z)
    pinned = { x = floor(x), y = floor(y), z = floor(z) }
    log("target pinned at " .. pinned.x .. "," .. pinned.y .. "," .. pinned.z)
end

function VPGF.clearTarget()
    pinned = nil
    log("target unpinned")
end

-------------------------------------------------------------------------------
-- Inspection
-------------------------------------------------------------------------------

-- Lua-only one-line summary of a square (used by hover mode and as fallback).
local function luaSquareSummary(sq)
    if not sq then return "no square" end
    local parts = {}
    local objs = try(function() return sq:getObjects() end)
    if objs then
        for i = 0, objs:size() - 1 do
            local o = objs:get(i)
            local spr = try(function() return o:getSprite():getName() end)
            local name = try(function() return o:getObjectName() end)
            table.insert(parts, "[" .. i .. "] " .. tostring(name) .. ":" .. tostring(spr))
        end
    end
    return (#parts > 0) and table.concat(parts, " ") or "(no objects)"
end

function VPGF.inspectAt(x, y, z, source)
    if not VPGF.isDebug() then
        VPGF.notify("Debug ist AUS - zuerst " .. keyName("toggleDebug") .. " druecken")
        return
    end
    local cell = getCell and getCell()
    if not cell then log("inspect: no cell (not in game)") return end
    x, y, z = floor(x), floor(y), floor(z)
    source = source or "manual"
    log(string.format("inspect target %d,%d,%d source=%s", x, y, z, source))
    local useJava = VPGF.java()
    if useJava then try(VPGF_reportBegin, source, x, y, z) end
    for dz = -VPGF.columnBelow, VPGF.columnAbove do
        local sq = cell:getGridSquare(x, y, z + dz)
        local label = "z" .. (dz >= 0 and "+" or "") .. dz
        if useJava then
            try(VPGF_reportSquare, sq, label)
        else
            log("inspect " .. label .. " " .. x .. "," .. y .. "," .. (z + dz) .. ": " .. luaSquareSummary(sq))
        end
    end
    if useJava then
        local path = try(VPGF_reportEnd)
        if path then
            VPGF.notify(string.format("Tile %d,%d,%d untersucht (%s) -> %s", x, y, z, source, tostring(path)), 8)
        else
            VPGF.notify("Bericht konnte nicht geschrieben werden - siehe console.txt", 8)
        end
    else
        VPGF.notify(string.format("Tile %d,%d,%d untersucht (nur Lua) - Ergebnis in console.txt", x, y, z), 8)
    end
end

function VPGF.inspect()
    local x, y, z, src = VPGF.resolveTarget()
    if not x then VPGF.notify("kein Ziel (kein Spieler?)") return end
    VPGF.inspectAt(x, y, z, src)
end

function VPGF.inventory(which)
    if not VPGF.java() then VPGF.notify("Inventar braucht den Java-Teil (ZombieBuddy)") return end
    local path = try(VPGF_inventory, which or "viewpoint")
    VPGF.notify(path and ("Inventar -> " .. tostring(path)) or "Inventar fehlgeschlagen (Viewpoint gefunden?) - siehe console.txt", 8)
    return path
end

function VPGF.viewpointState()
    if not VPGF.java() then log("viewpointState needs the Java part (ZombieBuddy)") return end
    return try(VPGF_viewpointState)
end

function VPGF.dumpStatics(className)
    if not VPGF.java() then log("dumpStatics needs the Java part (ZombieBuddy)") return end
    try(VPGF_dumpStatics, className)
end

-------------------------------------------------------------------------------
-- Hover mode: console summary whenever the target square changes.
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
    for dz = 0, VPGF.columnAbove do
        log("hover " .. src .. " " .. x .. "," .. y .. "," .. (z + dz) .. ": "
            .. luaSquareSummary(cell:getGridSquare(x, y, z + dz)))
    end
    messageText = "VPGeometryFix hover " .. src .. " " .. key .. ": " .. luaSquareSummary(cell:getGridSquare(x, y, z))
    messageUntil = nowMs() + 3000
    updateOverlay()
end

function VPGF.setHover(on)
    if on == hoverOn then return end
    hoverOn = on
    lastHoverKey = nil
    if on then
        Events.OnTick.Add(onHoverTick)
    else
        Events.OnTick.Remove(onHoverTick)
    end
    VPGF.notify("Hover " .. (on and "AN" or "AUS"), 3)
end

-------------------------------------------------------------------------------
-- Events
-------------------------------------------------------------------------------

-- Maps a pressed key code to an action using the live bindings from Options.
local function actionFor(key)
    for _, b in ipairs(VPGF.bindings) do
        local code = getCore and try(function() return getCore():getKey(b.name) end)
        if code == nil or code == 0 then code = b.default end
        if key == code then return b.action end
    end
    return nil
end

local function onKeyPressed(key)
    local action = actionFor(key)
    if not action then return end
    if action == "toggleDebug" then
        VPGF.setDebug(not VPGF.isDebug())
        return
    end
    if not VPGF.isDebug() then
        VPGF.notify("Debug ist AUS - zuerst " .. keyName("toggleDebug") .. " druecken")
        return
    end
    if action == "inspect" then
        VPGF.inspect()
    elseif action == "hover" then
        VPGF.setHover(not hoverOn)
    elseif action == "inventory" then
        VPGF.inventory("viewpoint")
    end
end

local function onGameBoot()
    if reported then return end
    reported = true
    VPGF.startupReport()
end

local function onGameStart()
    -- Fallback if OnGameBoot ran before this file was loaded (mod enabled per save).
    onGameBoot()
    VPGF.notify("aktiv" .. (VPGF.java() and "" or " (OHNE Java-Teil)") .. " - Debug: " .. keyName("toggleDebug")
        .. "  (Tasten: Optionen > Tastenbelegung > [VPGeometryFix])", 10)
    if not VPGF.isDebug() then return end
    log("session start; viewpoint state: " .. tostring(VPGF.java() and try(VPGF_viewpointState) or "n/a"))
end

Events.OnGameBoot.Add(onGameBoot)
Events.OnGameStart.Add(onGameStart)
Events.OnKeyPressed.Add(onKeyPressed)
