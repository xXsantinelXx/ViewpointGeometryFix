package fake;

import org.joml.Vector3f;

/** Test double for zombie.tileDepth.TileGeometryFile$Box: the fields seen in the user's logs. */
public final class FakeBox {
    public Vector3f translate = new Vector3f(0, 0, 0);
    public Vector3f rotate = new Vector3f(0, 0, 0);
    public Vector3f min = new Vector3f(0, 0, 0);
    public Vector3f max = new Vector3f(0, 0, 0);

    public FakeBox() {}

    public static FakeBox of(float[] t, float[] r, float[] mn, float[] mx) {
        FakeBox b = new FakeBox();
        b.translate = new Vector3f(t[0], t[1], t[2]);
        b.rotate = new Vector3f(r[0], r[1], r[2]);
        b.min = new Vector3f(mn[0], mn[1], mn[2]);
        b.max = new Vector3f(mx[0], mx[1], mx[2]);
        return b;
    }
}
