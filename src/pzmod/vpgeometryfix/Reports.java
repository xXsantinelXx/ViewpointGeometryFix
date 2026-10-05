package pzmod.vpgeometryfix;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Probe reports as files under the Zomboid cache directory, so a user can
 * attach one to a bug report instead of scraping console.txt.
 *
 * Only this mod's own folder is ever written. Saves, game files, Viewpoint
 * files and ZombieBuddy files are never touched.
 */
public final class Reports {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String FOLDER = "VPGeometryFix";

    private Reports() {
    }

    /** Our report folder inside the Zomboid cache directory, or null. */
    static Path folder() {
        try {
            String cache = zombie.ZomboidFileSystem.instance.getCacheDir();
            if (cache == null || cache.isEmpty()) {
                return null;
            }
            return Paths.get(cache, FOLDER);
        } catch (Throwable t) {
            Log.debug("the cache directory is unreadable: " + t);
            return null;
        }
    }

    /**
     * Writes one report and returns the path as a string, or a reason why
     * nothing was written. Never throws into the caller's frame.
     */
    public static String write(String name, String body) {
        Path folder = folder();
        if (folder == null) {
            return "not written (cache directory unknown)";
        }
        try {
            Files.createDirectories(folder);
            Path file = folder.resolve(
                    name + "-" + LocalDateTime.now().format(STAMP) + ".txt");
            Files.write(file, body.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return file.toString();
        } catch (Throwable t) {
            Log.error("writing the report failed", t);
            return "not written (" + t.getClass().getSimpleName() + ")";
        }
    }
}
