package vpgeometryfix.diag;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects read-only information about IsoGridSquares / IsoObjects handed in
 * from Lua and writes one report file per inspection to
 * {@code <Zomboid>/VPGeometryFix/inspect/}.
 *
 * Game objects are only READ: fields via reflection, plus a short whitelist of
 * accessor methods that return existing data ({@link #SAFE_GETTERS}).
 * Runs only on explicit user action (hotkey / Lua call), never per frame.
 */
public final class SquareInspector {
    /** Square accessors we call; each just returns a coordinate or an internal list. */
    static final String[] SAFE_GETTERS = {"getObjects", "getSpecialObjects", "getMovingObjects",
            "getStaticMovingObjects", "getWorldObjects"};
    static final String[] EXPAND = {"zombie.iso.sprite.", "zombie.core.properties.", "zombie.core.textures.Texture",
            "zombie.iso.SpriteDetails", "zombie.iso.areas.", "zombie.iso.RoomDef", "zombie.iso.BuildingDef"};

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private static StringBuilder report;
    private static final List<String> summary = new ArrayList<>();
    private static String fileTag;

    private SquareInspector() {}

    public static synchronized void begin(String reason, int x, int y, int z) {
        report = new StringBuilder();
        summary.clear();
        fileTag = LocalDateTime.now().format(FILE_TS) + "_" + x + "_" + y + "_" + z;
        Env.Info env = Env.detect(false);
        report.append("VPGeometryFix inspection report\n")
                .append("time: ").append(LocalDateTime.now()).append('\n')
                .append("reason: ").append(reason).append('\n')
                .append("target: ").append(x).append(',').append(y).append(',').append(z).append('\n')
                .append("pz: ").append(env.pzVersion).append('\n')
                .append("viewpoint: ").append(env.vpClassesFound ? Env.viewpointVersion(env) : "not detected").append('\n')
                .append("viewpoint state: ").append(ViewpointProbe.stateLine()).append('\n')
                .append("note: sprite-name classification is heuristic; see docs/DIAGNOSTICS.md\n\n");
    }

    public static synchronized void addSquare(Object square, String label) {
        if (report == null) begin("implicit", -1, -1, -1);
        if (square == null) {
            report.append("=== ").append(label).append(": square is null (not loaded / out of range)\n\n");
            summary.add(label + ": no square");
            return;
        }
        String coords = Reflect.call(square, "getX") + "," + Reflect.call(square, "getY") + "," + Reflect.call(square, "getZ");
        report.append("=== ").append(label).append(" square ").append(coords).append(" (")
                .append(ObjectDumper.ref(square)).append(")\n");
        StringBuilder sline = new StringBuilder(label + " " + coords + ":");
        for (String getter : SAFE_GETTERS) {
            List<Object> items = asList(Reflect.call(square, getter));
            report.append("--- ").append(getter).append("() count=").append(items == null ? "n/a" : items.size()).append('\n');
            if (items == null) continue;
            for (int i = 0; i < items.size(); i++) {
                Object o = items.get(i);
                String one = describeObject(o);
                report.append("  [").append(i).append("] ").append(one).append('\n');
                if (getter.equals("getObjects") || getter.equals("getSpecialObjects")) sline.append(" [").append(i).append("] ").append(one).append(';');
                ObjectDumper d = new ObjectDumper(EXPAND, 2, 600, 12);
                d.dump("      ", o, 0);
                report.append(d.result());
            }
        }
        report.append("--- square fields\n");
        ObjectDumper d = new ObjectDumper(new String[0], 0, 400, 8);
        d.dump("  ", square, 0);
        report.append(d.result()).append('\n');
        summary.add(sline.toString());
    }

    public static synchronized void addObject(Object obj, String label) {
        if (report == null) begin("implicit", -1, -1, -1);
        report.append("=== ").append(label).append(" object ").append(obj == null ? "null" : describeObject(obj)).append('\n');
        if (obj != null) {
            ObjectDumper d = new ObjectDumper(EXPAND, 2, 800, 12);
            d.dump("  ", obj, 0);
            report.append(d.result()).append('\n');
            summary.add(label + ": " + describeObject(obj));
        }
    }

    /** Writes the report; returns the file path, or null on failure. Logs a short summary. */
    public static synchronized String end() {
        if (report == null) return null;
        Path dir = Paths.outputDir().resolve("inspect");
        Path file = dir.resolve("inspect_" + fileTag + ".txt");
        String result;
        try {
            Files.createDirectories(dir);
            Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
            result = file.toString();
        } catch (IOException | RuntimeException e) {
            Log.error("cannot write inspection report", e);
            result = null;
        }
        for (String s : summary) Log.info("inspect " + s);
        Log.info("inspect report: " + (result == null ? "<not written>" : result));
        report = null;
        return result;
    }

    static String describeObject(Object o) {
        if (o == null) return "null";
        String cls = o.getClass().getSimpleName();
        String sprite = spriteName(o);
        Classifier.Kind kind = Classifier.classify(cls, sprite);
        return cls + " sprite=" + sprite + " kind~" + kind;
    }

    static String spriteName(Object o) {
        Object sprite = Reflect.call(o, "getSprite");
        if (sprite == null) return "-";
        Object name = Reflect.call(sprite, "getName");
        if (name == null) {
            try {
                var f = Reflect.findField(sprite.getClass(), "name");
                if (f != null) {
                    f.setAccessible(true);
                    name = f.get(sprite);
                }
            } catch (Throwable ignored) {
                // stays null
            }
        }
        return name == null ? "?" : name.toString();
    }

    /** Accepts java.util.List or any game list type with size()/get(int) (e.g. PZArrayList). */
    static List<Object> asList(Object list) {
        if (list == null) return null;
        List<Object> out = new ArrayList<>();
        if (list instanceof Iterable<?> it) {
            for (Object o : it) out.add(o);
            return out;
        }
        try {
            Method size = list.getClass().getMethod("size");
            Method get = list.getClass().getMethod("get", int.class);
            int n = (Integer) size.invoke(list);
            for (int i = 0; i < n; i++) out.add(get.invoke(list, i));
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
