package pzmod.vpgeometryfix;

import java.util.ArrayList;
import java.util.List;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.sprite.IsoSprite;

/**
 * Reads one square and the objects on it and returns a text report.
 *
 * Read-only on purpose: every call here is a getter. Nothing is drawn,
 * no alpha is written, no renderer state is entered. The report is the
 * raw material for the later geometry work — for a roof tile that does
 * not show up in Viewpoint we want to know its sprite name, its tile
 * type, its flags and its render offsets before touching any renderer.
 *
 * The probe runs only when it is asked to (hotkey or an explicit
 * coordinate from Lua), never per frame.
 */
public final class TileProbe {

    /** Objects listed per square; a stack deeper than this is truncated. */
    private static final int MAX_OBJECTS = 24;

    private TileProbe() {
    }

    /** Report for one square, or a one-line reason why there is none. */
    public static String describeSquare(int x, int y, int z) {
        StringBuilder out = new StringBuilder();
        out.append("square ").append(x).append(',').append(y).append(',').append(z);
        IsoGridSquare square;
        try {
            IsoWorld world = IsoWorld.instance;
            if (world == null || world.getCell() == null) {
                return out.append(" -> no cell loaded").toString();
            }
            square = world.getCell().getGridSquare(x, y, z);
        } catch (Throwable t) {
            return out.append(" -> lookup failed: ").append(t).toString();
        }
        if (square == null) {
            return out.append(" -> not loaded").toString();
        }
        out.append('\n');
        appendSquare(out, square);
        appendObjects(out, square);
        return out.toString();
    }

    private static void appendSquare(StringBuilder out, IsoGridSquare square) {
        out.append("  outside=").append(call(() -> square.isOutside()))
                .append(" solidFloor=").append(call(() -> square.isSolidFloor()))
                .append(" slopedRoof=").append(call(() -> square.HasSlopedRoof()))
                .append(" slopedRoofN=").append(call(() -> square.HasSlopedRoofNorth()))
                .append(" slopedRoofW=").append(call(() -> square.HasSlopedRoofWest()))
                .append('\n');
        out.append("  eave=").append(call(() -> square.HasEave()))
                .append(" elevatedFloor=").append(call(() -> square.HasElevatedFloor()))
                .append(" stairs=").append(call(() -> square.HasStairs()))
                .append(" roofHideBuilding=").append(call(() -> square.getRoofHideBuilding()))
                .append('\n');
        out.append("  room=").append(call(() -> {
            Object room = square.getRoom();
            return room == null ? "none" : String.valueOf(square.getRoomID());
        })).append(" building=").append(call(() -> {
            Object building = square.getBuilding();
            return building == null ? "none" : "present";
        })).append(" floorObject=").append(call(() -> {
            IsoObject floor = square.getFloor();
            return floor == null ? "none" : spriteNameOf(floor);
        })).append(" wallObject=").append(call(() -> {
            IsoObject wall = square.getWall();
            return wall == null ? "none" : spriteNameOf(wall);
        })).append('\n');
        out.append("  squareProperties: ")
                .append(call(() -> properties(square.getProperties())))
                .append('\n');
    }

    private static void appendObjects(StringBuilder out, IsoGridSquare square) {
        List<IsoObject> objects = objectsOf(square);
        out.append("  objects=").append(objects.size()).append('\n');
        int shown = Math.min(objects.size(), MAX_OBJECTS);
        for (int i = 0; i < shown; i++) {
            appendObject(out, i, objects.get(i));
        }
        if (shown < objects.size()) {
            out.append("  ... ").append(objects.size() - shown)
                    .append(" more object(s) not listed\n");
        }
    }

    private static void appendObject(StringBuilder out, int index, IsoObject object) {
        out.append("  [").append(index).append("] ")
                .append(call(() -> object.getClass().getName())).append('\n');
        out.append("      sprite=").append(call(() -> spriteNameOf(object)))
                .append(" tile=").append(call(() -> String.valueOf(object.getTileName())))
                .append(" objectName=").append(call(() -> String.valueOf(object.getObjectName())))
                .append(" type=").append(call(() -> String.valueOf(object.getType())))
                .append('\n');
        out.append("      alpha=").append(call(() -> String.valueOf(object.getAlpha())))
                .append(" targetAlpha=").append(call(() -> String.valueOf(object.getTargetAlpha())))
                .append(" renderYOffset=").append(call(() -> String.valueOf(object.getRenderYOffset())))
                .append(" offset=").append(call(() -> object.getOffsetX() + "/" + object.getOffsetY()))
                .append('\n');
        out.append("      spriteProperties: ").append(call(() -> {
            IsoSprite sprite = object.getSprite();
            return sprite == null ? "no sprite" : properties(sprite.getProperties());
        })).append('\n');
        out.append("      spriteGrid=").append(call(() -> {
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                return "no sprite";
            }
            Object grid = sprite.getSpriteGrid();
            return grid == null ? "none" : "present";
        })).append(" tileType=").append(call(() -> {
            IsoSprite sprite = object.getSprite();
            Object tileType = sprite == null ? null : sprite.getTileType();
            return tileType == null ? "none" : String.valueOf(tileType);
        })).append(" activeModel=").append(call(() -> {
            IsoSprite sprite = object.getSprite();
            return sprite == null ? "no sprite" : String.valueOf(sprite.hasActiveModel());
        })).append('\n');
    }

    /**
     * getObjects() is declared as a game-specific list type, so it is read
     * as an Iterable: that keeps this code compiling against the real jar
     * and against the test stubs alike.
     */
    static List<IsoObject> objectsOf(IsoGridSquare square) {
        List<IsoObject> result = new ArrayList<>();
        try {
            Iterable<?> objects = square.getObjects();
            if (objects != null) {
                for (Object candidate : objects) {
                    if (candidate instanceof IsoObject) {
                        result.add((IsoObject) candidate);
                    }
                }
            }
        } catch (Throwable t) {
            Log.debug("reading the object list failed: " + t);
        }
        return result;
    }

    static String spriteNameOf(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        if (sprite == null) {
            String name = object.getSpriteName();
            return name == null ? "none" : name + " (no sprite instance)";
        }
        String name = sprite.getName();
        return name == null ? "unnamed sprite" : name;
    }

    /** Flags and property names of a container, as a single short list. */
    static String properties(PropertyContainer container) {
        if (container == null) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        Iterable<?> flags = container.getFlagsList();
        if (flags != null) {
            out.append("flags[");
            boolean first = true;
            for (Object flag : flags) {
                if (!first) {
                    out.append(' ');
                }
                out.append(flag);
                first = false;
            }
            out.append(']');
        }
        Iterable<?> names = container.getPropertyNames();
        if (names != null) {
            out.append(" props[");
            boolean first = true;
            for (Object name : names) {
                if (!first) {
                    out.append(' ');
                }
                out.append(name).append('=').append(container.get(String.valueOf(name)));
                first = false;
            }
            out.append(']');
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    /**
     * Any single getter may throw on a half-loaded square; one failing
     * field must not cost the whole report.
     */
    private static String call(Reader reader) {
        try {
            Object value = reader.read();
            return String.valueOf(value);
        } catch (Throwable t) {
            return "<error " + t.getClass().getSimpleName() + ">";
        }
    }

    private interface Reader {
        Object read() throws Throwable;
    }
}
