package vpgeometryfix.diag;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Read-only reflective field dumper.
 *
 * Reads fields only (never calls getters, so no side effects), with a depth
 * limit, an identity-based cycle guard and an output line cap. Nested objects
 * are expanded only when their class name starts with one of the configured
 * prefixes; everything else is printed as {@code Type@identityHash}.
 */
public final class ObjectDumper {
    private final StringBuilder out = new StringBuilder();
    private final Map<Object, Boolean> seen = new IdentityHashMap<>();
    private final String[] expandPrefixes;
    private final int maxDepth;
    private final int maxLines;
    private final int maxElements;
    private int lines;

    public ObjectDumper(String[] expandPrefixes, int maxDepth, int maxLines, int maxElements) {
        this.expandPrefixes = expandPrefixes;
        this.maxDepth = maxDepth;
        this.maxLines = maxLines;
        this.maxElements = maxElements;
    }

    public String result() {
        return out.toString();
    }

    public boolean truncated() {
        return lines >= maxLines;
    }

    public void line(String indent, String text) {
        if (lines >= maxLines) return;
        lines++;
        out.append(indent).append(text).append('\n');
        if (lines == maxLines) out.append(indent).append("... output truncated (maxLines=").append(maxLines).append(")\n");
    }

    /** Dumps all non-static fields of {@code obj} including inherited ones. */
    public void dump(String indent, Object obj, int depth) {
        if (obj == null) {
            line(indent, "null");
            return;
        }
        if (seen.put(obj, Boolean.TRUE) != null) {
            line(indent, "(already dumped) " + ref(obj));
            return;
        }
        line(indent, "class " + hierarchy(obj.getClass()));
        for (Class<?> k = obj.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            Field[] fields;
            try {
                fields = k.getDeclaredFields();
            } catch (Throwable t) {
                line(indent, "  <fields of " + k.getName() + " unreadable: " + t + ">");
                continue;
            }
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(obj);
                } catch (Throwable t) {
                    line(indent, "  " + k.getSimpleName() + "." + f.getName() + " = <inaccessible>");
                    continue;
                }
                String label = "  " + k.getSimpleName() + "." + f.getName() + " : " + f.getType().getSimpleName() + " = ";
                if (v != null && depth < maxDepth && shouldExpand(v.getClass())) {
                    line(indent, label + ref(v));
                    dump(indent + "    ", v, depth + 1);
                } else {
                    line(indent, label + value(v, depth));
                }
                if (lines >= maxLines) return;
            }
        }
    }

    public boolean shouldExpand(Class<?> c) {
        if (c.isArray() || c.isEnum() || c.isPrimitive()) return false;
        String n = c.getName();
        for (String p : expandPrefixes) if (n.startsWith(p)) return true;
        return false;
    }

    /** One-line rendering of a value. */
    public String value(Object v, int depth) {
        if (v == null) return "null";
        Class<?> c = v.getClass();
        if (v instanceof String) return "\"" + v + "\"";
        if (v instanceof Number || v instanceof Boolean || v instanceof Character) return v.toString();
        if (c.isEnum()) return c.getSimpleName() + "." + ((Enum<?>) v).name();
        if (c.isArray()) {
            int len = Array.getLength(v);
            StringBuilder sb = new StringBuilder(c.getComponentType().getSimpleName()).append('[').append(len).append("] {");
            for (int i = 0; i < Math.min(len, maxElements); i++) {
                if (i > 0) sb.append(", ");
                sb.append(shallow(Array.get(v, i)));
            }
            if (len > maxElements) sb.append(", ...");
            return sb.append('}').toString();
        }
        if (v instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder(c.getSimpleName()).append("(size=").append(col.size()).append(") {");
            int i = 0;
            try {
                for (Object e : col) {
                    if (i == maxElements) {
                        sb.append(", ...");
                        break;
                    }
                    if (i++ > 0) sb.append(", ");
                    sb.append(shallow(e));
                }
            } catch (RuntimeException ex) {
                sb.append("<iteration failed: ").append(ex.getClass().getSimpleName()).append('>');
            }
            return sb.append('}').toString();
        }
        if (v instanceof Map<?, ?> m) return c.getSimpleName() + "(size=" + m.size() + ")";
        return ref(v);
    }

    private String shallow(Object e) {
        if (e == null) return "null";
        if (e instanceof String || e instanceof Number || e instanceof Boolean || e instanceof Character || e.getClass().isEnum()) {
            return value(e, maxDepth);
        }
        return ref(e);
    }

    public static String ref(Object o) {
        return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }

    public static String hierarchy(Class<?> c) {
        StringBuilder sb = new StringBuilder(c.getName());
        for (Class<?> k = c.getSuperclass(); k != null && k != Object.class; k = k.getSuperclass()) {
            sb.append(" < ").append(k.getName());
        }
        return sb.toString();
    }
}
