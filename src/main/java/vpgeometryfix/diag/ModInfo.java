package vpgeometryfix.diag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal read-only parser for Project Zomboid {@code mod.info} files. */
public final class ModInfo {
    public final Path file;
    public final Map<String, String> values;

    private ModInfo(Path file, Map<String, String> values) {
        this.file = file;
        this.values = values;
    }

    public String get(String key) {
        return values.get(key);
    }

    public static Map<String, String> parse(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            out.putIfAbsent(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
        }
        return out;
    }

    public static ModInfo read(Path file) {
        try {
            return new ModInfo(file, parse(Files.readAllLines(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Walks up from a JAR (e.g. {@code <mod>/42/media/java/client/X.jar}) to the
     * nearest directory containing {@code mod.info} (or {@code common/mod.info},
     * as Viewpoint 0.1.5a-hotfix ships it); at most {@code maxLevels}.
     */
    public static ModInfo findAbove(Path jar, int maxLevels) {
        if (jar == null) return null;
        Path dir = jar.toAbsolutePath().getParent();
        for (int i = 0; i < maxLevels && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("mod.info");
            if (Files.isRegularFile(candidate)) return read(candidate);
            // B42 layout: <mod>/<version>/media/... with mod.info possibly only in <mod>/common
            Path common = dir.resolve("common").resolve("mod.info");
            if (Files.isRegularFile(common)) return read(common);
        }
        return null;
    }
}
