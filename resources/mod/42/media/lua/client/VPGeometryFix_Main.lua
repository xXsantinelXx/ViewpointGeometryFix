--[[
    Viewpoint Geometry Fix - diagnostic build (client only).

    Does NOT change rendering, gameplay, save games, Viewpoint or game files.
    All heavy work runs only on explicit user action (hotkeys / console calls).
    Normal play cost: one OnKeyPressed check per key press. The optional
    "hover" mode adds a throttled OnTick handler only while it is switched on.

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
VPGF.VERSION = "0.1.0-diag"
VPGF.PREFIX = "[VPGeometryFix] "

-- Hotkeys: always Ctrl+Shift+<key>. Keyboard.KEY_* constants are exposed by the game.
VPGF.keys = VPGF.keys or {
    toggleDebug = Keyboard.KEY_F9,
    inspect = Keyboard.KEY_F10,
    hover = Keyboard.KEY_F11,
    inventory = Keyboard.KEY_F8,
}
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

local function log(msg)
    print(VPGF.PREFIX .. tostring(msg))
end

local function try(fn, ...)
    local ok, res = pcall(fn, ...)
    if ok then return res end
    return nil
end

-- True when the Java part (ZombieBuddy-loaded JAR) registered its globals.
function VPGF.java()
    return type(VPGF_javaAvailable) == "function" and try(VPGF_javaAvailable) == true
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
    local player = getPlayer and getPlayer()
    if player and HaloTextHelper then
        try(HaloTextHelper.addText, player, "VPGeometryFix debug " .. (luaDebug and "ON" or "OFF"))
    end
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
        log("inspect ignored: debug mode is OFF (Ctrl+Shift+F9)")
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
        local player = getPlayer and getPlayer()
        if player and HaloTextHelper and path then
            try(HaloTextHelper.addText, player, "VPGeometryFix: report written")
        end
    end
end

function VPGF.inspect()
    local x, y, z, src = VPGF.resolveTarget()
    if not x then log("inspect: no target (no player?)") return end
    VPGF.inspectAt(x, y, z, src)
end

function VPGF.inventory(which)
    if not VPGF.java() then log("inventory needs the Java part (ZombieBuddy)") return end
    return try(VPGF_inventory, which or "viewpoint")
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
    log("hover mode " .. (on and "ON" or "OFF"))
end

-------------------------------------------------------------------------------
-- Events
-------------------------------------------------------------------------------

local function modifiersDown()
    local ctrl = isCtrlKeyDown and try(isCtrlKeyDown)
    local shift = isShiftKeyDown and try(isShiftKeyDown)
    return ctrl and shift
end

local function onKeyPressed(key)
    local k = VPGF.keys
    if key ~= k.toggleDebug and key ~= k.inspect and key ~= k.hover and key ~= k.inventory then return end
    if not modifiersDown() then return end
    if key == k.toggleDebug then
        VPGF.setDebug(not VPGF.isDebug())
        if not VPGF.isDebug() then VPGF.setHover(false) end
        return
    end
    if not VPGF.isDebug() then
        log("debug mode is OFF - press Ctrl+Shift+F9 first")
        return
    end
    if key == k.inspect then
        VPGF.inspect()
    elseif key == k.hover then
        VPGF.setHover(not hoverOn)
    elseif key == k.inventory then
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
    if not VPGF.isDebug() then return end
    log("session start; viewpoint state: " .. tostring(VPGF.java() and try(VPGF_viewpointState) or "n/a"))
end

Events.OnGameBoot.Add(onGameBoot)
Events.OnGameStart.Add(onGameStart)
Events.OnKeyPressed.Add(onKeyPressed)
