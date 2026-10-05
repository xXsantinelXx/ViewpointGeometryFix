package vpgeometryfix.diag;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * On-demand, read-only look at how Viewpoint turns tile shapes into meshes.
 * No patch, no per-frame work: runs only when the user presses "Dach-Daten".
 *
 * Names are [V1] from the signatures of the user's Viewpoint 0.1.5a-hotfix JAR
 * (VPGF Doctor): {@code viewpoint.world.MeshBuilder} (static TO_ISO_CAMERA,
 * UV_INSET, ON_FLOOR, CYLINDER_SEGMENTS, static float frameX/frameY(Vector3f)),
 * {@code viewpoint.world.TileMesh} (static STRIDE, EMPTY; float[] data,
 * int vertCount, long hash), {@code viewpoint.world.TileMeshes} (static
 * IdentityHashMap cache with values TileMeshes$Held{mesh, used}).
 * Their meaning is [H]: frameX/frameY are assumed to project a 3D point into the
 * sprite frame (texture coordinates), the first three floats of each vertex are
 * assumed to be its position. This class only reads; it never calls a method
 * that builds or caches meshes (get/create).
 */
public final class MeshProbe {
    static final String TILE_MESHES = "viewpoint.world.TileMeshes";
    static final String TILE_MESH = "viewpoint.world.TileMesh";
    static final String MESH_BUILDER = "viewpoint.world.MeshBuilder";
    static final String WORLD_MESHER = "viewpoint.world.WorldMesher";
    static final String VECTOR3F = "org.joml.Vector3f";
    static final int CACHE_MAX = 300;

    private MeshProbe() {}

    /** Static values that fix units and the projection, one line per class. */
    public static List<String> statics() {
        List<String> out = new ArrayList<>();
        out.add(line(TILE_MESHES, "GAME", "SQUARE_PIXELS", "UNIT_PIXELS", "FOOT_PIXELS", "WALL_ART_W", "WALL_ART_N"));
        out.add(line(MESH_BUILDER, "TO_ISO_CAMERA", "UV_INSET", "ON_FLOOR", "CYLINDER_SEGMENTS"));
        out.add(line(TILE_MESH, "STRIDE"));
        out.add(line(WORLD_MESHER, "STOREY_PIXELS", "lift"));
        return out;
    }

    private static String line(String cls, String... fields) {
        StringBuilder sb = new StringBuilder(cls.substring(cls.lastIndexOf('.') + 1)).append(':');
        Class<?> c = Reflect.find(cls);
        if (c == null) return sb.append(" class not found").toString();
        for (String f : fields) sb.append(' ').append(f).append('=').append(fmt(Reflect.staticField(cls, f)));
        return sb.toString();
    }

