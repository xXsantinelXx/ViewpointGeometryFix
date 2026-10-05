package vpgeometryfix.diag;

import java.nio.file.Path;

/** Resolves the output directory without hard dependencies on game classes. */
public final class Paths {
    private static volatile Path override;
    private static volatile Path cached;

    private Paths() {}

    /** For tests only. */
    public static void overrideOutputDir(Path dir) {
        override = dir;
        cached = null;
    }

    /**
     * {@code <Zomboid user dir>/VPGeometryFix}. The user dir comes from
     * {@code zombie.ZomboidFileSystem.instance.getCacheDir()} when available
     * (looked up reflectively), otherwise {@code ~/Zomboid}.
     */
    public static Path outputDir() {
        Path o = override;
        if (o != null) return o;
        Path c = cached;
        if (c != null) return c;
        String base = null;
        Object fs = Reflect.staticField("zombie.ZomboidFileSystem", "instance");
        if (fs != null) {
            Object dir = Reflect.call(fs, "getCacheDir");
            if (dir != null) base = dir.toString();
        }
        if (base == null || base.isBlank()) base = System.getProperty("user.home") + "/Zomboid";
        c = Path.of(base, "VPGeometryFix");
        cached = c;
        return c;
    }
}
