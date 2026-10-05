package pzmod.vpgeometryfix;

/**
 * Every line this mod writes carries the same prefix, so a user can grep
 * console.txt for "[VPGeometryFix]" and get our output and nothing else.
 *
 * Debug lines are gated: with debug off the mod writes only its startup
 * block, which is what keeps a normal session free of per-frame logging.
 */
public final class Log {

    public static final String PREFIX = "[VPGeometryFix]";

    private Log() {
    }

    public static void line(String message) {
        System.out.println(PREFIX + " " + message);
    }

    /** Written only while debug mode is on. */
    public static void debug(String message) {
        if (Environment.isDebug()) {
            System.out.println(PREFIX + " [debug] " + message);
        }
    }

    public static void error(String message, Throwable cause) {
        System.out.println(PREFIX + " ERROR " + message
                + (cause == null ? "" : ": " + cause));
    }
}