    /**
     * Calls MeshBuilder.frameX/frameY on a few unit points. If the texture comes
     * from an isometric projection of the sprite, x and z move the point in
     * opposite screen-x directions and y moves it straight up [H].
     */
    public static List<String> frameProbe() {
        List<String> out = new ArrayList<>();
        Class<?> mb = Reflect.find(MESH_BUILDER);
        Class<?> v3 = Reflect.find(VECTOR3F);
        if (mb == null || v3 == null) {
            out.add("frameX/frameY: " + (mb == null ? "MeshBuilder" : "org.joml.Vector3f") + " not found");
            return out;
        }
        Method fx = staticOneArg(mb, "frameX", v3);
        Method fy = staticOneArg(mb, "frameY", v3);
        if (fx == null || fy == null) {
            out.add("frameX/frameY: methods not found");
            return out;
        }
        float[][] pts = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0.5f, 0, 0.5f}, {-0.5f, 0, -0.5f}};
        for (float[] p : pts) {
            try {
                Object v = vector(v3, p[0], p[1], p[2]);
                Object x = fx.invoke(null, v);
                Object y = fy.invoke(null, vector(v3, p[0], p[1], p[2]));
                out.add(String.format(java.util.Locale.ROOT, "frame(%.2f,%.2f,%.2f) = x %s, y %s", p[0], p[1], p[2], fmt(x), fmt(y)));
            } catch (Throwable t) {
                out.add("frame probe failed: " + t);
                break;
            }
        }
        return out;
    }

    static Object vector(Class<?> v3, float x, float y, float z) throws ReflectiveOperationException {
        Constructor<?> k = v3.getConstructor(float.class, float.class, float.class);
        return k.newInstance(x, y, z);
    }

    static Method staticOneArg(Class<?> c, String name, Class<?> arg) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1 && Modifier.isStatic(m.getModifiers())
                    && m.getParameterTypes()[0].isAssignableFrom(arg)) {
                try {
                    m.setAccessible(true);
                    return m;
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Snapshot of Viewpoint's tile mesh cache for keys whose name starts with
     * {@code prefix}: vertex count, EMPTY or not, and the position bounds
     * (assuming floats 0..2 of each vertex are x, y, z [H]).
     */
    public static List<String> cacheSnapshot(String prefix) {
        List<String> out = new ArrayList<>();
        Object cache = Reflect.staticField(TILE_MESHES, "cache");
        if (!(cache instanceof Map<?, ?> map)) {
            out.add("mesh cache: not readable (" + (cache == null ? "null" : cache.getClass().getName()) + ")");
            return out;
        }
        List<Map.Entry<?, ?>> entries = copy(map);
        if (entries == null) {
            out.add("mesh cache: changed while reading, try again");
            return out;
        }
        Object empty = Reflect.staticField(TILE_MESH, "EMPTY");
        Object strideObj = Reflect.staticField(TILE_MESH, "STRIDE");
        int stride = strideObj instanceof Number n ? n.intValue() : 0;
        String keyClass = null;
        String valueClass = null;
        int matched = 0;
        int emptyCount = 0;
        for (Map.Entry<?, ?> e : entries) {
            Object key = e.getKey();
            Object value = e.getValue();
            if (keyClass == null && key != null) keyClass = key.getClass().getName();
            if (valueClass == null && value != null) valueClass = value.getClass().getName();
            Object nameObj = Reflect.call(key, "getName");
            String name = nameObj == null ? null : nameObj.toString();
            if (name == null || (prefix != null && !name.startsWith(prefix))) continue;
            matched++;
            Object mesh = value != null && value.getClass().getName().endsWith("$Held") ? Reflect.field(value, "mesh") : value;
            boolean isEmpty = mesh == null || mesh == empty;
            if (isEmpty) emptyCount++;
            if (matched <= CACHE_MAX) out.add(name + " - " + describeMesh(mesh, isEmpty, stride));
        }
        out.add(0, "mesh cache: " + entries.size() + " entries, key " + keyClass + ", value " + valueClass + ", "
                + matched + " with prefix '" + prefix + "', of these EMPTY/null " + emptyCount
                + (matched > CACHE_MAX ? ", listed " + CACHE_MAX : ""));
        return out;
    }

    static List<Map.Entry<?, ?>> copy(Map<?, ?> map) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                List<Map.Entry<?, ?>> list = new ArrayList<>();
                for (Map.Entry<?, ?> e : map.entrySet()) list.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()));
                return list;
            } catch (ConcurrentModificationException | ArrayIndexOutOfBoundsException e) {
                // a mesh was built meanwhile; retry once
            }
        }
        return null;
    }

    static String describeMesh(Object mesh, boolean isEmpty, int stride) {
        if (mesh == null) return "null";
        Object vc = Reflect.field(mesh, "vertCount");
        Object data = Reflect.field(mesh, "data");
        StringBuilder sb = new StringBuilder();
        if (isEmpty) sb.append("EMPTY ");
        sb.append("verts=").append(fmt(vc));
        if (data instanceof float[] d) {
            sb.append(" floats=").append(d.length);
            // only the vertices in use: data may be larger than vertCount * STRIDE [H]
            int used = d.length;
            if (vc instanceof Number n && n.intValue() >= 0 && (long) n.intValue() * stride <= d.length) used = n.intValue() * stride;
            if (vc instanceof Number n && stride > 0 && (long) n.intValue() * stride != d.length) sb.append(" (verts*STRIDE=")
                    .append((long) n.intValue() * stride).append(')');
            if (stride >= 3 && used >= stride) {
                float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
                float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int i = 0; i + 2 < used; i += stride) {
                    for (int k = 0; k < 3; k++) {
                        min[k] = Math.min(min[k], d[i + k]);
                        max[k] = Math.max(max[k], d[i + k]);
                    }
                }
                sb.append(String.format(java.util.Locale.ROOT, " bounds~(%.3f %.3f %.3f)..(%.3f %.3f %.3f)",
                        min[0], min[1], min[2], max[0], max[1], max[2]));
            }
        }
        return sb.toString();
    }

    /** Short text for numbers, strings and small vector-like objects (public float x/y/z). */
    static String fmt(Object v) {
        if (v == null) return "?";
        if (v instanceof Float f) return String.format(java.util.Locale.ROOT, "%.4f", f);
        if (v instanceof Number || v instanceof String || v instanceof Boolean) return v.toString();
        Object x = fieldValue(v, "x");
        Object y = fieldValue(v, "y");
        Object z = fieldValue(v, "z");
        if (x instanceof Float && y instanceof Float && z instanceof Float) return "(" + fmt(x) + " " + fmt(y) + " " + fmt(z) + ")";
        String s = v.toString().replaceAll("\\s+", " ");
        return s.length() > 80 ? s.substring(0, 80) + ".." : s;
    }

    private static Object fieldValue(Object o, String name) {
        try {
            Field f = Reflect.findField(o.getClass(), name);
            if (f == null || Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }
}
