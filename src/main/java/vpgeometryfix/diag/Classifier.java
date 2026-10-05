package vpgeometryfix.diag;

import java.util.Locale;

/**
 * HEURISTIC classification of a tile object for grouping diagnostics.
 * Based only on the Java class simple name and the sprite (tile) name,
 * e.g. {@code roofs_01_12}, {@code walls_exterior_house_01_0}. It does not
 * reflect how Viewpoint classifies geometry (that logic is still unknown).
 */
public final class Classifier {
    public enum Kind { ROOF, WALL, FLOOR, DOOR, WINDOW, STAIRS, VEGETATION, CHARACTER, ITEM, OTHER }

    private Classifier() {}

    public static Kind classify(String className, String spriteName) {
        String c = className == null ? "" : className.toLowerCase(Locale.ROOT);
        String s = spriteName == null ? "" : spriteName.toLowerCase(Locale.ROOT);
        if (c.endsWith("zombie") || c.endsWith("player") || c.endsWith("deadbody")) return Kind.CHARACTER;
        if (c.endsWith("worldinventoryobject")) return Kind.ITEM;
        if (c.endsWith("door") || s.contains("door")) return Kind.DOOR;
        if (c.contains("window") || s.contains("window")) return Kind.WINDOW;
        if (s.startsWith("roofs_") || s.contains("roof")) return Kind.ROOF;
        if (s.contains("stair")) return Kind.STAIRS;
        if (c.endsWith("wall") || s.startsWith("walls_") || s.contains("_wall")) return Kind.WALL;
        if (s.startsWith("floors_") || s.startsWith("blends_") || s.contains("floor")) return Kind.FLOOR;
        if (c.endsWith("tree") || s.startsWith("e_") || s.startsWith("vegetation_")) return Kind.VEGETATION;
        return Kind.OTHER;
    }
}
