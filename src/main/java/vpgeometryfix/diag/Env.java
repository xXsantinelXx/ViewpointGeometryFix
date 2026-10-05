package vpgeometryfix.diag;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Environment detection: Project Zomboid version, ZombieBuddy, Viewpoint.
 * Read-only. Class lookups never run static initializers (see {@link Reflect#find}).
 * Results are cached after the first call because hashing JARs costs a few
 * hundred milliseconds once.
 */
public final class Env {
    /** Viewpoint classes named in the third-party 0.1.5a-hotfix audit (kilroy94/project-viewpoint-vr). */
    public static final String[] VIEWPOINT_PROBE_CLASSES = {
            "viewpoint.FP", "viewpoint.SceneDrawer", "viewpoint.render.WorldRenderer", "viewpoint.core.View"};
    public static final String ZB_CLASS = "me.zed_0xff.zombie_buddy.ZombieBuddy";

    public static final class Info {
        public String pzVersion = "unknown";
        public String pzVersionSource = "none";
        public Path gameJar;
        public String gameJarSha;
        public boolean zbDetected;
        public String zbVersion = "unknown";
        public Path zbJar;
        public String zbJarSha;
        public boolean zbJavaAgent;
        public boolean vpClassesFound;
        public String vpProbeClass;
        public Path vpJar;
        public String vpJarSha;
        public String vpModId;
        public String vpModName;
        public String vpModVersion;
        public final List<String> notes = new ArrayList<>();
    }

    private static volatile Info cached;

    private Env() {}

    public static synchronized Info detect(boolean refresh) {
        if (cached != null && !refresh) return cached;
        Info i = new Info();
        detectGame(i);
        detectZombieBuddy(i);
        detectViewpoint(i);
        cached = i;
        return i;
    }

    private static void detectGame(Info i) {
        Class<?> core = Reflect.find("zombie.core.Core");
        if (core == null) {
            i.notes.add("zombie.core.Core not found (not running inside Project Zomboid?)");
            return;
        }
        i.gameJar = Reflect.codeSource(core);
        i.gameJarSha = KnownBinaries.sha256(i.gameJar);
        Object inst = Reflect.callStatic(core, "getInstance");
        // Names verified to exist in B42: Core.getVersion() / Core.getGameVersion()
        // (referenced by github.com/daaag0n00969/pz-42.21-compat). Others are fallbacks.
        for (String m : new String[] {"getVersion", "getGameVersion", "getVersionNumber"}) {
            Object v = Reflect.callStatic(core, m);
            if (v == null && inst != null) v = Reflect.call(inst, m);
            if (v != null && !v.toString().isBlank()) {
                i.pzVersion = v.toString();
                i.pzVersionSource = "Core." + m + "()";
                return;
            }
        }
        i.notes.add("no Core version accessor answered");
    }

    private static void detectZombieBuddy(Info i) {
        Class<?> zb = Reflect.find(ZB_CLASS);
        if (zb != null) {
            i.zbDetected = true;
            Object v = Reflect.callStatic(zb, "getVersion");
            if (v != null) i.zbVersion = v.toString();
            i.zbJar = Reflect.codeSource(zb);
            i.zbJarSha = KnownBinaries.sha256(i.zbJar);
        }
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                String a = arg.toLowerCase();
                if (a.startsWith("-javaagent:") && a.contains("zombiebuddy")) i.zbJavaAgent = true;
            }
        } catch (Throwable t) {
            i.notes.add("JVM arguments unreadable: " + t);
        }
    }

    private static void detectViewpoint(Info i) {
        for (String name : VIEWPOINT_PROBE_CLASSES) {
            Class<?> c = Reflect.find(name);
            if (c != null) {
                i.vpClassesFound = true;
                i.vpProbeClass = name;
                i.vpJar = Reflect.codeSource(c);
                break;
            }
        }
        if (i.vpJar == null) return;
        i.vpJarSha = KnownBinaries.sha256(i.vpJar);
        ModInfo mi = ModInfo.findAbove(i.vpJar, 6);
        if (mi != null) {
            i.vpModId = mi.get("id");
            i.vpModName = mi.get("name");
            i.vpModVersion = mi.get("modversion");
        }
    }

    /** Human readable Viewpoint version from the best available evidence. */
    public static String viewpointVersion(Info i) {
        List<String> parts = new ArrayList<>();
        if (i.vpModVersion != null) parts.add("mod.info modversion=" + i.vpModVersion);
        if (i.vpJarSha != null) {
            String id = KnownBinaries.identify(i.vpJarSha);
            parts.add("jar sha256=" + i.vpJarSha.substring(0, 12) + "... (" + id + ")");
        }
        return parts.isEmpty() ? "unknown" : String.join(", ", parts);
    }
}
