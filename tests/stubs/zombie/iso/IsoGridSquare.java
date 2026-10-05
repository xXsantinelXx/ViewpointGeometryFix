package zombie.iso;

import java.util.ArrayList;

import zombie.core.properties.PropertyContainer;

/** Stub of zombie.iso.IsoGridSquare. */
public class IsoGridSquare {

    public final ArrayList<IsoObject> objects = new ArrayList<>();
    public PropertyContainer properties;
    public IsoObject floor;
    public IsoObject wall;
    public Object room;
    public Object building;
    public int roomID = -1;
    public boolean outside = true;
    public boolean solidFloor;
    public boolean slopedRoof;
    public boolean eave;
    public boolean elevatedFloor;
    public boolean stairs;
    /** Set to make a getter throw, mirroring a half-loaded square. */
    public boolean failOnProperties;

    public ArrayList<IsoObject> getObjects() {
        return objects;
    }

    public PropertyContainer getProperties() {
        if (failOnProperties) {
            throw new IllegalStateException("square not ready");
        }
        return properties;
    }

    public IsoObject getFloor() {
        return floor;
    }

    public IsoObject getWall() {
        return wall;
    }

    public Object getRoom() {
        return room;
    }

    public Object getBuilding() {
        return building;
    }

    public Object getRoofHideBuilding() {
        return null;
    }

    public int getRoomID() {
        return roomID;
    }

    public boolean isOutside() {
        return outside;
    }

    public boolean isSolidFloor() {
        return solidFloor;
    }

    public boolean HasSlopedRoof() {
        return slopedRoof;
    }

    public boolean HasSlopedRoofNorth() {
        return slopedRoof;
    }

    public boolean HasSlopedRoofWest() {
        return false;
    }

    public boolean HasEave() {
        return eave;
    }

    public boolean HasElevatedFloor() {
        return elevatedFloor;
    }

    public boolean HasStairs() {
        return stairs;
    }
}
