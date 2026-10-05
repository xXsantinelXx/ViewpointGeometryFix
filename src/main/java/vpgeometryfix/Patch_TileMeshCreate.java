package vpgeometryfix;

import me.zed_0xff.zombie_buddy.Patch;
import viewpoint.world.TileMesh;
import vpgeometryfix.fix.RoofMirror;

/**
 * Back roof halves, part 1: when Viewpoint builds the mesh of a north/west
 * roof half ({@code viewpoint.world.TileMeshes.create(IsoSprite, Texture,
 * IsoSprite) : TileMesh} [V1 signature, Viewpoint 0.1.5a-hotfix]) the mirrored
 * mesh of the front half is returned instead; see {@link RoofMirror}.
 * Approved by the user 2026-10-05; switchable (panel "Dach-Fix", config
 * roofFixMesh). TileMesh is a compile-only stub; ByteBuddy needs the exact
 * type to replace the return value.
 */
@Patch(className = "viewpoint.world.TileMeshes", methodName = "create")
public final class Patch_TileMeshCreate {
    private Patch_TileMeshCreate() {}

    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object sprite, @Patch.Argument(1) Object texture,
                            @Patch.Argument(2) Object base, @Patch.Return(readOnly = false) TileMesh ret) {
        Object replacement = RoofMirror.onCreate(sprite, texture, base, ret);
        if (replacement != null) ret = (TileMesh) replacement;
    }
}
