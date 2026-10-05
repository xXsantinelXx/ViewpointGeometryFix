package vpgeometryfix.fix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vpgeometryfix.diag.GeometrySource;
import vpgeometryfix.diag.Reflect;

/**
 * Corrected copies of the game's roof tile shapes for Viewpoint's first-person
 * meshes (approved by the user 2026-10-05, "alles fixen"). The game's own shape
 * objects are never changed: every correction works on a deep copy, so the
 * vanilla isometric renderer keeps its data.
 *
 * Facts [V1, user's logs, game data]: shapes are zombie.tileDepth.TileGeometryFile
 * boxes with Vector3f fields translate / rotate (degrees) / min / max; 1 tile =
 * 1.0 with the origin in the tile centre, y up, one storey = 2.4495; -z = north,
 * -x = west [H from the wall-edge convention]. Steep roofs (39.2 deg):
 * roofs_*_0..2 face +z (rotate X), roofs_*_3..5 face +x (rotate Z); the six
 * tiles roofs_*_8..13 have no shape at all, which is the missing roof half [H:
 * they are the north/west halves, edge-on in the isometric view].
 */
public final class RoofShapes {
    static final String VEC = "org.joml.Vector3f";
    /** Steep roof tilesets only (roofs_01..09, roofs_burnt_01..09); roofs_30_* have shapes for all four sides. */
    static final Pattern ROOF = Pattern.compile("^(roofs_(?:burnt_)?0\\d)_(\\d+)$");

    /** Back tile index -> {front tile index, axis (0 = mirror z, 1 = mirror x)} [H, configurable]. */
    static final int[][] DEFAULT_BACK = {{8, 0, 0}, {9, 1, 0}, {10, 2, 0}, {11, 3, 1}, {12, 4, 1}, {13, 5, 1}};
    /**
     * How the shapes of tiles 8..13 are made. "same" (default since 0.3.1): the
     * partner tile's shapes unchanged. Evidence [V1, user's 0.3.0 log]: their
     * pictures have the same size and place as tiles 0..5 (roofs_01_8 128x131 at
     * y 125 vs roofs_01_0 128x129 at y 127) and the game has snow overlays for them
     * (e_roof_snow_1_41..45 = roofs_01_9..13), so they are visible surfaces with the
     * partner's slope, not edge-on back halves [H]. "mirror" (0.3.0 behaviour) is
     * kept as an option: it put slabs with the opposite slope on the roof.
     */
    private static volatile boolean mirrorMode;

