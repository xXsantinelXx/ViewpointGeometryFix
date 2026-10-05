package viewpoint.world;

import java.util.ArrayList;

/** Test double: stands in for Viewpoint's class (static lookup by sprite, signature per 0.1.5a-hotfix). */
public final class TileMeshes {
    private TileMeshes() {}

    /** Roof sprites get two shapes, everything else none. */
    static ArrayList<Object> geometryFor(fake.FakeSquare.FakeSprite sprite) {
        ArrayList<Object> out = new ArrayList<>();
        if (sprite.name.startsWith("roofs_")) {
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
