package zombie.core;

/** Stub of zombie.core.Core. */
public class Core {

    private static final Core INSTANCE = new Core();

    public static Core getInstance() {
        return INSTANCE;
    }

    public String getVersionNumber() {
        return "42.21.0";
    }

    public GameVersion getGameVersion() {
        return new GameVersion();
    }
}
