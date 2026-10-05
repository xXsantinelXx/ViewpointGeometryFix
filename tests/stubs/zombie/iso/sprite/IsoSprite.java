package zombie.iso.sprite;

import zombie.core.properties.PropertyContainer;

/** Stub of zombie.iso.sprite.IsoSprite. */
public class IsoSprite {

    public String name;
    public PropertyContainer properties;
    public Object spriteGrid;
    public Object tileType;
    public boolean activeModel;

    public String getName() {
        return name;
    }

    public PropertyContainer getProperties() {
        return properties;
    }

    public Object getSpriteGrid() {
        return spriteGrid;
    }

    public Object getTileType() {
        return tileType;
    }

    public boolean hasActiveModel() {
        return activeModel;
    }
}
