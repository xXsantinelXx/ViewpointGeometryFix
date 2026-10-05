package vpgeometryfix;

import vpgeometryfix.diag.Config;
import vpgeometryfix.diag.Log;
import vpgeometryfix.fix.RoofFallback;

/**
 * ZombieBuddy entry point: ZombieBuddy invokes {@code <javaPkgName>.Main.main}
 * once when it loads this mod's JAR (verified in ZombieBuddy v2.3.2 and v3
 * Loader sources).
 *
 * Deliberately minimal: no bytecode patches, no render hooks, no threads.
 * The startup report is printed later from Lua (OnGameBoot), after all mods
 * - including Viewpoint - have been loaded.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        try {
            Config.load();
            // Roof fix variant B: on by default in this test build, config key roofFixB=false disables it.
            RoofFallback.setEnabled(Config.getBool("roofFixB", true));
            Log.fileOnly("Java component loaded via ZombieBuddy (debug=" + Config.isDebug() + ", source=" + Config.source()
                    + ", roofFixB=" + RoofFallback.isEnabled() + ")");
        } catch (Throwable t) {
            System.out.println(Log.PREFIX + "ERROR Java init failed: " + t);
        }
    }
}
