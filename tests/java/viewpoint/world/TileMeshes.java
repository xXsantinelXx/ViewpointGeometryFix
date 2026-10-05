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

    /** Like Viewpoint's mesh build: one triangle from the sprite's (fake) shape, positions then 2 UV floats. */
    static TileMesh create(fake.FakeSquare.FakeSprite sprite, zombie.core.textures.Texture tex, fake.FakeSquare.FakeSprite base) {
        CREATED.add(sprite.name);
        if (sprite.name.equals("roofs_01_0") || sprite.name.equals("roofs_05_0")) {
            return new TileMesh(new float[] {-0.5f, 0f, 0.5f, 0.1f, 0.9f, 0.5f, 0f, 0.5f, 0.9f, 0.9f, 0f, 0.8f, -0.5f, 0.5f, 0.1f});
        }
        return new TileMesh(new float[] {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0});
    }

    public static final java.util.List<String> CREATED = new java.util.ArrayList<>();

    /** Like the real data: roofs_01 sprites have two shapes, other roof colour variants none. */
    static ArrayList<Object> geometryFor(fake.FakeSquare.FakeSprite sprite) {
        ArrayList<Object> out = new ArrayList<>();
        if (sprite.name.equals("roofs_05_0") || sprite.name.equals("roofs_05_3")) {
            // steep slab as in the game data: 2 x 2 tiles, rotate X 39.2394 (or Z -39.4506)
            boolean x = sprite.name.endsWith("_0");
            out.add(fake.FakeBox.of(new float[] {0, 0.3982f, 0}, x ? new float[] {39.2394f, 0, 0} : new float[] {0, 0, -39.4506f},
                    new float[] {-1, 0, -1}, new float[] {1, 0.05f, 1}));
        } else if (sprite.name.equals("roofs_accents_01_4")) {
            out.add(fake.FakeBox.of(new float[] {0, 0, 0}, new float[] {0, 0, 0},
                    new float[] {-1.5f, -1, -0.5f}, new float[] {0.75f, 2.45f, -0.2f}));
        } else if (sprite.name.startsWith("roofs_01_") || sprite.name.equals("roofs_02_14")) {
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
