package vpgeometryfix.diag;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vpgeometryfix.fix.RoofFallback;

/**
 * On-demand check WHERE Viewpoint's roof shapes come from. In 0.2.2 Viewpoint
 * returned shapes for sprites that have none in media/tileGeometry.txt, often
 * the shape of a different tile (e.g. roofs_01_14 = roofs_01_4) [V1, log].
 *
 * Per sprite it compares Viewpoint's geometryFor result with
 * <ul>
 *   <li>the game's tile geometry per mod id (TileGeometryManager.getInstance():getModIDs()
 *       is [V1] from the game's TileGeometryEditor.lua; the per-tile getter is looked
 *       up by shape (String, String, int, int) -> List, name [U]),</li>
 *   <li>the depth-texture assignment (TileDepthTextureAssignmentManager.getInstance()
 *       :getAssignedTileName(modID, tileName), [V1] from the same Lua file) and the
 *       shapes Viewpoint returns for the assigned tile.</li>
 * </ul>
 * Package names of both managers are [H] (zombie.tileDepth like TileGeometryFile).
 * Read-only, reflective, runs once per button press.
 */
public final class GeometrySource {
    static final String[] GEOMETRY_MANAGER = {"zombie.tileDepth.TileGeometryManager"};
    static final String[] ASSIGNMENT_MANAGER = {"zombie.tileDepth.TileDepthTextureAssignmentManager"};
    static final Pattern TILE = Pattern.compile("^(.+)_(\\d+)$");
    static final int COLUMNS = 8; // tile sheets are 8 tiles wide [V1 for roofs: xy col 0..7]
    static final int MAX = 650; // FIXED + RoofFallback caps (400 with shape + 200 without)

    /** Sprites checked even if not seen yet: the 0.2.2 cross-index cases and controls. */
    static final String[] FIXED = {
        "roofs_01_14", "roofs_01_71", "roofs_02_14", "roofs_30_01_77", "roofs_30_02_80", "roofs_30_08_107",
        "roofs_01_11", "roofs_01_12", "roofs_01_69", "roofs_01_118", "roofs_02_118", "roofs_03_38", "roofs_05_116",
        "roofs_accents_01_4", "roofs_accents_01_22", "roofs_accents_30_01_22", "roofs_accents_30_01_0",
        "roofs_01_0", "roofs_02_3", "roofs_30_01_29", "roofs_30_06_0", "roofs_30_08_16",
    };

    private GeometrySource() {}

