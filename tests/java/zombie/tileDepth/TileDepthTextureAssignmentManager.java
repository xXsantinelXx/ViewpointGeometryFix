package zombie.tileDepth;

/** Test double: one tile borrows another tile's depth data. */
public final class TileDepthTextureAssignmentManager {
    private static final TileDepthTextureAssignmentManager INSTANCE = new TileDepthTextureAssignmentManager();

    public static TileDepthTextureAssignmentManager getInstance() {
        return INSTANCE;
    }

    public String getAssignedTileName(String modID, String tileName) {
        return "roofs_02_14".equals(tileName) ? "roofs_01_4" : null;
    }
}
