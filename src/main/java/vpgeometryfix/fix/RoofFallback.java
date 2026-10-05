package vpgeometryfix.fix;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vpgeometryfix.diag.Log;
import vpgeometryfix.diag.Reflect;

/**
 * Roof fix, variant B (experimental, switchable).
 *
 * Problem [V1 data, user's install]: in the game's tileGeometry.txt only 84 of
 * 1426 roof tiles carry a 3D shape. roofs_02..05 and roofs_30_02..10 carry
 * none, while roofs_01 and roofs_30_01 carry tilted slab boxes. Viewpoint takes
 * near-world tile shapes from {@code viewpoint.world.TileMeshes.geometryFor}
 * [V1 signature] and has to guess the rest.
 *
 * Fix [H]: these tilesets are colour variants with the same sheet layout, so
 * when geometryFor returns an EMPTY list for such a roof sprite, return the
 * shapes of the same tile index in the sibling tileset instead. Non-empty
 * results and all other sprites are never touched.
 *
 * Called from {@code vpgeometryfix.Patch_RoofGeometry} (ZombieBuddy advice).
 * Thread-safe; never throws into Viewpoint.
 */
public final class RoofFallback {
    static final String TILE_MESHES = "viewpoint.world.TileMeshes";
    static final Pattern SPRITE = Pattern.compile("^(roofs_(?:30_)?\\d+)_(\\d+)$");
    static final Pattern VARIANT = Pattern.compile("^roofs_0[2-5]$");
    static final Pattern VARIANT_30 = Pattern.compile("^roofs_30_(?:0[2-9]|10)$");

    private static volatile boolean enabled;
    private static volatile boolean mirrorOn = true;
    private static volatile boolean clipOn = true;
    private static volatile boolean trimOn = true;
    private static final AtomicLong MIRRORED = new AtomicLong();
    /** Back-half sprites whose empty result the shape path filled (the mesh path only acts on these). */
    private static final Map<String, Boolean> MIRRORED_BACK = new ConcurrentHashMap<>();
    private static final AtomicLong CLIPPED = new AtomicLong();
    private static final AtomicLong TRIMMED = new AtomicLong();
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();
    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong REPLACED = new AtomicLong();
    private static final AtomicLong ROOF_EMPTY = new AtomicLong();
    private static final AtomicLong ROOF_SHAPED = new AtomicLong();
    private static final Map<String, Boolean> LOGGED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> EMPTY_SEEN = new ConcurrentHashMap<>();
    private static final Map<String, String> SHAPE_FIRST = new ConcurrentHashMap<>();
    static final int SEEN_MAX = 400;
    static final int EMPTY_SEEN_MAX = 200;
    private static volatile Method geometryFor;
    private static volatile Object spriteManager;
    private static volatile Method getSprite;

