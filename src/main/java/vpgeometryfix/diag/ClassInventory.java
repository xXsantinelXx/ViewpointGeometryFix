package vpgeometryfix.diag;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Local class inventory of a JAR: every class name plus field/method
 * SIGNATURES (no bytecode, no decompilation) of classes whose name matches
 * geometry/render keywords. Classes are loaded without initialization.
 *
 * Output stays on the user's machine ({@code <Zomboid>/VPGeometryFix/inventory/}).
 * It describes third-party/proprietary binaries: do not publish it.
 */
public final class ClassInventory {
    public static final String[] KEYWORDS = {"render", "cull", "visib", "mesh", "vertex", "model", "roof", "wall",
            "tile", "sprite", "chunk", "room", "floor", "shell", "far", "pick", "building", "geometry", "cutaway",
            "batch", "atlas", "texture", "shader", "scene", "world", "occlu", "stair", "fbo", "depth"};

    private ClassInventory() {}

    public static boolean matches(String className) {
        String simple = className.substring(className.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String pkg = className.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) if (simple.contains(k) || pkg.contains("." + k)) return true;
        return false;
    }

    /**
     * @param jar        JAR to list
     * @param pkgPrefix  only classes whose name starts with this (e.g. "viewpoint." or "zombie.iso.")
     * @param tag        output file tag
     */
    public static Path write(Path jar, String pkgPrefix, String tag) {
        if (jar == null || !Files.isRegularFile(jar)) {
            Log.info("inventory " + tag + ": jar not found");
            return null;
        }
        List<String> all = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                String n = e.nextElement().getName();
                if (!n.endsWith(".class") || n.contains("module-info")) continue;
                String cn = n.substring(0, n.length() - 6).replace('/', '.');
                if (cn.startsWith(pkgPrefix)) all.add(cn);
            }
        } catch (IOException e) {
            Log.error("inventory " + tag + ": cannot open " + jar, e);
            return null;
        }
        all.sort(null);
        StringBuilder sb = new StringBuilder();
        sb.append("# VPGeometryFix class inventory (LOCAL ONLY - do not publish)\n")
                .append("# jar: ").append(jar).append('\n')
                .append("# sha256: ").append(KnownBinaries.sha256(jar)).append('\n')
                .append("# prefix: ").append(pkgPrefix).append(", classes: ").append(all.size()).append("\n\n## all classes\n");
        for (String cn : all) sb.append(cn).append('\n');
        sb.append("\n## members of keyword-matching classes\n");
        int matched = 0;
        for (String cn : all) {
            if (!matches(cn)) continue;
            matched++;
            sb.append('\n').append(cn).append('\n');
            Class<?> c = Reflect.find(cn);
            if (c == null) {
                sb.append("  <not loadable>\n");
                continue;
            }
            try {
                if (c.getSuperclass() != null) sb.append("  extends ").append(c.getSuperclass().getName()).append('\n');
                for (Field f : c.getDeclaredFields()) {
                    sb.append("  F ").append(Modifier.toString(f.getModifiers())).append(' ')
                            .append(f.getGenericType().getTypeName()).append(' ').append(f.getName()).append('\n');
                }
                for (Method m : c.getDeclaredMethods()) {
                    sb.append("  M ").append(Modifier.toString(m.getModifiers())).append(' ')
                            .append(m.getReturnType().getTypeName()).append(' ').append(m.getName()).append('(');
                    Class<?>[] p = m.getParameterTypes();
                    for (int i = 0; i < p.length; i++) sb.append(i > 0 ? ", " : "").append(p[i].getTypeName());
                    sb.append(")\n");
                }
            } catch (Throwable t) {
                sb.append("  <members unreadable: ").append(t).append(">\n");
            }
        }
        Path dir = Paths.outputDir().resolve("inventory");
        Path out = dir.resolve("inventory_" + tag + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.error("inventory " + tag + ": cannot write", e);
            return null;
        }
        Log.info("inventory " + tag + ": " + all.size() + " classes, " + matched + " keyword matches -> " + out);
        return out;
    }
}
