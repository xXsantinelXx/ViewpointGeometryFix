package zombie.tileDepth;

import java.util.ArrayList;
import java.util.List;

/** Test double: the game's tile geometry per mod id (only roofs_01 has data here). */
public final class TileGeometryManager {
    private static final TileGeometryManager INSTANCE = new TileGeometryManager();

    public static TileGeometryManager getInstance() {
        return INSTANCE;
    }

    public ArrayList<String> getModIDs() {
        ArrayList<String> l = new ArrayList<>();
        l.add("game");
        return l;
    }

    public List<Object> getGeometry(String modID, String tileset, int col, int row) {
        ArrayList<Object> out = new ArrayList<>();
        if ("game".equals(modID) && "roofs_01".equals(tileset) && row == 0) {
            out.add(new viewpoint.world.TileMeshes.Polygon(new float[] {0, 0, 1, 1}));
            out.add(new viewpoint.world.TileMeshes.Box());
        }
        return out;
    }
}
