package zombie.core.textures;

import java.util.HashMap;
import java.util.Map;

/** Test double: textures by name, page and trimmed size. */
public final class Texture {
    static final Map<String, Texture> BY_NAME = new HashMap<>();
    public final String name;
    final TextureID id;

    Texture(String name) {
        this.name = name;
        this.id = new TextureID(name.startsWith("roofs_01_") ? "page1" : "page2");
    }

    public static Texture trygetTexture(String name) {
        return BY_NAME.computeIfAbsent(name, Texture::new);
    }

    public TextureID getTextureId() { return id; }
    public String getName() { return name; }
    public int getWidth() { return name.endsWith("_8") ? 128 : 100; }
    public int getHeight() { return name.endsWith("_8") ? 2 : 90; }
    public int getOffsetX() { return 0; }
    public int getOffsetY() { return 120; }
    public int getWidthOrig() { return 128; }
    public int getHeightOrig() { return 256; }
}