    private RoofFallback() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** Parts of the roof fix (all under the master switch {@link #setEnabled}). */
    public static void setParts(boolean mirror, boolean clip, boolean trim) {
        mirrorOn = mirror;
        clipOn = clip;
        trimOn = trim;
    }

    public static boolean isMirroredBack(String name) {
        return name != null && MIRRORED_BACK.containsKey(name);
    }

    public static long mirrored() {
        return MIRRORED.get();
    }

    public static long clipped() {
        return CLIPPED.get();
    }

    public static long trimmed() {
        return TRIMMED.get();
    }

    /** Number of geometryFor calls seen by the advice (proves the patch is active). */
    public static long calls() {
        return CALLS.get();
    }

    public static long replaced() {
        return REPLACED.get();
    }

    /** Roof sprites ("roofs_*") for which Viewpoint found no shape / a shape. */
    public static long roofEmpty() {
        return ROOF_EMPTY.get();
    }

    public static long roofShaped() {
        return ROOF_SHAPED.get();
    }

    /** Sibling tileset with shapes for a roof tileset without, or null. */
    public static String siblingTileset(String tileset) {
        if (tileset == null) return null;
        if (VARIANT.matcher(tileset).matches()) return "roofs_01";
        if (VARIANT_30.matcher(tileset).matches()) return "roofs_30_01";
        return null;
    }

    /** "roofs_02_12" -> "roofs_01_12", or null if not a covered roof sprite. */
    public static String siblingSprite(String spriteName) {
        if (spriteName == null) return null;
        Matcher m = SPRITE.matcher(spriteName);
        if (!m.matches()) return null;
        String sib = siblingTileset(m.group(1));
        return sib == null ? null : sib + "_" + m.group(2);
    }

    /**
     * Advice body. Returns a replacement list, or null to keep Viewpoint's result.
     */
    public static ArrayList<Object> apply(Object sprite, List<?> original) {
        long n = CALLS.incrementAndGet();
        if (n == 1 || n == 100 || n == 1000 || n == 5000 || n == 20000 || n == 100000) stats();
        if (sprite == null || Boolean.TRUE.equals(BUSY.get())) return null; // BUSY: our own nested call
        boolean empty = original == null || original.isEmpty();
        try {
            Object nameObj = Reflect.call(sprite, "getName");
            String name = nameObj == null ? null : nameObj.toString();
            if (name == null || !name.startsWith("roofs_")) return null;
            if (!empty) {
                ROOF_SHAPED.incrementAndGet();
                if (SEEN.size() < SEEN_MAX && !SEEN.containsKey(name)) {
                    String text = describe(original);
                    String first = SHAPE_FIRST.size() < SEEN_MAX ? SHAPE_FIRST.putIfAbsent(key(original), name) : null;
                    seen(name, "has " + original.size() + " shape(s): "
                            + (first == null || first.equals(name) ? text : "same as " + first));
                }
                return enabled ? correct(name, original) : null;
            }
            ROOF_EMPTY.incrementAndGet();
            if (RoofShapes.backOf(name) != null && LOGGED.putIfAbsent("type " + name, Boolean.TRUE) == null && LOGGED.size() <= 60) {
                // the game's object type of this tile (IsoSprite.getType [B41 name, B42 U]) - helps to tell what the tile is
                Log.fileOnly("roof tile type: " + name + " type=" + Reflect.call(sprite, "getType"));
            }
            if (enabled && mirrorOn) {
                ArrayList<Object> half = backHalf(name);
                if (half != null) return half;
            }
            String sibName = siblingSprite(name);
            if (sibName == null) {
                emptySeen(name, "no shape, no sibling");
                return null;
            }
            if (!enabled) {
                emptySeen(name, "no shape, fix B off");
                return null;
            }
            Object sibSprite = sprite(sibName);
            if (sibSprite == null) {
                emptySeen(name, "no shape, sibling sprite " + sibName + " not found");
                return null;
            }
            List<?> shapes;
            BUSY.set(Boolean.TRUE);
            try {
                shapes = invokeGeometryFor(sibSprite);
            } finally {
                BUSY.remove();
            }
            if (shapes == null || shapes.isEmpty()) {
                emptySeen(name, "no shape, sibling " + sibName + " has none either");
                return null;
            }
            REPLACED.incrementAndGet();
            emptySeen(name, "no shape, replaced by fix B <- " + sibName);
            if (LOGGED.putIfAbsent(name, Boolean.TRUE) == null && LOGGED.size() <= 20) {
                Log.fileOnly("roof fix B: " + name + " <- " + sibName + " (" + shapes.size() + " shape(s)): " + describe(shapes));
            }
            return new ArrayList<>(shapes);
        } catch (Throwable t) {
            Log.fileOnly("roof fix B failed: " + t);
            return null;
        }
    }

    /**
     * Trimmed copies of oversized roof slabs and gable-trim cards (see
     * {@link RoofShapes#clipSlab}, {@link RoofShapes#thinCard}); null if nothing
     * needs a change. The original list and shapes stay untouched.
     */
    static ArrayList<Object> correct(String name, List<?> original) {
        if (!clipOn && !trimOn) return null;
        ArrayList<Object> out = null;
        for (int i = 0; i < original.size(); i++) {
            Object s = original.get(i);
            Object c = clipOn ? RoofShapes.clipSlab(s) : null;
            boolean clipped = c != null;
            if (c == null && trimOn && name.startsWith("roofs_accents_")) c = RoofShapes.thinCard(s);
            if (c == null) continue;
            if (out == null) out = new ArrayList<>(original);
            out.set(i, c);
            (clipped ? CLIPPED : TRIMMED).incrementAndGet();
            if (LOGGED.putIfAbsent("corr " + name, Boolean.TRUE) == null && LOGGED.size() <= 60) {
                Log.fileOnly("roof fix: " + name + (clipped ? " slab trimmed to its tile" : " trim card moved into the gable plane")
                        + ": " + describe(java.util.List.of(c)));
            }
        }
        return out;
    }

    /**
     * Shapes for an empty north/west half of a steep roof: the front half's
     * shapes (corrected), mirrored across the tile centre. Null if this is no
     * back-half tile or the front has nothing usable.
     */
    static ArrayList<Object> backHalf(String name) {
        Object[] back = RoofShapes.backOf(name);
        if (back == null) return null;
        String frontName = (String) back[0];
        List<?> front = rawGeometryFor(spriteByName(frontName));
        String source = frontName;
        if ((front == null || front.isEmpty()) && siblingSprite(frontName) != null) {
            source = siblingSprite(frontName);
            front = rawGeometryFor(spriteByName(source));
        }
        if (front == null || front.isEmpty()) {
            emptySeen(name, "no shape, back half: front " + frontName + " has none");
            return null;
        }
        List<?> corrected = correct(source, front);
        List<?> base = corrected != null ? corrected : front;
        if (!RoofShapes.isMirrorMode()) {
            // default: the partner tile's (corrected) shapes as they are - same slope, own picture
            MIRRORED.incrementAndGet();
            emptySeen(name, "no shape, roof tile <- shape of " + source + ": " + describe(base));
            return new ArrayList<>(base);
        }
        ArrayList<Object> mirrored = RoofShapes.mirror(base, (Integer) back[1]);
        if (mirrored == null) {
            emptySeen(name, "no shape, back half: " + source + " cannot be mirrored");
            return null;
        }
        MIRRORED.incrementAndGet();
        MIRRORED_BACK.put(name, Boolean.TRUE);
        emptySeen(name, "no shape, back half <- mirrored " + source + ": " + describe(mirrored));
        return mirrored;
    }

    /** One file-log line per distinct roof sprite (capped): which roofs occur and what Viewpoint has for them. */
    private static void seen(String name, String what) {
        if (SEEN.size() < SEEN_MAX && SEEN.putIfAbsent(name, Boolean.TRUE) == null) {
            Log.fileOnly("roof seen: " + name + " - " + what);
            if (SEEN.size() == SEEN_MAX) Log.fileOnly("roof seen: limit of " + SEEN_MAX + " sprites with shape reached");
        }
    }

    /** Like {@link #seen} for roof sprites WITHOUT shape, with its own cap so none is lost behind shaped ones. */
    private static void emptySeen(String name, String what) {
        if (EMPTY_SEEN.size() < EMPTY_SEEN_MAX && EMPTY_SEEN.putIfAbsent(name, Boolean.TRUE) == null) {
            Log.fileOnly("roof seen: " + name + " - " + what);
        }
    }

    /** Names of the roof sprites seen so far (with and without shape), for on-demand diagnostics. */
    public static List<String> seenNames() {
        ArrayList<String> out = new ArrayList<>(SEEN.keySet());
        out.addAll(EMPTY_SEEN.keySet());
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * Viewpoint's own geometryFor result for a sprite, with fix B kept out of it (BUSY), or null.
     * Diagnostics only; called on demand, never per frame.
     */
    public static List<?> rawGeometryFor(Object sprite) {
        if (sprite == null) return null;
        BUSY.set(Boolean.TRUE);
        try {
            return invokeGeometryFor(sprite);
        } catch (Throwable t) {
            return null;
        } finally {
            BUSY.remove();
        }
    }

    /**
     * Existing sprite by name, or null. Looks in the manager's name map first
     * (field NamedMap/namedMap [B41 name, B42 U]) so diagnostics do not create
     * sprites for unknown names; falls back to getSprite.
     */
    public static Object spriteByName(String name) {
        if (name == null) return null;
        try {
            Object mgr = Reflect.staticField("zombie.iso.sprite.IsoSpriteManager", "instance");
            for (String f : new String[] {"NamedMap", "namedMap"}) {
                Object map = Reflect.field(mgr, f);
                if (map instanceof Map<?, ?> m) return m.get(name);
            }
            // name map under another name: the first instance field that is a Map [H]
            if (mgr != null) {
                for (java.lang.reflect.Field f : mgr.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    if (f.get(mgr) instanceof Map<?, ?> m) return m.get(name);
                }
            }
            // no name map at all: getSprite, which may register a sprite for an unknown name [B41 behaviour, B42 U];
            // callers only pass names the game itself reported (seen roofs, assigned tiles)
            return sprite(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Compact, read-only text of shapes, e.g. "Box{translate=(..) rotate=(..) min=(..) max=(..)}".
     * Instance fields of the shape class and its superclasses [field names: U, read reflectively].
     */
    public static String describe(List<?> shapes) {
        return describe(shapes, 4, 700);
    }

    /** Untruncated form of {@link #describe} for equality checks (dedupe, source verdicts). */
    public static String key(List<?> shapes) {
        return describe(shapes, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    static String describe(List<?> shapes, int maxShapes, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shapes.size() && i < maxShapes; i++) {
            Object g = shapes.get(i);
            if (i > 0) sb.append("; ");
            if (g == null) {
                sb.append("null");
                continue;
            }
            sb.append(g.getClass().getSimpleName()).append('{');
            boolean first = true;
            for (Class<?> c = g.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    String v;
                    try {
                        f.setAccessible(true);
                        v = value(f.get(g));
                    } catch (Throwable t) {
                        v = "?";
                    }
                    if (!first) sb.append(' ');
                    first = false;
                    sb.append(f.getName()).append('=').append(v);
                }
            }
            sb.append('}');
        }
        if (shapes.size() > maxShapes) sb.append("; +").append(shapes.size() - maxShapes).append(" more");
        return sb.length() > maxChars ? sb.substring(0, maxChars) + " ..." : sb.toString();
    }

    private static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof float[] a) return java.util.Arrays.toString(a);
        if (v instanceof int[] a) return java.util.Arrays.toString(a);
        if (v instanceof Object[] a) return "Object[" + a.length + "]";
        if (v instanceof java.util.Collection<?> c) return c.size() <= 12 ? c.toString() : c.getClass().getSimpleName() + "[" + c.size() + "]";
        String s = v.toString().replaceAll("\\s+", " ");
        return s.length() > 120 ? s.substring(0, 120) + ".." : s;
    }

    /** File-log counter line; logged at a few call counts only, never per frame. */
    public static void stats() {
        Log.fileOnly("roof fix B stats: geometryFor calls=" + CALLS.get() + ", roofs with shape=" + ROOF_SHAPED.get()
                + ", roofs without shape=" + ROOF_EMPTY.get() + ", replaced=" + REPLACED.get()
                + ", fix B " + (enabled ? "ON" : "OFF") + ", back halves=" + MIRRORED.get() + ", slabs trimmed=" + CLIPPED.get()
                + ", trim cards=" + TRIMMED.get() + ", meshes mirrored=" + RoofMirror.built() + ", texture swaps=" + RoofMirror.swapped()
                + " of " + RoofMirror.placeCalls() + " places"
                + ", mesh rejects=" + RoofMirror.rejected());
    }

    static List<?> invokeGeometryFor(Object sprite) throws ReflectiveOperationException {
        Method m = geometryFor;
        if (m == null) {
            Class<?> c = Reflect.find(TILE_MESHES);
            if (c == null) return null;
            for (Method cand : c.getDeclaredMethods()) {
                if (cand.getName().equals("geometryFor") && cand.getParameterCount() == 1
                        && Modifier.isStatic(cand.getModifiers())) {
                    cand.setAccessible(true);
                    m = cand;
                    break;
                }
            }
            if (m == null) return null;
            geometryFor = m;
        }
        if (!m.getParameterTypes()[0].isInstance(sprite)) return null;
        Object r = m.invoke(null, sprite);
        return r instanceof List<?> l ? l : null;
    }

    /** IsoSpriteManager.instance.getSprite(name) [B41 API, B42 assumed - reflective]. */
    static Object sprite(String name) throws ReflectiveOperationException {
        Object mgr = spriteManager;
        Method get = getSprite;
        if (mgr == null || get == null) {
            mgr = Reflect.staticField("zombie.iso.sprite.IsoSpriteManager", "instance");
            if (mgr == null) return null;
            get = mgr.getClass().getMethod("getSprite", String.class);
            spriteManager = mgr;
            getSprite = get;
        }
        return get.invoke(mgr, name);
    }
}
