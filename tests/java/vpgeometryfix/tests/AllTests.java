package vpgeometryfix.tests;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import fake.FakeSquare;
import vpgeometryfix.LuaBridge;
import vpgeometryfix.diag.ClassInventory;
import vpgeometryfix.diag.Classifier;
import vpgeometryfix.diag.Config;
import vpgeometryfix.diag.Env;
import vpgeometryfix.diag.KnownBinaries;
import vpgeometryfix.diag.ModInfo;
import vpgeometryfix.diag.ObjectDumper;
import vpgeometryfix.diag.Paths;

/**
 * Dependency-free test runner (no JUnit, no network). Runs outside the game:
 * game classes are absent, a test double stands in for viewpoint.core.View.
 */
public final class AllTests {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    interface Body { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("vpgf-test");
        Paths.overrideOutputDir(tmp);

        test("ModInfo.parse", () -> {
            var m = ModInfo.parse(List.of("# c", "name=Viewpoint", "id = Viewpoint ", "modversion=0.1.5a-hotfix", "bad", "id=dup"));
            eq("Viewpoint", m.get("id"));
            eq("0.1.5a-hotfix", m.get("modversion"));
            eq(3, m.size());
        });

        test("ModInfo.findAbove", () -> {
            Path mod = tmp.resolve("Viewpoint/42");
            Path jarDir = mod.resolve("media/java/client");
            Files.createDirectories(jarDir);
            Files.writeString(mod.resolve("mod.info"), "id=Viewpoint\nmodversion=9.9\n");
            Path jar = jarDir.resolve("vp.jar");
            Files.writeString(jar, "x");
            eq("9.9", ModInfo.findAbove(jar, 6).get("modversion"));
            eq(null, ModInfo.findAbove(jar, 2));
        });

        test("KnownBinaries", () -> {
            eq("Viewpoint 0.1.5a-hotfix", KnownBinaries.identify("94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f"));
            eq("not an audited build", KnownBinaries.identify("00"));
            eq("unknown", KnownBinaries.identify(null));
            Path f = tmp.resolve("abc.txt");
            Files.writeString(f, "abc");
            eq("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", KnownBinaries.sha256(f));
        });

        test("Classifier", () -> {
            eq(Classifier.Kind.ROOF, Classifier.classify("IsoObject", "roofs_01_12"));
            eq(Classifier.Kind.WALL, Classifier.classify("IsoObject", "walls_exterior_house_01_0"));
            eq(Classifier.Kind.DOOR, Classifier.classify("IsoDoor", "fixtures_doors_01_0"));
            eq(Classifier.Kind.WINDOW, Classifier.classify("IsoWindow", "fixtures_windows_01_0"));
            eq(Classifier.Kind.FLOOR, Classifier.classify("IsoObject", "floors_interior_tilesandwood_01_0"));
            eq(Classifier.Kind.CHARACTER, Classifier.classify("IsoZombie", null));
            eq(Classifier.Kind.OTHER, Classifier.classify(null, null));
        });

        test("ClassInventory.matches", () -> {
            check(ClassInventory.matches("viewpoint.render.WorldRenderer"));
            check(ClassInventory.matches("viewpoint.visibility.Rooms"));
            check(ClassInventory.matches("viewpoint.models.ModelCull"));
            check(!ClassInventory.matches("viewpoint.input.Look"));
        });

        test("ObjectDumper cycles, arrays, collections, cap", () -> {
            FakeSquare sq = new FakeSquare(1, 2, 3);
            sq.getObjects().add(new FakeSquare.FakeObject("roofs_01_1"));
            ObjectDumper d = new ObjectDumper(new String[] {"fake."}, 3, 1000, 4);
            d.dump("", sq, 0);
            String r = d.result();
            contains(r, "class fake.FakeSquare");
            contains(r, "(already dumped) fake.FakeSquare@");
            contains(r, "FakeSquare.x : int = 1");
            contains(r, "ArrayList(size=1)");
            ObjectDumper small = new ObjectDumper(new String[] {"fake."}, 3, 3, 4);
            small.dump("", sq, 0);
            check(small.truncated());
            contains(small.result(), "output truncated");
            ObjectDumper arr = new ObjectDumper(new String[0], 0, 10, 2);
            eq("int[5] {1, 2, ...}", arr.value(new int[] {1, 2, 3, 4, 5}, 0));
        });

        test("Env.detect outside the game", () -> {
            Env.Info i = Env.detect(true);
            eq("unknown", i.pzVersion);
            check(!i.zbDetected);
            check(i.vpClassesFound); // test double viewpoint.core.View
            eq("viewpoint.core.View", i.vpProbeClass);
        });

