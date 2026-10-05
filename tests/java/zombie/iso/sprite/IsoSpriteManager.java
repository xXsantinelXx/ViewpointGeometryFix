package zombie.iso.sprite;

import fake.FakeSquare;

/** Test double for the game's sprite registry (only getSprite by name). */
public final class IsoSpriteManager {
    public static final IsoSpriteManager instance = new IsoSpriteManager();

    private IsoSpriteManager() {}

    public Object getSprite(String name) {
        return new FakeSquare.FakeSprite(name);
    }
}
