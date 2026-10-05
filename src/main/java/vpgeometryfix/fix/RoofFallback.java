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
    private static final Map<String, Boolean> LOGGED = new ConcurrentHashMap<>();
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
        CALLS.incrementAndGet();
        if (!enabled || sprite == null || (original != null && !original.isEmpty())) return null;
        if (Boolean.TRUE.equals(BUSY.get())) return null; // our own nested call for the sibling
        try {
            Object nameObj = Reflect.call(sprite, "getName");
            String sibName = siblingSprite(nameObj == null ? null : nameObj.toString());
            if (sibName == null) return null;
            Object sibSprite = sprite(sibName);
            if (sibSprite == null) return null;
            List<?> shapes;
            BUSY.set(Boolean.TRUE);
            try {
                shapes = invokeGeometryFor(sibSprite);
            } finally {
                BUSY.remove();
            }
            if (shapes == null || shapes.isEmpty()) return null;
            REPLACED.incrementAndGet();
            if (LOGGED.putIfAbsent(nameObj.toString(), Boolean.TRUE) == null && LOGGED.size() <= 20) {
                Log.fileOnly("roof fix B: " + nameObj + " <- " + sibName + " (" + shapes.size() + " shape(s))");
            }
            return new ArrayList<>(shapes);
        } catch (Throwable t) {
            Log.fileOnly("roof fix B failed: " + t);
            return null;
        }
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
