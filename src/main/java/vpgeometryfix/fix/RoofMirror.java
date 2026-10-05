package vpgeometryfix.fix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import vpgeometryfix.diag.Log;
import vpgeometryfix.diag.Reflect;

/**
 * Back roof halves with the FRONT half's picture (approved 2026-10-05).
 *
 * Why: Viewpoint paints a tile's 3D surface with the tile's own 2D sprite,
 * projected along the isometric view [H: MeshBuilder.TO_ISO_CAMERA,
 * frameX/frameY]. The north/west halves of steep roofs are edge-on in that view,
 * so their sprites have (almost) no picture; a correct surface alone would stay
 * invisible or smeared.
 *
 * How: when Viewpoint builds the mesh of a back-half tile
 * ({@code TileMeshes.create(IsoSprite, Texture, IsoSprite) : TileMesh} [V1
 * signature]) we build the matching front tile's mesh instead, mirror its
 * vertex positions (and normals, if present) across the tile centre, and
 * remember that this mesh must be drawn with the front tile's texture. The
 * texture swap happens where Viewpoint places a mesh into a chunk recipe
 * ({@code Recipe.place/placeFace/placeCaps/placed(Recipe, TextureID, ..., TileMesh,
 * float[] map, ...)} [V1 signatures]): page and map of our meshes are replaced
 * by those of the front texture.
 *
 * Safety: the mirrored mesh has exactly the same length and vertex count as a
 * mesh Viewpoint built itself; it is only produced when the layout checks pass
 * (positions in floats 0..2 within plausible bounds, data length = vertCount *
 * STRIDE, whole triangles). Every failure keeps Viewpoint's own result.
 */
public final class RoofMirror {
    static final String TILE_MESHES = "viewpoint.world.TileMeshes";
    static final String TILE_MESH = "viewpoint.world.TileMesh";
    static final String WORLD_MESHER = "viewpoint.world.WorldMesher";
    static final String TEXTURE = "zombie.core.textures.Texture";
    static final String TEXTURE_ID = "zombie.core.textures.TextureID";

    private static volatile boolean enabled = true;
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();
    /**
     * Our meshes -> Swap (front texture page and map). Keys by identity (TileMesh may
     * define equals by content) and weakly, so meshes Viewpoint drops are not kept alive.
     */
    private static final Map<IdKey, Swap> MESHES = new HashMap<>();
    private static final ReferenceQueue<Object> GONE = new ReferenceQueue<>();

    /** Weak identity key. */
    static final class IdKey extends WeakReference<Object> {
        final int hash;

