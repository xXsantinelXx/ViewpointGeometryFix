package vpgeometryfix;

import java.nio.file.Path;
import java.util.List;

import se.krka.kahlua.integration.annotations.LuaMethod;
import vpgeometryfix.diag.ClassInventory;
import vpgeometryfix.diag.Config;
import vpgeometryfix.diag.Env;
import vpgeometryfix.diag.GeometrySource;
import vpgeometryfix.diag.KnownBinaries;
import vpgeometryfix.diag.Log;
import vpgeometryfix.diag.MeshProbe;
import vpgeometryfix.diag.Reflect;
import vpgeometryfix.diag.SquareInspector;
import vpgeometryfix.diag.ViewpointProbe;
import vpgeometryfix.fix.RoofFallback;
import vpgeometryfix.fix.RoofMirror;

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
            // Details go to VPGeometryFix.log only, so console.txt stays short.
            Log.fileOnly("detail pz version source: " + i.pzVersionSource + ", game jar: " + i.gameJar + " sha256=" + i.gameJarSha);
            Log.fileOnly("detail viewpoint jar: " + i.vpJar + " (probe " + i.vpProbeClass + ") sha256=" + i.vpJarSha);
            Log.fileOnly("detail zombiebuddy jar: " + i.zbJar + " sha256=" + i.zbJarSha);
            Log.fileOnly("detail java: " + System.getProperty("java.version") + " / " + System.getProperty("java.vm.name"));
            for (String n : i.notes) Log.fileOnly("detail note: " + n);
        } catch (Throwable t) {
            Log.error("startupReport failed", t);
        }
    }

    /** Short status lines for the in-game panel, separated by newlines. No logging. */
    @LuaMethod(name = "VPGF_status", global = true)
    public static String status() {
        try {
            Env.Info i = Env.detect(false);
            String vp = i.vpClassesFound
                    ? "erkannt" + (i.vpModVersion != null ? " " + i.vpModVersion : "")
                        + (i.vpJarSha != null && KnownBinaries.PINS.containsKey(i.vpJarSha) ? " (auditierter Build)" : "")
                    : "nicht gefunden";
            return "Java-Teil: OK\n"
                    + "PZ: " + i.pzVersion + "\n"
                    + "Viewpoint: " + vp + "\n"
                    + "ZombieBuddy: " + (i.zbDetected ? i.zbVersion : "nicht gefunden") + "\n"
                    + roofFixStatus();
        } catch (Throwable t) {
            return "Java-Teil: Fehler " + t.getClass().getSimpleName();
        }
    }

    /** Two status lines for the roof fix variants. */
    @LuaMethod(name = "VPGF_roofFixStatus", global = true)
    public static String roofFixStatus() {
        try {
            String b = "Dach-Fix (Code): " + (RoofFallback.isEnabled() ? "AN" : "AUS")
                    + (RoofFallback.calls() > 0 ? ", Patch aktiv (" + RoofFallback.calls() + " Aufrufe)\n"
                        + "  Daecher: mit Form " + RoofFallback.roofShaped() + ", ohne Form " + RoofFallback.roofEmpty()
                        + ", ersetzt " + RoofFallback.replaced() + "\n"
                        + "  Rueckseiten " + RoofFallback.mirrored() + " (Mesh " + RoofMirror.built() + ", Bild "
                        + RoofMirror.swapped() + "), Platten " + RoofFallback.clipped() + ", Leisten " + RoofFallback.trimmed()
                        : ", Patch noch nicht aufgerufen");
            Path data = roofDataFile();
            String a = "Dach-Fix A (Datei): " + (data != null && java.nio.file.Files.isRegularFile(data)
                    ? "vorhanden" : "nicht installiert");
            return b + "\n" + a;
        } catch (Throwable t) {
            return "Dach-Fix: Status unbekannt";
        }
    }

    /** Switches variant B for this session and stores it for the next start. */
    @LuaMethod(name = "VPGF_setRoofFix", global = true)
    public static void setRoofFix(boolean on) {
        try {
            RoofFallback.setEnabled(on);
            Config.put("roofFixB", Boolean.toString(on));
            RoofFallback.stats();
            Log.info("roof fix: " + (on ? "ON" : "OFF") + " (affects newly built areas; restart for a full effect)");
        } catch (Throwable t) {
            Log.error("setRoofFix failed", t);
        }
    }

    @LuaMethod(name = "VPGF_isRoofFix", global = true)
    public static boolean isRoofFix() {
        return RoofFallback.isEnabled();
    }

    /** {@code <mod>/42/media/tileGeometry.txt} next to our JAR (variant A, written by VPGF-RoofData). */
    static Path roofDataFile() {
        Path jar = Reflect.codeSource(LuaBridge.class);
        if (jar == null) return null;
        Path media = jar.getParent() == null ? null : jar.getParent().getParent();
        media = media == null ? null : media.getParent();
        return media == null ? null : media.resolve("tileGeometry.txt");
    }

    /** TILE lines of the last inspection, newline separated. */
    @LuaMethod(name = "VPGF_lastSummary", global = true)
    public static String lastSummary() {
        try {
            return SquareInspector.lastSummary();
        } catch (Throwable t) {
            return "";
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
            if (RoofFallback.calls() > 0) RoofFallback.stats();
            return SquareInspector.end();
        } catch (Throwable t) {
            Log.error("reportEnd failed", t);
            return null;
        }
    }

    /**
     * "Dach-Daten": one on-demand report about Viewpoint's roof meshes - static
     * units, the texture projection probe, the mesh cache for roofs_* and the
     * source of every roof shape seen so far. Returns a short status for the panel.
     */
    @LuaMethod(name = "VPGF_roofData", global = true)
    public static String roofData() {
        try {
            StringBuilder sb = new StringBuilder("VPGeometryFix roof data ").append(java.time.LocalDateTime.now()).append('\n');
            sb.append("Viewpoint: ").append(Env.viewpointVersion(Env.detect(false))).append('\n');
            sb.append("fix B: ").append(RoofFallback.isEnabled() ? "ON" : "OFF").append(", geometryFor calls ")
                    .append(RoofFallback.calls()).append(", roofs with shape ").append(RoofFallback.roofShaped())
                    .append(", without ").append(RoofFallback.roofEmpty()).append(", replaced ").append(RoofFallback.replaced()).append('\n');
            sb.append("\n--- statics\n");
            for (String l : MeshProbe.statics()) sb.append(l).append('\n');
            sb.append("\n--- texture projection probe (MeshBuilder.frameX/frameY)\n");
            for (String l : MeshProbe.frameProbe()) sb.append(l).append('\n');
            List<String> sources = GeometrySource.report();
            sb.append("\n--- shape sources\n");
            for (String l : sources) sb.append(l).append('\n');
            List<String> cache = MeshProbe.cacheSnapshot("roofs_");
            sb.append("\n--- mesh cache (roofs_*)\n");
            for (String l : cache) sb.append(l).append('\n');
            Path dir = vpgeometryfix.diag.Paths.outputDir().resolve("inspect");
            java.nio.file.Files.createDirectories(dir);
            Path file = dir.resolve("roofdata_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
            java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            String head = (sources.size() > 1 ? sources.get(1) : "sources: ?") + "; " + (cache.isEmpty() ? "" : cache.get(0));
            Log.info("roof data: " + head);
            Log.info("report -> " + file);
            return head + "\n" + file;
        } catch (Throwable t) {
            Log.error("roofData failed", t);
            return "Dach-Daten fehlgeschlagen: " + t;
        }
    }

    /** which = "viewpoint", "game" (zombie.iso.) or a game package prefix such as "zombie.tileDepth.". */
    @LuaMethod(name = "VPGF_inventory", global = true)
    public static String inventory(String which) {
        try {
            Env.Info i = Env.detect(false);
            Path out;
            if ("game".equals(which)) {
                out = ClassInventory.write(i.gameJar, "zombie.iso.", "game_zombie_iso");
            } else if (which != null && which.startsWith("zombie.")) {
                // any game package, e.g. "zombie.tileDepth." (source of Viewpoint's tile shapes)
                out = ClassInventory.write(i.gameJar, which, "game_" + which.replace('.', '_'));
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
