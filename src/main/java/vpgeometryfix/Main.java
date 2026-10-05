package vpgeometryfix;

import vpgeometryfix.diag.Config;
import vpgeometryfix.diag.Log;
import vpgeometryfix.fix.RoofFallback;
import vpgeometryfix.fix.RoofMirror;
import vpgeometryfix.fix.RoofShapes;

/**
 * ZombieBuddy entry point: ZombieBuddy invokes {@code <javaPkgName>.Main.main}
 * once when it loads this mod's JAR (verified in ZombieBuddy v2.3.2 and v3
 * Loader sources).
 *
 * Deliberately minimal: reads the config, no threads. The roof fix patches
 * (Patch_* classes in this package) are applied by ZombieBuddy itself. The
 * startup report is printed later from Lua (OnGameBoot), after all mods -
 * including Viewpoint - have been loaded.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            Config.load();
            // Roof fix variant B: on by default in this test build, config key roofFixB=false disables it.
            RoofFallback.setEnabled(Config.getBool("roofFixB", true));
            // Roof fix parts (approved 2026-10-05), all under the master switch roofFixB / panel "Dach-Fix".
            RoofFallback.setParts(Config.getBool("roofFixMirror", true), Config.getBool("roofFixClip", true),
                    Config.getBool("roofFixTrim", true));
            RoofMirror.setEnabled(Config.getBool("roofFixMesh", true));
            String map = RoofShapes.setBackMap(Config.get("roofBackMap", null));
            RoofShapes.setBackMode(Config.get("roofBackMode", "same"));
            Log.fileOnly("Java component loaded via ZombieBuddy (debug=" + Config.isDebug() + ", source=" + Config.source()
                    + ", roofFixB=" + RoofFallback.isEnabled() + ", mirror/clip/trim/mesh="
                    + Config.getBool("roofFixMirror", true) + "/" + Config.getBool("roofFixClip", true) + "/"
                    + Config.getBool("roofFixTrim", true) + "/" + RoofMirror.isEnabled() + ", backMap=" + map
                    + ", backMode=" + (RoofShapes.isMirrorMode() ? "mirror" : "same") + ")");
        } catch (Throwable t) {
            System.out.println(Log.PREFIX + "ERROR Java init failed: " + t);
        }
    }
}