        IdKey(Object o, ReferenceQueue<Object> q) {
            super(o, q);
            hash = System.identityHashCode(o);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdKey k)) return false;
            Object a = get();
            return a != null && a == k.get();
        }
    }

    static void register(Object mesh, Swap swap) {
        synchronized (MESHES) {
            for (Object r; (r = GONE.poll()) != null; ) MESHES.remove(r);
            MESHES.put(new IdKey(mesh, GONE), swap);
        }
    }

    static Swap lookup(Object mesh) {
        IdKey probe = new IdKey(mesh, null);
        synchronized (MESHES) {
            return MESHES.get(probe);
        }
    }
    private static volatile boolean any;
    private static final AtomicLong BUILT = new AtomicLong();
    private static final AtomicLong SWAPPED = new AtomicLong();
    /** Recipe.place* advice calls of any mesh: proves the Recipe patches are woven in. */
    private static final AtomicLong PLACE_CALLS = new AtomicLong();
    private static final AtomicLong REJECTED = new AtomicLong();
    private static final Map<String, Boolean> LOGGED = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Method create;
    private static volatile Constructor<?> meshCtor;
    private static volatile Method mapMethod;

    /** Front texture data for the recipe swap, resolved lazily. */
    static final class Swap {
        final Object backTexture;
        final Object frontTexture;
        volatile Object page;
        volatile float[] map;
        volatile boolean resolved;

        Swap(Object back, Object front) {
            backTexture = back;
            frontTexture = front;
        }
    }

    private RoofMirror() {}

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static long built() {
        return BUILT.get();
    }

    public static long swapped() {
        return SWAPPED.get();
    }

    public static long placeCalls() {
        return PLACE_CALLS.get();
    }

    public static long rejected() {
        return REJECTED.get();
    }

    /**
     * Advice body for TileMeshes.create OnExit. Returns a replacement mesh, or
     * null to keep Viewpoint's.
     */
    public static Object onCreate(Object sprite, Object texture, Object base, Object built) {
        if (!enabled || !RoofFallback.isEnabled() || sprite == null || Boolean.TRUE.equals(BUSY.get())) return null;
        try {
            Object nameObj = Reflect.call(sprite, "getName");
            String name = nameObj == null ? null : nameObj.toString();
            Object[] back = RoofShapes.backOf(name);
            if (back == null) return null;
            // only where the shape path filled this empty back half (same front, same axis)
            if (!RoofFallback.isMirroredBack(name)) return null;
            String frontName = (String) back[0];
            int axis = (Integer) back[1];
            logArt(name, texture);
            Object frontSprite = RoofFallback.spriteByName(frontName);
            Object frontTexture = texture(frontName);
            if (frontSprite == null || frontTexture == null) return reject(name, "front sprite/texture " + frontName + " not found");
            logArt(frontName, frontTexture);
            Object frontMesh;
            BUSY.set(Boolean.TRUE);
            try {
                frontMesh = invokeCreate(frontSprite, frontTexture, base == sprite ? frontSprite : base);
            } finally {
                BUSY.remove();
            }
            if (frontMesh == null) return reject(name, "front mesh of " + frontName + " not built");
            Object mirrored = mirrorMesh(frontMesh, axis);
            if (mirrored == null) return reject(name, "mesh layout check failed for " + frontName);
            register(mirrored, new Swap(texture, frontTexture));
            any = true;
            BUILT.incrementAndGet();
            if (LOGGED.putIfAbsent("built " + name, Boolean.TRUE) == null && LOGGED.size() <= 60) {
                Log.fileOnly("roof back half: " + name + " <- mirrored " + frontName + " (" + (axis == 0 ? "z" : "x")
                        + ", " + Reflect.field(mirrored, "vertCount") + " verts)");
            }
            return mirrored;
        } catch (Throwable t) {
            return reject(String.valueOf(sprite), "error " + t);
        }
    }

    private static Object reject(String name, String why) {
        REJECTED.incrementAndGet();
        if (LOGGED.putIfAbsent("rejected " + name, Boolean.TRUE) == null && LOGGED.size() <= 60) {
            Log.fileOnly("roof back half: " + name + " kept as Viewpoint built it (" + why + ")");
        }
        return null;
    }

    /**
     * Advice body for Recipe.place*: for our meshes returns {page, map} of the
     * front texture, else null. Fast path when no mesh was mirrored yet.
     */
    public static Object[] swap(Object mesh, Object page, float[] map) {
        PLACE_CALLS.incrementAndGet();
        if (!any || mesh == null) return null;
        try {
            Swap s = lookup(mesh);
            if (s == null) return null;
            if (!s.resolved) resolve(s, map);
            if (s.page == null || s.map == null) return null;
            SWAPPED.incrementAndGet();
            return new Object[] {s.page, s.map};
        } catch (Throwable t) {
            return null;
        }
    }

    /** Page (TextureID) and map of the front texture, using the same mapping method Viewpoint used for the back one. */
    static synchronized void resolve(Swap s, float[] backMap) {
        if (s.resolved) return;
        s.resolved = true;
        try {
            s.page = textureId(s.frontTexture);
            Method m = mapMethod;
            if (m == null) {
                Class<?> wm = Reflect.find(WORLD_MESHER);
                Class<?> tex = Reflect.find(TEXTURE);
                if (wm == null || tex == null) return;
                Method fallback = null;
                for (String n : new String[] {"mapping", "textureMapping"}) {
                    Method cand = staticMethod(wm, n, tex);
                    if (cand == null) continue;
                    if (fallback == null) fallback = cand;
                    Object r = cand.invoke(null, s.backTexture);
                    if (r instanceof float[] f && backMap != null && Arrays.equals(f, backMap)) {
                        m = cand;
                        break;
                    }
                }
                if (m == null) m = fallback;
                if (m == null) return;
                mapMethod = m;
                Log.fileOnly("roof back half: texture map via WorldMesher." + m.getName());
            }
            Object r = m.invoke(null, s.frontTexture);
            if (r instanceof float[] f) s.map = f;
        } catch (Throwable t) {
            Log.fileOnly("roof back half: texture swap unavailable: " + t);
        }
    }

    // ------------------------------------------------------------------ mesh mirroring

    /**
     * Mirrors a TileMesh across the tile centre (z -> -z for axis 0, x -> -x for
     * axis 1). Positions are assumed in floats 0..2 of each vertex [H, checked];
     * unit-length triples elsewhere in the vertex are treated as normals and
     * mirrored too; triangle winding is reversed. Returns a new TileMesh or null.
     */
    static Object mirrorMesh(Object mesh, int axis) throws ReflectiveOperationException {
        Object dataObj = Reflect.field(mesh, "data");
        Object vcObj = Reflect.field(mesh, "vertCount");
        Object strideObj = Reflect.staticField(TILE_MESH, "STRIDE");
        if (!(dataObj instanceof float[] d) || !(vcObj instanceof Integer vc) || !(strideObj instanceof Integer stride)) return null;
        float[] out = mirrorData(d, vc, stride, axis);
        if (out == null) return null;
        Constructor<?> k = meshCtor;
        if (k == null) {
            Class<?> c = mesh.getClass();
            k = c.getDeclaredConstructor(float[].class);
            k.setAccessible(true);
            meshCtor = k;
        }
        return k.newInstance((Object) out);
    }

    /** Pure data part of {@link #mirrorMesh}; null when the layout checks fail. */
    static float[] mirrorData(float[] d, int vertCount, int stride, int axis) {
        if (stride < 3 || vertCount <= 0 || vertCount % 3 != 0 || (long) vertCount * stride != d.length) return null;
        int comp = axis == 0 ? 2 : 0;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int v = 0; v < vertCount; v++) {
            int o = v * stride;
            float x = d[o];
            float y = d[o + 1];
            float z = d[o + 2];
            if (!(Math.abs(x) <= 1.6f && Math.abs(z) <= 1.6f && y >= -1.3f && y <= 3.8f)) return null; // not positions
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        // a front slab covers its own tile: centred on the tile origin. A corner origin (0..1) would put the
        // mirrored half one tile off, so it is refused.
        if (Math.abs(minX + maxX) > 0.15f || Math.abs(minZ + maxZ) > 0.15f) return null;
        // normals: offsets k >= 3 whose triple is unit length for every vertex
        boolean[] normal = new boolean[stride];
        for (int k = 3; k + 2 < stride; k++) {
            boolean unit = true;
            for (int v = 0; v < vertCount && unit; v++) {
                int o = v * stride + k;
                double len = Math.sqrt(d[o] * d[o] + d[o + 1] * d[o + 1] + d[o + 2] * d[o + 2]);
                unit = Math.abs(len - 1) < 0.02;
            }
            if (unit) {
                normal[k] = true;
                k += 2;
            }
        }
        float[] out = new float[d.length];
        for (int t = 0; t < vertCount; t += 3) {
            // reverse winding: vertex order 0,2,1
            int[] order = {t, t + 2, t + 1};
            for (int i = 0; i < 3; i++) {
                int src = order[i] * stride;
                int dst = (t + i) * stride;
                System.arraycopy(d, src, out, dst, stride);
                out[dst + comp] = -out[dst + comp];
                for (int k = 3; k + 2 < stride; k++) {
                    if (normal[k]) out[dst + k + comp] = -out[dst + k + comp];
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ game/Viewpoint lookups (reflective)

    static Object invokeCreate(Object sprite, Object texture, Object base) throws ReflectiveOperationException {
        Method m = create;
        if (m == null) {
            Class<?> c = Reflect.find(TILE_MESHES);
            if (c == null) return null;
            for (Method cand : c.getDeclaredMethods()) {
                if (cand.getName().equals("create") && cand.getParameterCount() == 3 && Modifier.isStatic(cand.getModifiers())) {
                    cand.setAccessible(true);
                    m = cand;
                    break;
                }
            }
            if (m == null) return null;
            create = m;
        }
        Class<?>[] p = m.getParameterTypes();
        if (!p[0].isInstance(sprite) || !p[1].isInstance(texture) || (base != null && !p[2].isInstance(base))) return null;
        return m.invoke(null, sprite, texture, base);
    }

    /** Texture by name without loading if possible: Texture.trygetTexture / getSharedTexture [B41 names, B42 U]. */
    static Object texture(String name) {
        Class<?> tex = Reflect.find(TEXTURE);
        if (tex == null) return null;
        for (String n : new String[] {"trygetTexture", "getSharedTexture"}) {
            Method m = staticMethod(tex, n, String.class);
            if (m == null) continue;
            try {
                Object r = m.invoke(null, name);
                if (r != null) return r;
            } catch (Throwable ignored) {
                // next
            }
        }
        return null;
    }

    /** The texture's page: the no-arg method returning TextureID [name U, found by type]. */
    static Object textureId(Object texture) {
        if (texture == null) return null;
        for (Class<?> c = texture.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers())
                        && m.getReturnType().getName().equals(TEXTURE_ID)) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(texture);
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    static Method staticMethod(Class<?> c, String name, Class<?> arg) {
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

    /** Logs the trimmed picture size of a roof sprite once (shows whether a back half has any picture). */
    static void logArt(String name, Object texture) {
        if (texture == null || LOGGED.putIfAbsent("art " + name, Boolean.TRUE) != null || LOGGED.size() > 60) return;
        Log.fileOnly("roof art: " + name + " " + art(texture));
    }

    /** "w x h at ox,oy of W x H" from Texture getters [B41 names, B42 U]; "?" parts when missing. */
    public static String art(Object texture) {
        return Reflect.call(texture, "getWidth") + "x" + Reflect.call(texture, "getHeight") + " at "
                + Reflect.call(texture, "getOffsetX") + "," + Reflect.call(texture, "getOffsetY") + " of "
                + Reflect.call(texture, "getWidthOrig") + "x" + Reflect.call(texture, "getHeightOrig");
    }
}
