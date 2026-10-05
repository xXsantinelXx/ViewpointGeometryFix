package vpgeometryfix.diag;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Asks Viewpoint which 3D source geometry it uses for a tile object.
 *
 * Signatures [V1] from the user's Viewpoint 0.1.5a-hotfix JAR (VPGF Doctor 0.3.0):
 * <ul>
 *   <li>{@code viewpoint.world.TileMeshes.geometryFor(IsoSprite) -> ArrayList}
 *       (elements: {@code zombie.tileDepth.TileGeometryFile$Geometry}, consumed by
 *       {@code MeshBuilder.add}; role inferred from names [H])</li>
 *   <li>{@code viewpoint.world.WorldMesher.rise(IsoObject) -> float} (vertical lift [H])</li>
 * </ul>
 * Both are static lookups; they are called reflectively, only during an
 * explicit inspection, and any failure yields "unknown". Nothing is cached or
 * changed by this class.
 */
public final class ViewpointGeometry {
    static final String TILE_MESHES = "viewpoint.world.TileMeshes";
    static final String WORLD_MESHER = "viewpoint.world.WorldMesher";
    static final String[] EXPAND = {"zombie.tileDepth.", "org.joml."};

    private ViewpointGeometry() {}

    /** Short text for the TILE line, e.g. "vpGeom=2(Box,Polygon) rise=0.0", or "" without Viewpoint. */
    public static String summary(Object obj) {
        if (obj == null || Reflect.find(TILE_MESHES) == null) return "";
        Object sprite = Reflect.call(obj, "getSprite");
        StringBuilder sb = new StringBuilder();
        List<Object> geo = sprite == null ? null : SquareInspector.asList(callStatic(TILE_MESHES, "geometryFor", sprite));
        if (sprite == null) sb.append("vpGeom=no-sprite");
        else if (geo == null) sb.append("vpGeom=unknown");
        else {
            sb.append("vpGeom=").append(geo.size());
            if (!geo.isEmpty()) {
                sb.append('(');
                for (int i = 0; i < geo.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object g = geo.get(i);
                    sb.append(g == null ? "null" : g.getClass().getSimpleName());
                }
                sb.append(')');
            }
        }
        Object nameObj = Reflect.call(sprite, "getName");
        String sib = vpgeometryfix.fix.RoofFallback.siblingSprite(nameObj == null ? null : nameObj.toString());
        if (sib != null) sb.append(" fixB=").append(vpgeometryfix.fix.RoofFallback.isEnabled() ? "an" : "aus").append("<-").append(sib);
        Object rise = callStatic(WORLD_MESHER, "rise", obj);
        if (rise != null) sb.append(" rise=").append(rise);
        // WorldMesher.hasModel(IsoObject) [V1 signature]: true = drawn from a model pack instead of / besides tile geometry [H]
        Object model = callStatic(WORLD_MESHER, "hasModel", obj);
        if (model != null) sb.append(" model=").append(model);

        return sb.toString();
    }

    /** Full dump of the source geometry for the report file. */
    public static String details(Object obj) {
        if (obj == null || Reflect.find(TILE_MESHES) == null) return "";
        Object sprite = Reflect.call(obj, "getSprite");
        if (sprite == null) return "";
        List<Object> geo = SquareInspector.asList(callStatic(TILE_MESHES, "geometryFor", sprite));
        if (geo == null) return "      viewpoint geometryFor: unknown\n";
        StringBuilder sb = new StringBuilder("      viewpoint geometryFor: " + geo.size() + " shape(s)\n");
        if (!geo.isEmpty()) sb.append("        compact: ").append(vpgeometryfix.fix.RoofFallback.describe(geo)).append('\n');
        for (int i = 0; i < geo.size(); i++) {
            ObjectDumper d = new ObjectDumper(EXPAND, 2, 80, 16);
            d.dump("        #" + i + " ", geo.get(i), 0);
            sb.append(d.result());
        }
        return sb.toString();
    }

    /**
     * Invokes a static one-argument method by name whose parameter accepts
     * {@code arg}. Returns null if the class/method is missing or the call fails.
     */
    static Object callStatic(String className, String method, Object arg) {
        Class<?> c = Reflect.find(className);
        if (c == null || arg == null) return null;
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(method) || m.getParameterCount() != 1 || !Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getParameterTypes()[0].isInstance(arg)) continue;
                m.setAccessible(true);
                return m.invoke(null, arg);
            }
        } catch (Throwable t) {
            Log.fileOnly("viewpoint " + className + "." + method + " failed: " + t);
        }
        return null;
    }
}
