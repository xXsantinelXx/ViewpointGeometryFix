-- Startup log and the probe keys.
--
-- Nothing in here runs per frame or per tick: OnGameStart fires once, the
-- key handler only when the key is pressed. With debug mode off the probe
-- key does nothing but say so.

require "VPGeometryFix_Options"

local PROBE_BINDING = "VPGeometryFix Probe Tile"
local DEBUG_BINDING = "VPGeometryFix Toggle Debug"

-- Viewpoint's mod id as its own dependants declare it (require=Viewpoint).
-- The other spellings are fallbacks for differently packaged copies.
local VIEWPOINT_IDS = { "Viewpoint", "ProjectViewpoint", "projectviewpoint" }

local function halo(text)
    local player = getPlayer()
    if HaloTextHelper and player then
        HaloTextHelper.addText(player, text)
    end
    print("[VPGeometryFix] " .. text)
end

-- The mod list is the only place the Viewpoint version string exists; Java
-- cannot read it before the game is loaded, so Lua hands it over.
local function viewpointModVersion()
    local function versionOf(id)
        local info = getModInfoByID(id)
        if not info then return nil end
        local version = info:getModVersion()
        if version == nil or version == "" then
            return "installed, no modversion in mod.info"
        end
        return tostring(version)
    end
    for i = 1, #VIEWPOINT_IDS do
        local version = versionOf(VIEWPOINT_IDS[i])
        if version then
            return VIEWPOINT_IDS[i] .. " " .. version
        end
    end
    -- Not under a known id: report any active mod whose id mentions viewpoint
    -- rather than claiming it is absent.
    local active = getActivatedMods()
    if active then
        for i = 0, active:size() - 1 do
            local id = tostring(active:get(i))
            if string.find(string.lower(id), "viewpoint", 1, true) then
                local version = versionOf(id)
                return id .. " " .. (version or "unknown")
            end
        end
    end
    return "unknown (no mod id mentioning viewpoint is active)"
end

local function onGameStart()
    if not VPGFix.javaReady then
        print("[VPGeometryFix] Loaded (Lua only; the Java part is not loaded)")
        print("[VPGeometryFix] Viewpoint detected: " .. viewpointModVersion())
        return
    end
    VPGFix.callJava("setViewpointModVersion", viewpointModVersion())
    -- Carry the saved option into Java before the startup block is printed,
    -- so the "Debug mode:" line states the value this session will run with.
    local debugOpt = VPGFix.debugOpt
    if debugOpt then
        VPGFix.callJava("setDebug", debugOpt:getValue() and true or false)
    end
    VPGFix.callJava("startupReport")
end

-- Three squares are read per probe, each labelled, because under Viewpoint's
-- first-person camera the cursor square and the square the player faces are
-- not the same square, and which of them matches what is on screen is
-- exactly what we are trying to find out.
local function probeTargets()
    local player = getPlayer()
    if not player then
        print("[VPGeometryFix] no player; nothing to probe")
        return
    end
    local px = math.floor(player:getX())
    local py = math.floor(player:getY())
    local pz = math.floor(player:getZ())

    print("[VPGeometryFix] probe target: player square")
    VPGFix.callJava("probeColumn", px, py, pz, pz + 1)

    local direction = player:getForwardDirection()
    if direction then
        local fx = math.floor(player:getX() + direction:getX() * 1.5)
        local fy = math.floor(player:getY() + direction:getY() * 1.5)
        print("[VPGeometryFix] probe target: square the player faces")
        VPGFix.callJava("probeColumn", fx, fy, pz, pz + 1)
    end

    -- Vanilla screen-to-world mapping. Under Viewpoint's own camera this can
    -- point somewhere else than the cursor appears to; the report says which
    -- square was read, so a mismatch is visible instead of silent.
    local mx = getMouseX()
    local my = getMouseY()
    local cx = screenToIsoX(player, mx, my, pz)
    local cy = screenToIsoY(player, mx, my, pz)
    if cx and cy then
        print("[VPGeometryFix] probe target: cursor square via vanilla screenToIso")
        VPGFix.callJava("probeColumn", math.floor(cx), math.floor(cy), pz, pz + 1)
    end
end

local function onKeyPressed(key)
    local core = getCore()
    if key == core:getKey(DEBUG_BINDING) then
        local debugOpt = VPGFix.debugOpt
        local value = not (VPGFix.callJava("isDebug") == true)
        VPGFix.callJava("setDebug", value)
        if debugOpt then
            debugOpt:setValue(value)
            -- setValue only touches the in-memory option; save so the state
            -- survives a restart like a change from the options screen.
            PZAPI.ModOptions:save()
        end
        halo("Debug mode: " .. (value and "on" or "off"))
        return
    end
    if key ~= core:getKey(PROBE_BINDING) then return end
    if not VPGFix.javaReady then
        halo("The Java part of this mod is not loaded")
        return
    end
    if VPGFix.callJava("isDebug") ~= true then
        halo("Debug mode is off; turn it on to probe tiles")
        return
    end
    probeTargets()
    halo("Tile report written; see console.txt and the VPGeometryFix cache folder")
end

Events.OnGameStart.Add(onGameStart)
Events.OnKeyPressed.Add(onKeyPressed)
