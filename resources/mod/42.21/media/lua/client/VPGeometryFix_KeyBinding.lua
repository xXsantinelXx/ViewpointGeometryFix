-- Registers this mod's keys under "[VPGeometryFix]" in Options -> Keybinds.
-- Both keys are rebindable there; the handlers read the live key code, so a
-- rebind takes effect without a restart.
--
-- F10 and F11 are free in vanilla B42.21, in Viewpoint (Delete, O, Shift+O,
-- Insert+O) and in PeekAView (F8). Rebind if another mod claims them.

if keyBinding then
    table.insert(keyBinding, { value = "[VPGeometryFix]" })
    table.insert(keyBinding, { value = "VPGeometryFix Probe Tile", key = Keyboard.KEY_F10 })
    table.insert(keyBinding, { value = "VPGeometryFix Toggle Debug", key = Keyboard.KEY_F11 })
end
