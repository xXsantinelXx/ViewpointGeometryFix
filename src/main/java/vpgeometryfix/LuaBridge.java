package vpgeometryfix;

import java.nio.file.Path;
import java.util.List;

import se.krka.kahlua.integration.annotations.LuaMethod;
import vpgeometryfix.diag.ClassInventory;
import vpgeometryfix.diag.Config;
import vpgeometryfix.diag.Env;
import vpgeometryfix.diag.KnownBinaries;
import vpgeometryfix.diag.Log;
import vpgeometryfix.diag.Reflect;
import vpgeometryfix.diag.SquareInspector;
import vpgeometryfix.diag.ViewpointProbe;

/**
 * Global Lua functions. ZombieBuddy registers every public static method
 * annotated {@code @LuaMethod(global = true)} of classes located directly in
 * the {@code javaPkgName} package (this class must stay in package
 * {@code vpgeometryfix}, not a sub-package).
 *
 * Numbers arrive from Lua as doubles. Every entry point catches Throwable so
 * a diagnostic failure can never break the game.
 */
public final class LuaBridge {
    private LuaBridge() {}

    @LuaMethod(name = "VPGF_javaAvailable", global = true)
    public static boolean javaAvailable() {
        return true;
    }

    /**
     * Prints the startup block. Lua passes the facts only Lua knows
     * (activated mod ids).
     *
     * @param luaPzVersion      getCore():getVersion() as seen from Lua, or ""
     * @param luaViewpointModOn true if a Viewpoint mod id is in getActivatedMods()
     * @param luaModIds         matching mod ids found by Lua, for the log
     */
    @LuaMethod(name = "VPGF_startupReport", global = true)
    public static void startupReport(String luaPzVersion, boolean luaViewpointModOn, String luaModIds) {
        try {
            Env.Info i = Env.detect(false);
            String pz = i.pzVersion;
            if ("unknown".equals(pz) && luaPzVersion != null && !luaPzVersion.isBlank()) pz = luaPzVersion + " (from Lua)";
            String gameId = i.gameJarSha == null ? "" : " [projectzomboid.jar: " + KnownBinaries.identify(i.gameJarSha) + "]";

            String vp;
            if (i.vpClassesFound) {
                vp = "yes (version: " + Env.viewpointVersion(i) + "; mod id=" + i.vpModId + "; activated in mod list="
                        + luaViewpointModOn + ")";
            } else if (luaViewpointModOn) {
                vp = "mod enabled (" + luaModIds + ") but Viewpoint Java classes not found - Viewpoint Java part not loaded";
            } else {
                vp = "no";
            }
            String zb = i.zbDetected
                    ? "yes (version " + i.zbVersion + ", javaagent=" + i.zbJavaAgent + ", jar: " + KnownBinaries.identify(i.zbJarSha) + ")"
                    : "no";

            Log.info("Loaded");
            Log.info("PZ version: " + pz + gameId);
            Log.info("Viewpoint detected: " + vp);
            Log.info("ZombieBuddy detected: " + zb);
            Log.info("Debug mode: " + (Config.isDebug() ? "ON" : "OFF") + " (source: " + Config.source() + ")");
            if (Config.isDebug()) {
                Log.info("[debug] pz version source: " + i.pzVersionSource + ", game jar: " + i.gameJar);
                Log.info("[debug] game jar sha256: " + i.gameJarSha);
                Log.info("[debug] viewpoint jar: " + i.vpJar + " (probe class " + i.vpProbeClass + ")");
                Log.info("[debug] viewpoint jar sha256: " + i.vpJarSha);
                Log.info("[debug] zombiebuddy jar: " + i.zbJar + " sha256: " + i.zbJarSha);
                Log.info("[debug] java: " + System.getProperty("java.version") + " / " + System.getProperty("java.vm.name"));
                for (String n : i.notes) Log.info("[debug] note: " + n);
            }
        } catch (Throwable t) {
            Log.error("startupReport failed", t);
        }
    }

    @LuaMethod(name = "VPGF_isDebug", global = true)
    public static boolean isDebug() {
        return Config.isDebug();
    }

    @LuaMethod(name = "VPGF_setDebug", global = true)
    public static void setDebug(boolean on) {
        Config.setDebug(on, "runtime toggle");
        Log.info("Debug mode: " + (on ? "ON" : "OFF") + " (runtime toggle)");
    }

    /** Viewpoint first-person flag: "true", "false" or "unknown". Reads Viewpoint state on demand. */
    @LuaMethod(name = "VPGF_viewpointFirstPerson", global = true)
    public static String viewpointFirstPerson() {
        try {
            Boolean b = ViewpointProbe.firstPersonActive();
            return b == null ? "unknown" : b.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    @LuaMethod(name = "VPGF_viewpointState", global = true)
    public static String viewpointState() {
        try {
            String s = ViewpointProbe.stateLine();
            Log.info("viewpoint state: " + s);
            return s;
        } catch (Throwable t) {
            Log.error("viewpointState failed", t);
            return "error";
        }
    }

    /** Debug helper: list static primitive fields of one Viewpoint (or any) class. */
    @LuaMethod(name = "VPGF_dumpStatics", global = true)
    public static void dumpStatics(String className) {
        try {
            List<String> lines = ViewpointProbe.staticPrimitives(className);
            for (String l : lines) Log.info("statics " + className + ": " + l);
        } catch (Throwable t) {
            Log.error("dumpStatics failed", t);
        }
    }

    @LuaMethod(name = "VPGF_reportBegin", global = true)
    public static void reportBegin(String reason, double x, double y, double z) {
        try {
            SquareInspector.begin(reason, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        } catch (Throwable t) {
            Log.error("reportBegin failed", t);
        }
    }

    @LuaMethod(name = "VPGF_reportSquare", global = true)
    public static void reportSquare(Object square, String label) {
        try {
            SquareInspector.addSquare(square, label);
        } catch (Throwable t) {
            Log.error("reportSquare failed", t);
        }
    }

    @LuaMethod(name = "VPGF_reportObject", global = true)
    public static void reportObject(Object obj, String label) {
        try {
            SquareInspector.addObject(obj, label);
        } catch (Throwable t) {
            Log.error("reportObject failed", t);
        }
    }

    @LuaMethod(name = "VPGF_reportEnd", global = true)
    public static String reportEnd() {
        try {
            return SquareInspector.end();
        } catch (Throwable t) {
            Log.error("reportEnd failed", t);
            return null;
        }
    }

    /** which = "viewpoint" (Viewpoint jar, package viewpoint.) or "game" (projectzomboid.jar, zombie.iso.). */
    @LuaMethod(name = "VPGF_inventory", global = true)
    public static String inventory(String which) {
        try {
            Env.Info i = Env.detect(false);
            Path out;
            if ("game".equals(which)) {
                out = ClassInventory.write(i.gameJar, "zombie.iso.", "game_zombie_iso");
            } else {
                if (!i.vpClassesFound) {
                    Log.info("inventory: Viewpoint not detected");
                    return null;
                }
                String prefix = i.vpProbeClass.substring(0, i.vpProbeClass.indexOf('.') + 1);
                out = ClassInventory.write(i.vpJar, prefix, "viewpoint");
            }
            return out == null ? null : out.toString();
        } catch (Throwable t) {
            Log.error("inventory failed", t);
            return null;
        }
    }

    /** Is the given class resolvable (without initializing it)? */
    @LuaMethod(name = "VPGF_hasClass", global = true)
    public static boolean hasClass(String name) {
        return Reflect.find(name) != null;
    }
}
