package viewpoint.models;

/** Test double for the VPGF Doctor class listing (keyword "roof"/"mesh"). Not a real Viewpoint class. */
public final class RoofMeshes {
    public static int built;
    private float edgeHeight;

    public float edge(int x, int y) {
        return edgeHeight + x + y;
    }
}
