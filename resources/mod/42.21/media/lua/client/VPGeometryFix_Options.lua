-- Options -> Mods -> Viewpoint Geometry Fix.
--
-- State lives in Java (Environment.debug); this file is only the UI that
-- writes to it. With debug off the mod does nothing beyond its startup log.

VPGFix = VPGFix or {}

-- Kahlua global registered by ZombieBuddy from @Exposer.LuaClass on Api.
-- nil only when the user declined the JAR in ZombieBuddy's startup prompt.
local Java = VPGeometryFix
VPGFix.javaReady = Java ~= nil
VPGFix.lastJavaError = nil

local function callJava(name, ...)
    if not VPGFix.javaReady then return nil end
    local fn = Java[name]
    if not fn then
        VPGFix.javaReady = false
        VPGFix.lastJavaError = "missing Java method: " .. name
        print("[VPGeometryFix] Java method missing: " .. name)
        return nil
    end
    local ok, result = pcall(fn, ...)
    if not ok then
        VPGFix.javaReady = false
        VPGFix.lastJavaError = tostring(result)
        print("[VPGeometryFix] Java call " .. name .. " failed: " .. tostring(result))
        return nil
    end
    return result
end

VPGFix.callJava = callJava

local options = PZAPI.ModOptions:create("VPGeometryFix", "Viewpoint Geometry Fix")

-- addTitle is not in every PZAPI build; addDescription always is.
local function section(text)
    if options.addTitle then
        options:addTitle(text)
    else
        options:addDescription(text)
    end
end

if not VPGFix.javaReady then
    options:addDescription("The Java part of this mod is not loaded. Enable ZombieBuddy and allow the VPGeometryFix JAR at startup.")
end

section("Diagnostics")

local debugOpt = options:addTickBox(
    "debug",
    "Debug mode",
    false,
    "Off: the mod only writes its startup lines. On: the probe key collects tile and object data and writes a report.")
debugOpt.onChangeApply = function(self, value)
    callJava("setDebug", value and true or false)
end
VPGFix.debugOpt = debugOpt

section("Probe a fixed coordinate")

local xOpt = options:addTextEntry("targetX", "Target X", "", "Map X coordinate.")
local yOpt = options:addTextEntry("targetY", "Target Y", "", "Map Y coordinate.")
local zOpt = options:addTextEntry("targetZ", "Target Z", "0", "Floor level, 0 is ground.")
VPGFix.targetOpts = { x = xOpt, y = yOpt, z = zOpt }

-- The later geometry work needs a way to inspect one known-broken tile over
-- and over. These three fields plus the button are that way in: type the
-- coordinate once, probe it again after every change.
options:addButton(
    "probeTarget",
    "Probe this coordinate",
    "Writes a report for the coordinate above, whether or not it is on screen.",
    function()
        local x = tonumber(xOpt:getValue())
        local y = tonumber(yOpt:getValue())
        local z = tonumber(zOpt:getValue()) or 0
        if not x or not y then
            print("[VPGeometryFix] Target X and Y must be numbers")
            return
        end
        callJava("probe", math.floor(x), math.floor(y), math.floor(z))
    end)
