package viewpoint.world;

/** Test double for Viewpoint's WorldMesher.rise(IsoObject). */
public final class WorldMesher {
    private WorldMesher() {}

    static float rise(fake.FakeSquare.FakeObject o) {
        return 0.25f;
    }

    static boolean hasModel(fake.FakeSquare.FakeObject o) {
        return false;
    }

    /** Atlas mapping per texture (Viewpoint: static float[] mapping(Texture)). */
    static float[] mapping(zombie.core.textures.Texture t) {
        return t.name.endsWith("_8") ? new float[] {1, 2} : new float[] {3, 4};
    }

    static float[] textureMapping(zombie.core.textures.Texture t) {
        return new float[] {9, 9};
    }

    static boolean hasModel(String unrelatedOverload) {
        return true;
    }
}