        test("Config from config.properties", () -> {
            Files.writeString(tmp.resolve("config.properties"), "debug=true\n");
            Config.load();
            check(Config.isDebug());
            eq("config.properties", Config.source());
            Files.writeString(tmp.resolve("config.properties"), "debug=false\n");
            Config.load();
            check(!Config.isDebug());
        });

        test("startup report prints the required lines", () -> {
            String out = captureStdout(() -> LuaBridge.startupReport("42.21.0", true, "\\Viewpoint"));
            contains(out, "[VPGeometryFix] Loaded\n");
            contains(out, "[VPGeometryFix] PZ version: 42.21.0 (from Lua)");
            contains(out, "[VPGeometryFix] Viewpoint detected: yes");
            contains(out, "[VPGeometryFix] ZombieBuddy detected: no");
            contains(out, "[VPGeometryFix] Debug mode: OFF");
            check(out.indexOf("Loaded") < out.indexOf("PZ version") && out.indexOf("PZ version") < out.indexOf("Debug mode"));
            check(Files.readString(tmp.resolve("VPGeometryFix.log")).contains("[VPGeometryFix] Loaded"));
            check(Files.readString(tmp.resolve("VPGeometryFix.log")).contains("detail viewpoint jar"));
            check(!out.contains("detail"));
            check(out.split("\n").length == 5);
        });

        test("status for the panel", () -> {
            String st = LuaBridge.status();
            contains(st, "Java-Teil: OK\nPZ: unknown\nViewpoint: erkannt\nZombieBuddy: nicht gefunden");
        });

        test("viewpoint first-person probe", () -> {
            eq("true", LuaBridge.viewpointFirstPerson());
            viewpoint.core.View.enabled = false;
            eq("false", LuaBridge.viewpointFirstPerson());
        });

        test("square inspection report", () -> {
            FakeSquare sq = new FakeSquare(10, 20, 1);
            sq.getObjects().add(new FakeSquare.FakeObject("roofs_01_12"));
            sq.getSpecialObjects().add(new FakeSquare.FakeObject("walls_exterior_house_01_0"));
            String out = captureStdout(() -> {
                LuaBridge.reportBegin("test", 10.7, 20.2, 1);
                LuaBridge.reportSquare(sq, "z+0");
                LuaBridge.reportSquare(null, "z+1");
                String path = LuaBridge.reportEnd();
                check(path != null);
                String rep = Files.readString(Path.of(path));
                contains(rep, "target: 10,20,1");
                contains(rep, "=== z+0 square 10,20,1");
                contains(rep, "getObjects() count=1");
                contains(rep, "getSpecialObjects() count=1");
                contains(rep, "FakeObject sprite=roofs_01_12 kind~ROOF");
                contains(rep, "FakeObject sprite=walls_exterior_house_01_0 kind~WALL");
                contains(rep, "z+1: square is null");
                contains(rep, "View.enabled=false");
                contains(rep, "FakeObject.alpha : float = 0.5");
                check(!rep.contains("unrelatedCounter"));
                contains(LuaBridge.lastSummary(), "TILE 10,20,1 Objects#0 FakeObject sprite=roofs_01_12 kind~ROOF");
            });
            contains(out, "[VPGeometryFix] TILE 10,20,1 Objects#0 FakeObject sprite=roofs_01_12 kind~ROOF");
            contains(out, "[VPGeometryFix] TILE 10,20,1 SpecialObjects#0 FakeObject sprite=walls_exterior_house_01_0 kind~WALL");
            contains(out, "[VPGeometryFix] TILE z+1 not loaded");
            contains(out, "[VPGeometryFix] report -> ");
            check(!out.contains("alpha"));
        });

        test("bridge never throws on bad input", () -> {
            LuaBridge.reportSquare(new Object(), "weird");
            LuaBridge.reportObject(null, "null");
            check(LuaBridge.reportEnd() != null);
            eq(null, LuaBridge.reportEnd());
            LuaBridge.dumpStatics("does.not.Exist");
        });

        test("inventory of a directory code source is refused cleanly", () -> {
            eq(null, ClassInventory.write(tmp, "viewpoint.", "t"));
        });

        System.out.println();
        System.out.println("passed: " + passed + ", failed: " + failures.size());
        for (String f : failures) System.out.println("FAIL " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void test(String name, Body b) {
        try {
            b.run();
            passed++;
            System.out.println("ok   " + name);
        } catch (Throwable t) {
            failures.add(name + ": " + t);
            System.out.println("FAIL " + name + ": " + t);
        }
    }

    private static String captureStdout(Body b) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            b.run();
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static void check(boolean c) {
        if (!c) throw new AssertionError("check failed");
    }

    static void eq(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void contains(String haystack, String needle) {
        if (!haystack.contains(needle)) throw new AssertionError("missing <" + needle + "> in:\n" + haystack);
    }
}
