package viewpoint.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Test double: stands in for Viewpoint's class (static lookup by sprite, signature per 0.1.5a-hotfix). */
public final class TileMeshes {
    static String GAME = "game";
    static final IdentityHashMap<Object, Object> cache = new IdentityHashMap<>();

    private TileMeshes() {}

    /** Like Viewpoint's cache value. */
    static final class Held {
        TileMesh mesh;
        long used;
        Held(TileMesh m) { mesh = m; }
    }

    /** Test hook: fills the cache like a built chunk would. */
    public static void fillCacheForTests() {
        cache.clear();
        cache.put(new fake.FakeSquare.FakeSprite("roofs_01_0"),
                new Held(new TileMesh(new float[] {-1, 0, -1, 0, 0, 1, 0.05f, 1, 1, 1})));
        cache.put(new fake.FakeSquare.FakeSprite("roofs_01_11"), new Held(TileMesh.EMPTY));
        cache.put(new fake.FakeSquare.FakeSprite("walls_01_0"), new Held(new TileMesh(new float[5])));
    }

    /** Like the real data: roofs_01 sprites have two shapes, other roof colour variants none. */
    static ArrayList<Object> geometryFor(fake.FakeSquare.FakeSprite sprite) {
        ArrayList<Object> out = new ArrayList<>();
        if (sprite.name.startsWith("roofs_01_") || sprite.name.equals("roofs_02_14")) {
            out.add(new Polygon(new float[] {0, 0, 1, 1}));
            out.add(new Box());
        }
        return out;
    }

    public static final class Polygon {
        public final float[] points;
        public Polygon(float[] p) { points = p; }
    }

    public static final class Box {
        public float height = 2.5f;
    }
}
