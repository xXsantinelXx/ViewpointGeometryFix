package vpgeometryfix.fix;

/** Test access to package-private RoofMirror internals. */
public final class RoofMirrorTestAccess {
    private RoofMirrorTestAccess() {}

    public static float[] mirrorData(float[] d, int vertCount, int stride, int axis) {
        return RoofMirror.mirrorData(d, vertCount, stride, axis);
    }

    public static void setArtCentre(java.util.function.Function<String, Float> f) {
        RoofShapes.artCentre = f;
        RoofShapes.setBackMap(null); // clears the automatic maps
    }

    public static void setAssignment(java.util.function.Function<String, String> f) {
        RoofShapes.assignment = f;
        RoofShapes.setBackMap(null);
    }
}
