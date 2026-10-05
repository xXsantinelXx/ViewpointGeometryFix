package pzmod.vpgeometryfix;

import me.zed_0xff.zombie_buddy.Exposer;

/**
 * The Lua-facing surface, reachable as the global {@code VPGeometryFix}
 * once ZombieBuddy has exposed this class.
 *
 * Everything here is a read or a flag flip. There is deliberately no
 * method that changes rendering: this release is diagnosis only.
 */
@Exposer.LuaClass
public class Api {

    /** Bumped together with mod.info's modversion. */
    public static final String VERSION = "0.1.0";

    private static volatile String lastReport = "";

    /** The startup block, printed once per session by the Lua side. */
    public static String startupReport() {
        String[] lines = {
                "Loaded",
                "PZ version: " + Environment.gameVersion(),
                "Viewpoint detected: " + Environment.viewpointSummary(),
                "ZombieBuddy detected: " + Environment.zombieBuddySummary(),
                "Debug mode: " + (Environment.isDebug() ? "on" : "off"),
        };
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            Log.line(line);
            joined.append(Log.PREFIX).append(' ').append(line).append('\n');
        }
        return joined.toString();
    }

    /** One line for an options screen or a halo message. */
    public static String status() {
        return "VPGeometryFix " + VERSION
                + " | Viewpoint: " + Environment.viewpointSummary()
                + " | debug: " + (Environment.isDebug() ? "on" : "off");
    }

    public static void setDebug(boolean value) {
        boolean previous = Environment.isDebug();
        Environment.setDebug(value);
        if (previous != value) {
            Log.line("Debug mode: " + (value ? "on" : "off"));
        }
    }

    public static boolean isDebug() {
        return Environment.isDebug();
    }

    /**
     * The Viewpoint version as the mod list reports it. Lua reads it from
     * the mod info; Java has no access to that list before the game is
     * loaded.
     */
    public static void setViewpointModVersion(String value) {
        Environment.setViewpointModVersion(value);
        Log.debug("Viewpoint mod version from the mod list: " + value);
    }

    public static String viewpointClasses() {
        return String.join(", ", Environment.viewpointClassesFound());
    }

    /** Identifies projectzomboid.jar by hash; a few seconds on first call. */
    public static String identifyGameJar() {
        return Environment.identifyJarOf("zombie/core/Core.class");
    }

    /**
     * The diagnostic for one square. Logs the report, writes it to the
     * report folder and returns it, so Lua can show it as well.
     */
    public static String probe(int x, int y, int z) {
        String report = header("probe " + x + "," + y + "," + z)
                + TileProbe.describeSquare(x, y, z) + "\n";
        return deliver("probe", report);
    }

    /**
     * Every level of one map column, which is how a missing roof is
     * usually caught: the tile exists on z+1 while the view shows nothing.
     */
    public static String probeColumn(int x, int y, int zFrom, int zTo) {
        int from = Math.min(zFrom, zTo);
        int to = Math.max(zFrom, zTo);
        StringBuilder report = new StringBuilder(
                header("column " + x + "," + y + " z " + from + ".." + to));
        for (int z = from; z <= to; z++) {
            report.append(TileProbe.describeSquare(x, y, z)).append('\n');
        }
        return deliver("column", report.toString());
    }

    public static String lastReport() {
        return lastReport;
    }

    private static String header(String what) {
        return Log.PREFIX + " " + what + "\n"
                + "  mod=" + VERSION
                + " pz=" + Environment.gameVersion()
                + " zb=" + Environment.zombieBuddyVersion() + "\n"
                + "  viewpoint=" + Environment.viewpointSummary() + "\n";
    }

    private static String deliver(String name, String report) {
        lastReport = report;
        System.out.print(report);
        String file = Reports.write(name, report);
        Log.line("report written to " + file);
        return report;
    }
}
