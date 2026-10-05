package pzmod.vpgeometryfix;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What this installation actually is: game build, Viewpoint presence,
 * ZombieBuddy version, debug state.
 *
 * Everything in here is read-only. Nothing is patched, no game file is
 * written, and no renderer state is touched. Where a fact cannot be
 * established from the running JVM, the value stays the literal string
 * {@link #UNKNOWN} rather than a guess.
 */
public final class Environment {

    public static final String UNKNOWN = "unknown";

    /**
     * Viewpoint classes named by the audited binary contract of
     * Viewpoint 0.1.5a-hotfix (see docs/VIEWPOINT-RENDERING.md for where
     * each name comes from). Presence of a name is evidence that the
     * renderer is installed; absence is evidence of a different layout,
     * not proof that Viewpoint is missing.
     */
    static final String[] VIEWPOINT_CLASSES = {
            "viewpoint.FP",
            "viewpoint.SceneDrawer",
            "viewpoint.Hooks",
            "viewpoint.core.Frame",
            "viewpoint.render.WorldRenderer",
            "viewpoint.render.SceneData",
            "viewpoint.render.FarPass",
            "viewpoint.render.TemporalPass",
            "viewpoint.render.Retirement",
            "viewpoint.world.ChunkWalk",
            "viewpoint.visibility.Rooms",
            "viewpoint.models.Models",
            "viewpoint.models.ModelCull",
            "viewpoint.models.Characters",
            "viewpoint.models.CorpseView",
            "viewpoint.input.Look",
            "viewpoint.input.FreeCam",
            "viewpoint.input.ThirdPerson",
            "viewpoint.platform.SettingsWindow",
            "viewpoint.platform.ImGuiFrame",
    };

    /**
     * SHA-256 of JARs whose contents this project has an audit trail for.
     * A hash that is not in here is reported as the hash itself, never as
     * a version number.
     */
    private static final Map<String, String> KNOWN_JARS = new LinkedHashMap<>();

    static {
        KNOWN_JARS.put("94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f",
                "Viewpoint 0.1.5a-hotfix");
        KNOWN_JARS.put("e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33",
                "Project Zomboid 42.21.0");
        KNOWN_JARS.put("6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6",
                "ZombieBuddy 2.3.2");
        KNOWN_JARS.put("dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283",
                "ZombieBuddy 2.3.2 (B42.21 fix build)");
    }

    private static volatile boolean debug =
            Boolean.getBoolean("vpgeometryfix.debug");

    /** Mod version reported by the Lua side; see Api.setViewpointModVersion. */
    private static volatile String viewpointModVersion = UNKNOWN;

    private Environment() {
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    static void setViewpointModVersion(String value) {
        viewpointModVersion = (value == null || value.isEmpty()) ? UNKNOWN : value;
    }

    /** e.g. "42.21.0 (build 42.21.0)", or UNKNOWN when Core is unreadable. */
    public static String gameVersion() {
        try {
            zombie.core.Core core = zombie.core.Core.getInstance();
            if (core == null) {
                return UNKNOWN;
            }
            String number = core.getVersionNumber();
            Object version = core.getGameVersion();
            String build = version == null ? UNKNOWN : String.valueOf(version);
            return (number == null ? UNKNOWN : number) + " (GameVersion " + build + ")";
        } catch (Throwable t) {
            Log.error("reading the game version failed", t);
            return UNKNOWN;
        }
    }

    /**
     * ZombieBuddy's own version string. Called reflectively so that a
     * renamed or removed accessor in a future ZombieBuddy degrades to
     * UNKNOWN instead of throwing on our startup path.
     */
    public static String zombieBuddyVersion() {
        try {
            Class<?> zb = Class.forName("me.zed_0xff.zombie_buddy.ZombieBuddy");
            Object value = zb.getMethod("getVersion").invoke(null);
            return value == null ? UNKNOWN : String.valueOf(value);
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /** Viewpoint classes found in this JVM, in declaration order. */
    public static List<String> viewpointClassesFound() {
        List<String> found = new ArrayList<>();
        ClassLoader loader = Environment.class.getClassLoader();
        for (String name : VIEWPOINT_CLASSES) {
            try {
                // false: resolve the name without running static initializers,
                // so probing never starts any renderer work.
                Class.forName(name, false, loader);
                found.add(name);
            } catch (Throwable ignored) {
                // Not present under this name in this build.
            }
        }
        return found;
    }

    /**
     * One line describing Viewpoint: how many of the audited classes are
     * present, the mod version Lua reported, and the identified JAR when
     * its hash is one we have an audit trail for.
     */
    public static String viewpointSummary() {
        List<String> found = viewpointClassesFound();
        if (found.isEmpty()) {
            return "no (none of " + VIEWPOINT_CLASSES.length
                    + " audited viewpoint.* classes present)";
        }
        String jar = identifyJarOf("viewpoint/SceneDrawer.class");
        return "yes (" + found.size() + "/" + VIEWPOINT_CLASSES.length
                + " audited classes, mod version " + viewpointModVersion
                + ", jar " + jar + ")";
    }

    public static String zombieBuddySummary() {
        boolean loaderPresent;
        try {
            Class.forName("me.zed_0xff.zombie_buddy.ZombieBuddy", false,
                    Environment.class.getClassLoader());
            loaderPresent = true;
        } catch (Throwable t) {
            loaderPresent = false;
        }
        if (!loaderPresent) {
            return "no (ZombieBuddy classes not on this classpath)";
        }
        return "yes (version " + zombieBuddyVersion() + ")";
    }

    /**
     * Identifies the JAR a class file came from: the audited name when the
     * hash is known, otherwise "sha256:<hash>" plus the file name. Reading
     * and hashing is the only access; nothing is written.
     */
    public static String identifyJarOf(String classResource) {
        Path jar = jarPathOf(classResource);
        if (jar == null) {
            return UNKNOWN + " (resource " + classResource + " not found in a jar)";
        }
        String hash = sha256(jar);
        if (hash == null) {
            return UNKNOWN + " (" + jar.getFileName() + ", not readable)";
        }
        String known = KNOWN_JARS.get(hash);
        return known != null
                ? known + " [" + jar.getFileName() + "]"
                : jar.getFileName() + " sha256:" + hash;
    }

    /** The JAR file holding a class resource, or null when it is not in one. */
    static Path jarPathOf(String classResource) {
        try {
            URL url = Environment.class.getClassLoader().getResource(classResource);
            if (url == null || !"jar".equals(url.getProtocol())) {
                return null;
            }
            String path = url.getPath();
            int separator = path.indexOf("!/");
            if (separator < 0) {
                return null;
            }
            URL fileUrl = new URL(path.substring(0, separator));
            if (!"file".equals(fileUrl.getProtocol())) {
                return null;
            }
            return Paths.get(fileUrl.toURI());
        } catch (Throwable t) {
            Log.debug("jar lookup for " + classResource + " failed: " + t);
            return null;
        }
    }

    static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                for (int read; (read = input.read(buffer)) > 0; ) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Throwable t) {
            Log.debug("hashing " + file + " failed: " + t);
            return null;
        }
    }
}
