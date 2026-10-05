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
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();
    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong REPLACED = new AtomicLong();
    private static final AtomicLong ROOF_EMPTY = new AtomicLong();
    private static final AtomicLong ROOF_SHAPED = new AtomicLong();
    private static final Map<String, Boolean> LOGGED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<>();
    private static final int SEEN_MAX = 60;
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
                seen(name, "has " + original.size() + " shape(s)");
                return null;
            }
            ROOF_EMPTY.incrementAndGet();
            String sibName = siblingSprite(name);
            if (sibName == null) {
                seen(name, "no shape, no sibling");
                return null;
            }
            if (!enabled) {
                seen(name, "no shape, fix B off");
                return null;
            }
            Object sibSprite = sprite(sibName);
            if (sibSprite == null) {
                seen(name, "no shape, sibling sprite " + sibName + " not found");
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
                seen(name, "no shape, sibling " + sibName + " has none either");
                return null;
            }
            REPLACED.incrementAndGet();
            if (LOGGED.putIfAbsent(name, Boolean.TRUE) == null && LOGGED.size() <= 20) {
                Log.fileOnly("roof fix B: " + name + " <- " + sibName + " (" + shapes.size() + " shape(s))");
            }
            return new ArrayList<>(shapes);
        } catch (Throwable t) {
            Log.fileOnly("roof fix B failed: " + t);
            return null;
        }
    }

    /** One file-log line per distinct roof sprite (capped): which roofs occur and what Viewpoint has for them. */
    private static void seen(String name, String what) {
        if (SEEN.size() < SEEN_MAX && SEEN.putIfAbsent(name, Boolean.TRUE) == null) {
            Log.fileOnly("roof seen: " + name + " - " + what);
        }
    }

    /** File-log counter line; logged at a few call counts only, never per frame. */
    public static void stats() {
        Log.fileOnly("roof fix B stats: geometryFor calls=" + CALLS.get() + ", roofs with shape=" + ROOF_SHAPED.get()
                + ", roofs without shape=" + ROOF_EMPTY.get() + ", replaced=" + REPLACED.get()
                + ", fix B " + (enabled ? "ON" : "OFF"));
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
