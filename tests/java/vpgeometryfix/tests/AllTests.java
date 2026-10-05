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

        test("ModInfo.findAbove with mod.info only in common/", () -> {
            Path root = tmp.resolve("VP2");
            Path jarDir = root.resolve("42/media/java/client");
            Files.createDirectories(jarDir);
            Files.createDirectories(root.resolve("common"));
            Files.writeString(root.resolve("common/mod.info"), "id=Viewpoint\nmodversion=0.1.5a-hotfix\n");
            Path jar = jarDir.resolve("Viewpoint.jar");
            Files.writeString(jar, "x");
            eq("Viewpoint", ModInfo.findAbove(jar, 6).get("id"));
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
                contains(rep, "FakeObject sprite=roofs_01_12 kind~ROOF vpGeom=2(Polygon,Box) rise=0.25");
                contains(rep, "viewpoint geometryFor: 2 shape(s)");
                contains(rep, "Polygon.points : float[] = float[4] {0.0, 0.0, 1.0, 1.0}");
                contains(rep, "FakeObject sprite=walls_exterior_house_01_0 kind~WALL vpGeom=0 rise=0.25");
                contains(rep, "FakeObject sprite=walls_exterior_house_01_0 kind~WALL");
                contains(rep, "z+1: square is null");
                contains(rep, "View.enabled=false");
                contains(rep, "FakeObject.alpha : float = 0.5");
                check(!rep.contains("unrelatedCounter"));
                contains(LuaBridge.lastSummary(), "TILE 10,20,1 Objects#0 FakeObject sprite=roofs_01_12 kind~ROOF vpGeom=2(Polygon,Box)");
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

        test("roof fix B: sibling mapping", () -> {
            eq("roofs_01_12", vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_02_12"));
            eq("roofs_01_0", vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_05_0"));
            eq("roofs_30_01_7", vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_30_10_7"));
            eq("roofs_30_01_7", vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_30_02_7"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_01_3"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_06_3"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_30_11_3"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite("roofs_accents_01_0"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite("walls_exterior_house_01_0"));
            eq(null, vpgeometryfix.fix.RoofFallback.siblingSprite(null));
        });

        test("roof fix B: replaces only empty results of covered roofs", () -> {
            var rf = vpgeometryfix.fix.RoofFallback.class;
            vpgeometryfix.fix.RoofFallback.setEnabled(false);
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_02_12"), new java.util.ArrayList<>()));
            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            long before = vpgeometryfix.fix.RoofFallback.replaced();
            var r = vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_02_12"), new java.util.ArrayList<>());
            check(r != null && r.size() == 2);
            eq(before + 1, vpgeometryfix.fix.RoofFallback.replaced());
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_02_12"), java.util.List.of("kept")));
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("walls_01_0"), new java.util.ArrayList<>()));
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_06_0"), new java.util.ArrayList<>()));
            eq(null, vpgeometryfix.fix.RoofFallback.apply(null, null));
            check(vpgeometryfix.fix.RoofFallback.calls() >= 6);
            long empty = vpgeometryfix.fix.RoofFallback.roofEmpty();
            long shaped = vpgeometryfix.fix.RoofFallback.roofShaped();
            vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_01_77"), new java.util.ArrayList<>());
            vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_01_0"), java.util.List.of("s"));
            vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("walls_01_0"), new java.util.ArrayList<>());
            eq(empty + 1, vpgeometryfix.fix.RoofFallback.roofEmpty());
            eq(shaped + 1, vpgeometryfix.fix.RoofFallback.roofShaped());
            eq("Box{height=2.5}; Polygon{points=[0.0, 0.0, 1.0, 1.0]}", vpgeometryfix.fix.RoofFallback.describe(java.util.List.of(
                    new viewpoint.world.TileMeshes.Box(), new viewpoint.world.TileMeshes.Polygon(new float[] {0, 0, 1, 1}))));
            check(rf != null);
        });

        test("roof fix status, toggle and TILE marker", () -> {
            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            contains(LuaBridge.roofFixStatus(), "Dach-Fix (Code): AN, Patch aktiv");
            contains(LuaBridge.roofFixStatus(), "  Daecher: mit Form ");
            contains(LuaBridge.roofFixStatus(), "Dach-Fix A (Datei): nicht installiert");
            contains(LuaBridge.status(), "Dach-Fix (Code): AN");
            String out = captureStdout(() -> LuaBridge.setRoofFix(false));
            contains(out, "roof fix: OFF");
            check(!LuaBridge.isRoofFix());
            Config.load();
            check(!Config.getBool("roofFixB", true)); // persisted
            LuaBridge.setRoofFix(true);
            FakeSquare sq = new FakeSquare(1, 1, 1);
            sq.getObjects().add(new FakeSquare.FakeObject("roofs_03_5"));
            captureStdout(() -> {
                LuaBridge.reportBegin("t", 1, 1, 1);
                LuaBridge.reportSquare(sq, "z+0");
                LuaBridge.reportEnd();
            });
            // without ZombieBuddy the advice is not woven in, so vpGeom stays 0 here; in game it shows the sibling shapes
            contains(LuaBridge.lastSummary(), "sprite=roofs_03_5 kind~ROOF vpGeom=0 fixB=an<-roofs_01_5");
            contains(LuaBridge.lastSummary(), "rise=0.25 model=false");
        });

        test("MeshProbe statics, projection probe and cache snapshot", () -> {
            String st = String.join("\n", vpgeometryfix.diag.MeshProbe.statics());
            contains(st, "TileMeshes: GAME=game");
            contains(st, "MeshBuilder: TO_ISO_CAMERA=(0.6120 0.5000 0.6120) UV_INSET=0.0010");
            contains(st, "TileMesh: STRIDE=5");
            String fp = String.join("\n", vpgeometryfix.diag.MeshProbe.frameProbe());
            contains(fp, "frame(1.00,0.00,0.00) = x 64.0000, y 32.0000");
            contains(fp, "frame(0.00,1.00,0.00) = x 0.0000, y -78.3800");
            viewpoint.world.TileMeshes.fillCacheForTests();
            List<String> c = vpgeometryfix.diag.MeshProbe.cacheSnapshot("roofs_");
            contains(c.get(0), "mesh cache: 3 entries, key fake.FakeSquare$FakeSprite, value viewpoint.world.TileMeshes$Held, 2 with prefix 'roofs_', of these EMPTY/null 1");
            String all = String.join("\n", c);
            contains(all, "roofs_01_0 - verts=2 floats=10 bounds~(-1.000 0.000 -1.000)..(1.000 0.050 1.000)");
            contains(all, "roofs_01_11 - EMPTY verts=0 floats=0");
            check(!all.contains("walls_01_0"));
        });

        test("GeometrySource: own, assigned, Viewpoint-only, none", () -> {
            String r = String.join("\n", vpgeometryfix.diag.GeometrySource.report());
            contains(r, "TileGeometryManager ok, mod ids [game], per-tile getter getGeometry; TileDepthTextureAssignmentManager ok");
            contains(r, "roofs_01_0 => OWN(game) | game own=2 | vp=2 ");
            contains(r, "roofs_02_14 => ASSIGNED(game->roofs_01_4) | game assigned=roofs_01_4 vp(assigned)=2 same | vp=2 ");
            contains(r, "roofs_01_14 => VP-ONLY | vp=2 Polygon{points=[0.0, 0.0, 1.0, 1.0]}; Box{height=2.5}");
            contains(r, "roofs_03_38 => NONE | vp=0");
            // index -> col/row with 8 columns: only row 0 of roofs_01 has own data in the double
            contains(r, "roofs_01_71 => VP-ONLY");
            check(!r.contains("checks incomplete"));
            contains(r, "unchecked 0");
        });

        test("roof fix B: replaced sprites are listed for the source report; shapes dedupe", () -> {
            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            String out = captureStdout(() -> {
                vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_04_20"), new java.util.ArrayList<>());
                vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_01_40"), java.util.List.of(new viewpoint.world.TileMeshes.Box()));
                vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_01_41"), java.util.List.of(new viewpoint.world.TileMeshes.Box()));
            });
            check(vpgeometryfix.fix.RoofFallback.seenNames().contains("roofs_04_20"));
            String log = Files.readString(tmp.resolve("VPGeometryFix.log"));
            contains(log, "roof seen: roofs_04_20 - no shape, replaced by fix B <- roofs_01_20");
            contains(log, "roof seen: roofs_01_40 - has 1 shape(s): Box{height=2.5}");
            contains(log, "roof seen: roofs_01_41 - has 1 shape(s): same as roofs_01_40");
            check(out.isEmpty()); // file log only, nothing on the console
        });

        test("roof data report via the bridge", () -> {
            String out = captureStdout(() -> {
                String s = LuaBridge.roofData();
                contains(s, "sources: own ");
                contains(s, "roofdata_");
            });
            contains(out, "[VPGeometryFix] roof data: sources: own ");
            contains(out, "[VPGeometryFix] report -> ");
        });

        test("roof shapes: back-half map, mirror, slab trim, thin trim card", () -> {
            var RS = vpgeometryfix.fix.RoofShapes.class;
            check(RS != null);
            Object[] b = vpgeometryfix.fix.RoofShapes.backOf("roofs_02_9");
            eq("roofs_02_1", b[0]);
            eq(0, b[1]);
            eq("roofs_burnt_01_3", vpgeometryfix.fix.RoofShapes.backOf("roofs_burnt_01_11")[0]);
            eq(1, vpgeometryfix.fix.RoofShapes.backOf("roofs_01_13")[1]);
            eq(null, vpgeometryfix.fix.RoofShapes.backOf("roofs_30_01_9"));
            eq(null, vpgeometryfix.fix.RoofShapes.backOf("roofs_accents_01_9"));
            eq(null, vpgeometryfix.fix.RoofShapes.backOf("roofs_01_14"));
            eq("8=2z,9=1z,10=0z,11=3x,12=4x,13=5x", vpgeometryfix.fix.RoofShapes.setBackMap("8=2z,9=1z,10=0z,11=3x,12=4x,13=5x"));
            eq("roofs_01_2", vpgeometryfix.fix.RoofShapes.backOf("roofs_01_8")[0]);
            eq("auto", vpgeometryfix.fix.RoofShapes.setBackMap("nonsense"));
            // automatic order from picture heights: strip of 8 lowest, 10 highest -> 8=0,9=1,10=2; west 11 lowest -> 11=5
            vpgeometryfix.fix.RoofMirrorTestAccess.setArtCentre(n -> n.endsWith("_8") ? 200f : n.endsWith("_9") ? 150f
                    : n.endsWith("_10") ? 100f : n.endsWith("_11") ? 210f : n.endsWith("_12") ? 160f : n.endsWith("_13") ? 110f : null);
            eq("roofs_03_0", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_8")[0]);
            eq("roofs_03_5", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_11")[0]);
            eq("roofs_03_3", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_13")[0]);
            vpgeometryfix.fix.RoofMirrorTestAccess.setArtCentre(n -> n.endsWith("_8") ? 100f : n.endsWith("_9") ? 150f
                    : n.endsWith("_10") ? 200f : null); // reversed sheet order; west group without data -> default
            eq("roofs_03_2", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_8")[0]);
            eq("roofs_03_0", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_10")[0]);
            eq("roofs_03_3", vpgeometryfix.fix.RoofShapes.backOf("roofs_03_11")[0]);
            vpgeometryfix.fix.RoofMirrorTestAccess.setArtCentre(n -> null);
            // tiles assigned to a back tile count as that tile (roofs_01_67 = roofs_01_11 in the game data)
            vpgeometryfix.fix.RoofMirrorTestAccess.setAssignment(n -> n.equals("roofs_02_67") ? "roofs_01_11" : null);
            eq("roofs_02_3", vpgeometryfix.fix.RoofShapes.backOf("roofs_02_67")[0]);
            eq(null, vpgeometryfix.fix.RoofShapes.backOf("roofs_02_66"));
            vpgeometryfix.fix.RoofMirrorTestAccess.setAssignment(n -> null);

            fake.FakeBox slabX = fake.FakeBox.of(new float[] {0, 0.3982f, 0}, new float[] {39.2394f, 0, 0},
                    new float[] {-1, 0, -1}, new float[] {1, 0.05f, 1});
            fake.FakeBox c = (fake.FakeBox) vpgeometryfix.fix.RoofShapes.clipSlab(slabX);
            eq(-0.5f, c.min.x);
            eq(0.5f, c.max.x);
            check(Math.abs(c.max.z - 0.6455f) < 0.001f && Math.abs(c.min.z + 0.6455f) < 0.001f);
            eq(-1f, slabX.min.x); // original untouched
            check(c.rotate != slabX.rotate); // deep copy
            fake.FakeBox slabZ = fake.FakeBox.of(new float[] {0, 2.0312f, 0}, new float[] {0, 0, -39.4506f},
                    new float[] {-1, 0, -1}, new float[] {1, 0.05f, 1});
            fake.FakeBox cz = (fake.FakeBox) vpgeometryfix.fix.RoofShapes.clipSlab(slabZ);
            eq(-0.5f, cz.min.z);
            check(Math.abs(cz.max.x - 0.6481f) < 0.001f);
            eq(null, vpgeometryfix.fix.RoofShapes.clipSlab(fake.FakeBox.of(new float[] {0, 0, 0}, new float[] {22.59f, 0, 0},
                    new float[] {-0.5f, 0, -0.54f}, new float[] {0.5f, 0.065f, 0.54f})));

            var m = vpgeometryfix.fix.RoofShapes.mirror(java.util.List.of(c), 0);
            fake.FakeBox mb = (fake.FakeBox) m.get(0);
            check(Math.abs(mb.rotate.x + 39.2394f) < 1e-4);
            eq(0.3982f, mb.translate.y);
            check(Math.abs(mb.min.z + 0.6455f) < 0.001f && Math.abs(mb.max.z - 0.6455f) < 0.001f);
            var mz = vpgeometryfix.fix.RoofShapes.mirror(java.util.List.of(cz), 1);
            check(Math.abs(((fake.FakeBox) mz.get(0)).rotate.z - 39.4506f) < 1e-4);
            eq(null, vpgeometryfix.fix.RoofShapes.mirror(java.util.List.of(cz), 0)); // wrong axis: nothing invented
            eq(null, vpgeometryfix.fix.RoofShapes.mirror(java.util.List.of(new viewpoint.world.TileMeshes.Box()), 0));

            fake.FakeBox card = fake.FakeBox.of(new float[] {0, 0, 0}, new float[] {0, 0, 0},
                    new float[] {-1.5f, -1, -0.5f}, new float[] {0.75f, 2.45f, -0.2f});
            fake.FakeBox tc = (fake.FakeBox) vpgeometryfix.fix.RoofShapes.thinCard(card);
            eq(-0.52f, tc.min.z);
            eq(-0.48f, tc.max.z);
            eq(-1.5f, tc.min.x);
            fake.FakeBox west = fake.FakeBox.of(new float[] {0, 0, 0}, new float[] {0, 0, 0},
                    new float[] {-0.5f, -1, -1.5f}, new float[] {-0.2f, 2.45f, 0.75f});
            eq(-0.48f, ((fake.FakeBox) vpgeometryfix.fix.RoofShapes.thinCard(west)).max.x);
            eq(null, vpgeometryfix.fix.RoofShapes.thinCard(slabX));
        });

        test("roof fix in the geometryFor advice: trim, card, back half", () -> {
            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            vpgeometryfix.fix.RoofFallback.setParts(true, true, true);
            var front = viewpoint.world.TileMeshes.class;
            check(front != null);
            // a steep slab from Viewpoint: replaced by a trimmed copy
            java.util.List<Object> orig = new java.util.ArrayList<>();
            orig.add(fake.FakeBox.of(new float[] {0, 0.3982f, 0}, new float[] {39.2394f, 0, 0},
                    new float[] {-1, 0, -1}, new float[] {1, 0.05f, 1}));
            var r = vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_05_0"), orig);
            check(r != null && r.get(0) != orig.get(0));
            eq(0.5f, ((fake.FakeBox) r.get(0)).max.x);
            eq(1f, ((fake.FakeBox) orig.get(0)).max.x);
            // trim card
            java.util.List<Object> acc = new java.util.ArrayList<>();
            acc.add(fake.FakeBox.of(new float[] {0, 0, 0}, new float[] {0, 0, 0},
                    new float[] {-1.5f, -1, -0.5f}, new float[] {0.75f, 2.45f, -0.2f}));
            var ra = vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_accents_01_4"), acc);
            eq(-0.48f, ((fake.FakeBox) ra.get(0)).max.z);
            // back half roofs_05_8 (empty) <- mirrored, trimmed roofs_05_0
            var rb = vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_05_8"), new java.util.ArrayList<>());
            check(rb != null && rb.size() == 1);
            fake.FakeBox bb = (fake.FakeBox) rb.get(0);
            check(Math.abs(bb.rotate.x + 39.2394f) < 1e-4 && bb.max.x == 0.5f);
            // master switch off: nothing changes
            vpgeometryfix.fix.RoofFallback.setEnabled(false);
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_05_0"), orig));
            eq(null, vpgeometryfix.fix.RoofFallback.apply(new FakeSquare.FakeSprite("roofs_05_9"), new java.util.ArrayList<>()));
            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            String log = Files.readString(tmp.resolve("VPGeometryFix.log"));
            contains(log, "roof fix: roofs_05_0 slab trimmed to its tile");
            contains(log, "roof fix: roofs_accents_01_4 trim card moved into the gable plane");
            contains(log, "roof seen: roofs_05_8 - no shape, back half <- mirrored roofs_05_0");
        });

        test("roof back-half mesh: mirrored front mesh and texture swap", () -> {
            float[] d = {-0.5f, 0f, 0.5f, 7, 8, 0.5f, 0f, 0.5f, 9, 10, 0f, 0.8f, -0.5f, 11, 12};
            float[] m = vpgeometryfix.fix.RoofMirrorTestAccess.mirrorData(d, 3, 5, 0);
            // winding reversed (0,2,1) and z negated, UV floats kept
            check(java.util.Arrays.equals(m, new float[] {-0.5f, 0f, -0.5f, 7, 8, 0f, 0.8f, 0.5f, 11, 12, 0.5f, 0f, -0.5f, 9, 10}));
            eq(null, vpgeometryfix.fix.RoofMirrorTestAccess.mirrorData(d, 2, 5, 0)); // not whole triangles
            eq(null, vpgeometryfix.fix.RoofMirrorTestAccess.mirrorData(new float[] {50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 3, 5, 0));
            float[] withNormals = {-0.5f, 0, 0.4f, 0, 0.8f, 0.6f, 0.5f, 0, 0.4f, 0, 0.8f, 0.6f, 0, 1, -0.4f, 0, 0.8f, 0.6f};
            float[] mn = vpgeometryfix.fix.RoofMirrorTestAccess.mirrorData(withNormals, 3, 6, 0);
            eq(-0.6f, mn[5]); // normal z mirrored too
            eq(-0.4f, mn[2]);

            float[] corner = {0, 0, 0, 7, 8, 1, 0, 0, 9, 10, 0, 0.8f, 1, 11, 12}; // corner origin (0..1): refused
            eq(null, vpgeometryfix.fix.RoofMirrorTestAccess.mirrorData(corner, 3, 5, 0));

            vpgeometryfix.fix.RoofFallback.setEnabled(true);
            vpgeometryfix.fix.RoofMirror.setEnabled(true);
            zombie.core.textures.Texture back = zombie.core.textures.Texture.trygetTexture("roofs_05_8");
            // without the shape path having filled roofs_05_9, the mesh path does nothing
            eq(null, vpgeometryfix.fix.RoofMirror.onCreate(new FakeSquare.FakeSprite("roofs_05_9"), back, null, null));
            check(vpgeometryfix.fix.RoofFallback.isMirroredBack("roofs_05_8")); // filled in the previous test
            Object mesh = vpgeometryfix.fix.RoofMirror.onCreate(new FakeSquare.FakeSprite("roofs_05_8"), back, null, null);
            check(mesh instanceof viewpoint.world.TileMesh);
            check(viewpoint.world.TileMeshes.CREATED.contains("roofs_05_0"));
            Object[] sw = vpgeometryfix.fix.RoofMirror.swap(mesh, back.getTextureId(), new float[] {1, 2});
            check(sw != null);
            eq("page2", ((zombie.core.textures.TextureID) sw[0]).page);
            check(java.util.Arrays.equals((float[]) sw[1], new float[] {3, 4})); // the front texture's map
            eq(null, vpgeometryfix.fix.RoofMirror.swap(new Object(), back.getTextureId(), new float[] {1, 2}));
            eq(null, vpgeometryfix.fix.RoofMirror.onCreate(new FakeSquare.FakeSprite("roofs_01_0"), back, null, null));
            String log = Files.readString(tmp.resolve("VPGeometryFix.log"));
            contains(log, "roof back half: roofs_05_8 <- mirrored roofs_05_0 (z, 3 verts)");
            contains(log, "roof art: roofs_05_8 128x2 at 0,120 of 128x256");
            check(vpgeometryfix.fix.RoofMirror.built() >= 1 && vpgeometryfix.fix.RoofMirror.swapped() >= 1);
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