    public static void setBackMode(String mode) {
        mirrorMode = "mirror".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public static boolean isMirrorMode() {
        return mirrorMode;
    }

    /** Explicit map from config (roofBackMap), or null for the automatic per-tileset map. */
    private static volatile int[][] configured;
    private static final Map<String, int[][]> AUTO = new ConcurrentHashMap<>();
    private static final Map<String, String> ALIAS = new ConcurrentHashMap<>();
    /** name -> assigned tile name or null; GeometrySource.assignedTile in game, replaceable in tests. */
    static volatile Function<String, String> assignment = GeometrySource::assignedTile;
    /** name -> vertical centre of the sprite's picture in its frame, or null; replaceable in tests. */
    static volatile Function<String, Float> artCentre = RoofShapes::artCentreOf;

    private RoofShapes() {}

    /** Parses e.g. "8=0z,9=1z,10=2z,11=3x,12=4x,13=5x"; empty = automatic; invalid = automatic. */
    public static String setBackMap(String spec) {
        AUTO.clear();
        configured = null;
        if (spec == null || spec.isBlank()) return "auto";
        try {
            List<int[]> out = new ArrayList<>();
            for (String part : spec.split(",")) {
                String p = part.trim();
                int eq = p.indexOf('=');
                char axis = p.charAt(p.length() - 1);
                if (eq <= 0 || (axis != 'z' && axis != 'x')) throw new IllegalArgumentException(p);
                out.add(new int[] {Integer.parseInt(p.substring(0, eq).trim()),
                    Integer.parseInt(p.substring(eq + 1, p.length() - 1).trim()), axis == 'z' ? 0 : 1});
            }
            configured = out.toArray(new int[0][]);
            return describe(configured);
        } catch (RuntimeException e) {
            return "auto";
        }
    }

    static String describe(int[][] map) {
        StringBuilder sb = new StringBuilder();
        for (int[] b : map) {
            if (sb.length() > 0) sb.append(',');
            sb.append(b[0]).append('=').append(b[1]).append(b[2] == 0 ? 'z' : 'x');
        }
        return sb.toString();
    }

    /**
     * For a back-half sprite name: {frontSpriteName, axis}, else null. Works for
     * every steep roofs_* tileset; tiles the game assigns to a back tile
     * (e.g. roofs_01_67 = roofs_01_11 [V1]) count as that back tile.
     */
    public static Object[] backOf(String spriteName) {
        if (spriteName == null) return null;
        Matcher m = ROOF.matcher(spriteName);
        if (!m.matches()) return null;
        String tileset = m.group(1);
        int[] b = entry(tileset, index(m.group(2)));
        if (b == null) {
            String a = ALIAS.computeIfAbsent(spriteName, n -> {
                String r = null;
                try {
                    r = assignment.apply(n);
                } catch (Throwable ignored) {
                    // no assignment
                }
                return r == null ? "" : r;
            });
            Matcher am = a.isEmpty() ? null : ROOF.matcher(a);
            if (am != null && am.matches()) b = entry(tileset, index(am.group(2)));
        }
        return b == null ? null : new Object[] {tileset + "_" + b[1], b[2]};
    }

    private static int index(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int[] entry(String tileset, int idx) {
        for (int[] b : mapFor(tileset)) if (b[0] == idx) return b;
        return null;
    }

    /** The configured map, or per tileset an automatic one (see {@link #autoMap}). */
    public static int[][] mapFor(String tileset) {
        int[][] c = configured;
        return c != null ? c : AUTO.computeIfAbsent(tileset, RoofShapes::autoMap);
    }

    /**
     * Orders each back group by the height of its picture: an edge-on half is a
     * thin strip in its sprite frame, lower strips belong to lower roof stages
     * [H, geometry of the isometric view]. North group 8..10 -> fronts 0,1,2
     * (bottom, middle, top: translate y 0.398/1.215/2.031 [V1]); west group
     * 11..13 -> fronts 5,4,3 (bottom..top [V1]). Without usable pictures the
     * default map is kept. Logged once per tileset.
     */
    static int[][] autoMap(String tileset) {
        int[][] out = {DEFAULT_BACK[0].clone(), DEFAULT_BACK[1].clone(), DEFAULT_BACK[2].clone(),
            DEFAULT_BACK[3].clone(), DEFAULT_BACK[4].clone(), DEFAULT_BACK[5].clone()};
        String how = order(tileset, new int[] {8, 9, 10}, new int[] {0, 1, 2}, out, 0)
                + "; " + order(tileset, new int[] {11, 12, 13}, new int[] {5, 4, 3}, out, 3);
        vpgeometryfix.diag.Log.fileOnly("roof back map " + tileset + ": " + describe(out) + " (" + how + ")");
        return out;
    }

    private static String order(String tileset, int[] backs, int[] frontsBottomUp, int[][] out, int at) {
        Float[] c = new Float[3];
        for (int i = 0; i < 3; i++) {
            try {
                c[i] = artCentre.apply(tileset + "_" + backs[i]);
            } catch (Throwable t) {
                c[i] = null;
            }
            if (c[i] == null || c[i].isNaN()) return backs[0] + "-" + backs[2] + " default (no picture data)";
        }
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (Math.abs(c[i] - c[j]) < 2f) return backs[0] + "-" + backs[2] + " default (pictures at the same height)";
            }
        }
        Integer[] idx = {0, 1, 2};
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(c[b], c[a])); // largest frame y = lowest = bottom stage
        for (int rank = 0; rank < 3; rank++) {
            int i = idx[rank];
            out[at + i] = new int[] {backs[i], frontsBottomUp[rank], at == 0 ? 0 : 1};
        }
        return backs[0] + "-" + backs[2] + " by picture height " + c[0] + "/" + c[1] + "/" + c[2];
    }

    /** offsetY + height/2 of the sprite's texture (Texture getters [B41 names, B42 U]), or null. */
    static Float artCentreOf(String name) {
        Object tex = RoofMirror.texture(name);
        if (tex == null) return null;
        Object oy = Reflect.call(tex, "getOffsetY");
        Object h = Reflect.call(tex, "getHeight");
        if (!(oy instanceof Number a) || !(h instanceof Number b) || b.floatValue() <= 0) return null;
        return a.floatValue() + b.floatValue() / 2f;
    }

    // ------------------------------------------------------------------ shape copies

    /** Deep copy of a shape (Vector3f and float[] fields copied), or null if the class cannot be instantiated. */
    static Object copy(Object shape) {
        if (shape == null) return null;
        try {
            Constructor<?> k = shape.getClass().getDeclaredConstructor();
            k.setAccessible(true);
            Object c = k.newInstance();
            for (Class<?> t = shape.getClass(); t != null && t != Object.class; t = t.getSuperclass()) {
                for (Field f : t.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object v = f.get(shape);
                    if (v instanceof float[] a) v = a.clone();
                    else if (isVec(v)) v = vec(x(v), y(v), z(v));
                    f.set(c, v);
                }
            }
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean isVec(Object v) {
        return v != null && v.getClass().getName().equals(VEC);
    }

    static Object vec(float x, float y, float z) throws ReflectiveOperationException {
        Class<?> c = Reflect.find(VEC);
        if (c == null) throw new ClassNotFoundException(VEC);
        return c.getConstructor(float.class, float.class, float.class).newInstance(x, y, z);
    }

    static float x(Object v) {
        return comp(v, "x");
    }

    static float y(Object v) {
        return comp(v, "y");
    }

    static float z(Object v) {
        return comp(v, "z");
    }

    private static float comp(Object v, String n) {
        Object o = Reflect.field(v, n);
        return o instanceof Float f ? f : Float.NaN;
    }

    static void set(Object v, float x, float y, float z) throws ReflectiveOperationException {
        for (String n : new String[] {"x", "y", "z"}) {
            Field f = Reflect.findField(v.getClass(), n);
            f.setAccessible(true);
            f.setFloat(v, n.equals("x") ? x : n.equals("y") ? y : z);
        }
    }

    static Object vecField(Object shape, String name) {
        Object v = Reflect.field(shape, name);
        return isVec(v) ? v : null;
    }

    /** True for a Box-like shape: Vector3f translate, rotate, min and max. */
    static boolean isBox(Object s) {
        return s != null && vecField(s, "translate") != null && vecField(s, "rotate") != null
                && vecField(s, "min") != null && vecField(s, "max") != null;
    }

    static boolean near(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }

    // ------------------------------------------------------------------ 1. back halves

    /**
     * Mirrored copies of the front half's shapes for a back-half tile. Only
     * single-axis slabs are mirrored (rotation about X for axis 0, about Z for
     * axis 1); anything else returns null so nothing wrong is invented.
     */
    public static ArrayList<Object> mirror(List<?> front, int axis) {
        if (front == null || front.isEmpty()) return null;
        ArrayList<Object> out = new ArrayList<>();
        try {
            for (Object s : front) {
                if (!isBox(s)) return null;
                Object r = vecField(s, "rotate");
                boolean ok = axis == 0 ? near(y(r), 0, 0.01f) && near(z(r), 0, 0.01f)
                        : near(x(r), 0, 0.01f) && near(y(r), 0, 0.01f);
                if (!ok) return null;
                Object c = copy(s);
                if (c == null) return null;
                Object t = vecField(c, "translate");
                Object rot = vecField(c, "rotate");
                Object mn = vecField(c, "min");
                Object mx = vecField(c, "max");
                if (axis == 0) {
                    set(rot, -x(rot), y(rot), z(rot));
                    set(t, x(t), y(t), -z(t));
                    float a = -z(mx);
                    float b = -z(mn);
                    set(mn, x(mn), y(mn), a);
                    set(mx, x(mx), y(mx), b);
                } else {
                    set(rot, x(rot), y(rot), -z(rot));
                    set(t, -x(t), y(t), z(t));
                    float a = -x(mx);
                    float b = -x(mn);
                    set(mn, a, y(mn), z(mn));
                    set(mx, b, y(mx), z(mx));
                }
                out.add(c);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 2. oversized slabs

    /**
     * The steep roof slabs are 2 x 2 tiles (min -1, max 1) on a 1 x 1 tile, so
     * in 3D they overhang 0.5 at the gables, hang 0.24 below the eaves and cross
     * in an X above the ridge [V1 calc]. Returns a copy trimmed to the tile
     * footprint (across the slope +-0.5, along the slope +-0.5/cos(angle)), or
     * null if the shape is not such a slab.
     */
    public static Object clipSlab(Object s) {
        try {
            if (!isBox(s)) return null;
            Object r = vecField(s, "rotate");
            Object mn = vecField(s, "min");
            Object mx = vecField(s, "max");
            boolean big = near(x(mn), -1, 0.01f) && near(x(mx), 1, 0.01f) && near(z(mn), -1, 0.01f) && near(z(mx), 1, 0.01f)
                    && y(mx) - y(mn) <= 0.1f;
            if (!big) return null;
            boolean aboutX = near(y(r), 0, 0.01f) && near(z(r), 0, 0.01f) && Math.abs(x(r)) >= 20 && Math.abs(x(r)) <= 60;
            boolean aboutZ = near(x(r), 0, 0.01f) && near(y(r), 0, 0.01f) && Math.abs(z(r)) >= 20 && Math.abs(z(r)) <= 60;
            if (!aboutX && !aboutZ) return null;
            float angle = (float) Math.toRadians(Math.abs(aboutX ? x(r) : z(r)));
            float along = (float) (0.5 / Math.cos(angle));
            Object c = copy(s);
            if (c == null) return null;
            Object cmn = vecField(c, "min");
            Object cmx = vecField(c, "max");
            if (aboutX) {
                set(cmn, -0.5f, y(cmn), -along);
                set(cmx, 0.5f, y(cmx), along);
            } else {
                set(cmn, -along, y(cmn), -0.5f);
                set(cmx, along, y(cmx), 0.5f);
            }
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 3. gable trims

    /**
     * Roof accent tiles (gable trims) get a 0.30 thick upright card whose
     * camera-facing side is 0.3 inside the gable (north card z -0.5..-0.2, west
     * card x -0.5..-0.2) [V1 log]. The trim art then sits 0.3 too far inside and
     * the card's back shows a second copy. Returns a thin copy (0.04) centred on
     * the gable plane (z or x = -0.5), or null if the shape is not such a card.
     */
    public static Object thinCard(Object s) {
        try {
            if (!isBox(s)) return null;
            Object r = vecField(s, "rotate");
            if (!(near(x(r), 0, 0.01f) && near(y(r), 0, 0.01f) && near(z(r), 0, 0.01f))) return null;
            Object mn = vecField(s, "min");
            Object mx = vecField(s, "max");
            if (y(mx) - y(mn) < 2.5f) return null;
            boolean north = near(z(mn), -0.5f, 0.01f) && near(z(mx), -0.2f, 0.01f) && x(mx) - x(mn) > 1.5f;
            boolean west = near(x(mn), -0.5f, 0.01f) && near(x(mx), -0.2f, 0.01f) && z(mx) - z(mn) > 1.5f;
            if (!north && !west) return null;
            Object c = copy(s);
            if (c == null) return null;
            Object cmn = vecField(c, "min");
            Object cmx = vecField(c, "max");
            if (north) {
                set(cmn, x(cmn), y(cmn), -0.52f);
                set(cmx, x(cmx), y(cmx), -0.48f);
            } else {
                set(cmn, -0.52f, y(cmn), z(cmn));
                set(cmx, -0.48f, y(cmx), z(cmx));
            }
            return c;
        } catch (Throwable t) {
            return null;
        }
    }
}
