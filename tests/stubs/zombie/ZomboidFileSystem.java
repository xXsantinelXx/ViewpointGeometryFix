package zombie;

/** Stub of zombie.ZomboidFileSystem. */
public class ZomboidFileSystem {

    public static final ZomboidFileSystem instance = new ZomboidFileSystem();

    /** Tests point this at a temporary directory. */
    public static String cacheDir = System.getProperty("java.io.tmpdir");

    public String getCacheDir() {
        return cacheDir;
    }
}
