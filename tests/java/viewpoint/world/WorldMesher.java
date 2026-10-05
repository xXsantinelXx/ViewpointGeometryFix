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

    static boolean hasModel(String unrelatedOverload) {
        return true;
    }
}
