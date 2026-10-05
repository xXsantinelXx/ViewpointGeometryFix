package vpgeometryfix;

import me.zed_0xff.zombie_buddy.Patch;
import vpgeometryfix.fix.RoofMirror;
import zombie.core.textures.TextureID;

/**
 * Back roof halves, part 2: a mirrored front-half mesh (see {@link RoofMirror})
 * is drawn with the front tile's texture page and map. Hooks
 * {@code viewpoint.world.Recipe.placeCaps} [V1 signature: page at argument 1, mesh at
 * 2, texture map at 3]. Only meshes this mod created are touched; all other
 * calls return at once. TextureID is a compile-only stub.
 */
@Patch(className = "viewpoint.world.Recipe", methodName = "placeCaps")
public final class Patch_RecipePlaceCaps {
    private Patch_RecipePlaceCaps() {}

    @Patch.OnEnter
    public static void enter(@Patch.Argument(value = 1, readOnly = false) TextureID page,
                             @Patch.Argument(2) Object mesh,
                             @Patch.Argument(value = 3, readOnly = false) float[] map) {
        Object[] swap = RoofMirror.swap(mesh, page, map);
        if (swap != null) {
            page = (TextureID) swap[0];
            map = (float[]) swap[1];
        }
    }

    /**
     * No-op on purpose: ZombieBuddy 2.3.4 matches target methods by the advice
     * parameter types but turns an array parameter (float[] map) into its
     * component type, so the OnEnter advice alone would match nothing
     * (PatchEngine, MIT; verified offline). This advice has a plain matchable
     * signature; ByteBuddy then weaves the whole class, OnEnter included.
     */
    @Patch.OnExit
    public static void exit(@Patch.Argument(1) TextureID page) {
        // intentionally empty
    }
}
