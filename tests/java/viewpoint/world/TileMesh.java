package viewpoint.world;

/** Test double: field layout as in Viewpoint 0.1.5a-hotfix signatures. */
public final class TileMesh {
    static int STRIDE = 5;
    static final TileMesh EMPTY = new TileMesh(new float[0]);
    float[] data;
    int vertCount;
    long hash;

    TileMesh(float[] data) {
        this.data = data;
        this.vertCount = data.length / STRIDE;
    }
}