    public static List<String> report() {
        List<String> out = new ArrayList<>();
        Object gm = instance(GEOMETRY_MANAGER);
        Object am = instance(ASSIGNMENT_MANAGER);
        List<String> mods = modIds(gm);
        boolean modsReal = !mods.isEmpty();
        if (!modsReal) mods.add("game");
        Method perMod = gm == null ? null : perModGetter(gm.getClass());
        Method assigned = am == null ? null : method(am.getClass(), "getAssignedTileName", String.class, String.class);
        boolean ownChecked = perMod != null;
        boolean assignChecked = assigned != null;
        out.add("geometry managers: TileGeometryManager " + (gm == null ? "not found" : "ok") + ", mod ids " + mods
                + (modsReal ? "" : " (fallback, getModIDs not readable)")
                + ", per-tile getter " + (perMod == null ? "not found" : perMod.getName())
                + "; TileDepthTextureAssignmentManager " + (am == null ? "not found" : "ok")
                + ", getAssignedTileName " + (assigned == null ? "not found" : "ok"));

        Set<String> names = new LinkedHashSet<>();
        for (String n : FIXED) names.add(n);
        names.addAll(RoofFallback.seenNames());
        int count = 0;
        int own = 0;
        int viaAssigned = 0;
        int vpOnly = 0;
        int none = 0;
        int unchecked = 0;
        for (String name : names) {
            if (++count > MAX) {
                out.add("(more than " + MAX + " sprites, rest skipped)");
                break;
            }
            Object sprite = RoofFallback.spriteByName(name);
            List<?> vp = sprite == null ? null : RoofFallback.rawGeometryFor(sprite);
            String vpText = vp == null ? null : RoofFallback.describe(vp);
            StringBuilder sb = new StringBuilder();
            String verdict = null;
            Matcher m = TILE.matcher(name);
            for (String mod : mods) {
                if (perMod != null && m.matches()) {
                    int index = Integer.parseInt(m.group(2));
                    List<?> g = asList(invoke(gm, perMod, mod, m.group(1), index % COLUMNS, index / COLUMNS));
                    if (g != null && !g.isEmpty()) {
                        sb.append(" | ").append(mod).append(" own=").append(g.size());
                        if (verdict == null && vp != null && RoofFallback.key(vp).equals(RoofFallback.key(g))) verdict = "OWN(" + mod + ")";
                    }
                }
                if (assigned != null) {
                    Object a = invoke(am, assigned, mod, name);
                    if (a != null && !a.toString().isEmpty() && !a.toString().equals(name)) {
                        String an = a.toString();
                        List<?> av = RoofFallback.rawGeometryFor(RoofFallback.spriteByName(an));
                        boolean same = av != null && !av.isEmpty() && vp != null && RoofFallback.key(vp).equals(RoofFallback.key(av));
                        sb.append(" | ").append(mod).append(" assigned=").append(an)
                                .append(" vp(assigned)=").append(av == null ? "?" : av.size()).append(same ? " same" : " differs");
                        if (verdict == null && same) verdict = "ASSIGNED(" + mod + "->" + an + ")";
                    }
                }
            }
            if (verdict == null) {
                // VP-ONLY only when both comparisons were possible; otherwise we do not know
                verdict = vp == null ? "UNKNOWN" : vp.isEmpty() ? "NONE" : (ownChecked && assignChecked) ? "VP-ONLY" : "UNCHECKED";
            }
            if (verdict.startsWith("OWN")) own++;
            else if (verdict.startsWith("ASSIGNED")) viaAssigned++;
            else if (verdict.equals("VP-ONLY")) vpOnly++;
            else if (verdict.equals("UNCHECKED")) unchecked++;
            else none++;
            // verdict first, so a cut long line never loses it
            out.add(name + " => " + verdict + sb + " | vp=" + (vp == null ? "?" : vp.size() + (vp.isEmpty() ? "" : " " + vpText)));
        }
        out.add(1, "sources: own " + own + ", assigned " + viaAssigned + ", Viewpoint-only " + vpOnly
                + ", unchecked " + unchecked + ", none/unknown " + none
                + (ownChecked && assignChecked ? "" : " (checks incomplete:" + (ownChecked ? "" : " own geometry")
                        + (assignChecked ? "" : " assignments") + " not readable)"));
        return out;
    }

    static Object instance(String[] candidates) {
        for (String c : candidates) {
            Class<?> k = Reflect.find(c);
            if (k == null) continue;
            Object o = Reflect.callStatic(k, "getInstance");
            if (o != null) return o;
        }
        return null;
    }

    static List<String> modIds(Object gm) {
        List<String> out = new ArrayList<>();
        List<?> l = asList(Reflect.call(gm, "getModIDs"));
        if (l != null) for (Object o : l) if (o != null) out.add(o.toString());
        return out;
    }

    /** A public getter (String modID, String tileset, int col, int row) returning a List, name unknown. */
    static Method perModGetter(Class<?> c) {
        for (Method m : c.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && m.getName().startsWith("get") && p.length == 4
                    && p[0] == String.class && p[1] == String.class && p[2] == int.class && p[3] == int.class
                    && List.class.isAssignableFrom(m.getReturnType())) {
                return m;
            }
        }
        return null;
    }

    static Method method(Class<?> c, String name, Class<?>... params) {
        try {
            return c.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object invoke(Object target, Method m, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    static List<?> asList(Object o) {
        return o instanceof List<?> l ? l : null;
    }
}
