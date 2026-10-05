package vpgeometryfix.diag;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Debug configuration. Sources, later ones win:
 * <ol>
 *   <li>default: debug off</li>
 *   <li>{@code <Zomboid>/VPGeometryFix/config.properties}: {@code debug=true}</li>
 *   <li>JVM property {@code -Dvpgf.debug=true}</li>
 *   <li>runtime toggle (hotkey / Lua {@code VPGF.setDebug})</li>
 * </ol>
 * Nothing is ever written to game settings or save games.
 */
public final class Config {
    private static volatile boolean debug;
    private static volatile String source = "default";
    private static final Properties props = new Properties();

    private Config() {}

    public static synchronized void load() {
        debug = false;
        source = "default";
        props.clear();
        Path file = Paths.outputDir().resolve("config.properties");
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
                if (props.containsKey("debug")) {
                    debug = Boolean.parseBoolean(props.getProperty("debug").trim());
                    source = "config.properties";
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.error("cannot read " + file, e);
            }
        }
        String sys = System.getProperty("vpgf.debug");
        if (sys != null) {
            debug = Boolean.parseBoolean(sys.trim());
            source = "-Dvpgf.debug";
        }
    }

    public static boolean isDebug() {
        return debug;
    }

    public static String source() {
        return source;
    }

    public static void setDebug(boolean on, String why) {
        debug = on;
        source = why;
    }

    public static boolean getBool(String key, boolean def) {
        String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    /** Sets a key and writes config.properties (only our own folder). */
    public static synchronized void put(String key, String value) {
        props.setProperty(key, value);
        Path file = Paths.outputDir().resolve("config.properties");
        try {
            Files.createDirectories(file.getParent());
            try (java.io.Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(w, "VPGeometryFix");
            }
        } catch (IOException | RuntimeException e) {
            Log.error("cannot write " + file, e);
        }
    }

    public static String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null ? def : v.trim();
    }
}
