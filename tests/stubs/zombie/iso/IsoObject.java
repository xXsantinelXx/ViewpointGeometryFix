package zombie.iso;

import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;

/** Stub of zombie.iso.IsoObject. */
public class IsoObject {

    public IsoSprite sprite;
    public String spriteName;
    public String tileName;
    public String objectName;
    public Object type;
    public float alpha;
    public float targetAlpha;
    public float renderYOffset;
    public float offsetX;
    public float offsetY;
    public PropertyContainer properties;

    public IsoSprite getSprite() {
        return sprite;
    }

    public String getSpriteName() {
        return spriteName;
    }

    public String getTileName() {
        return tileName;
    }

    public String getObjectName() {
        return objectName;
    }

    public Object getType() {
        return type;
    }

    public float getAlpha() {
        return alpha;
    }

    public float getTargetAlpha() {
        return targetAlpha;
    }

    public float getRenderYOffset() {
        return renderYOffset;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public PropertyContainer getProperties() {
        return properties;
    }
}
