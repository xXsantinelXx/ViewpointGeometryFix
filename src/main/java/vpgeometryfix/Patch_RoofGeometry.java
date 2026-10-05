package vpgeometryfix;

import java.util.ArrayList;

import me.zed_0xff.zombie_buddy.Patch;
import vpgeometryfix.fix.RoofFallback;

/**
 * Roof fix variant B: ZombieBuddy advice on Viewpoint's tile shape lookup
 * {@code viewpoint.world.TileMeshes.geometryFor(IsoSprite) : ArrayList}
 * [V1 signature, Viewpoint 0.1.5a-hotfix]. Only replaces an EMPTY result for
 * roof sprites of tilesets without shapes; see {@link RoofFallback}.
 *
 * Must live directly in package {@code vpgeometryfix}: ZombieBuddy only
 * scans the javaPkgName package for @Patch classes.
 */
@Patch(className = "viewpoint.world.TileMeshes", methodName = "geometryFor")
public final class Patch_RoofGeometry {
    private Patch_RoofGeometry() {}

    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object sprite,
                            @Patch.Return(readOnly = false) ArrayList<Object> ret) {
        ArrayList<Object> replacement = RoofFallback.apply(sprite, ret);
        if (replacement != null) ret = replacement;
    }
}
