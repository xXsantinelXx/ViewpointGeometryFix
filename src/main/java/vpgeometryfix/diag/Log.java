package vpgeometryfix.diag;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Single logging point. Every console line starts with the unique prefix
 * {@value #PREFIX} so it can be grepped from console.txt.
 *
 * Lines are also appended to {@code <Zomboid>/VPGeometryFix/VPGeometryFix.log}.
 * File errors never propagate into the game.
 */
public final class Log {
    public static final String PREFIX = "[VPGeometryFix] ";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile boolean fileBroken;
    private static volatile boolean sessionStarted;

    private Log() {}

    public static void info(String msg) {
        String line = PREFIX + msg;
        System.out.println(line);
        toFile(line);
    }

    /** Only printed while debug mode is on. */
    public static void debug(String msg) {
        if (Config.isDebug()) info("[debug] " + msg);
    }

    public static void error(String msg, Throwable t) {
        info("ERROR " + msg + (t == null ? "" : ": " + t));
    }

    private static synchronized void toFile(String line) {
        if (fileBroken) return;
        try {
            Path dir = Paths.outputDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("VPGeometryFix.log");
            StandardOpenOption mode = sessionStarted ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
            sessionStarted = true;
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode)) {
                w.write(LocalDateTime.now().format(TS));
                w.write(' ');
                w.write(line);
                w.write(System.lineSeparator());
            }
        } catch (IOException | RuntimeException e) {
            fileBroken = true;
            System.out.println(PREFIX + "file logging disabled: " + e);
        }
    }
}
