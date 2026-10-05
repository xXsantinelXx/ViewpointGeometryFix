package pzmod.vpgeometryfix;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import zombie.ZomboidFileSystem;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.sprite.IsoSprite;

/**
 * Offline checks for the diagnosis code, run against the stubs in
 * tests/stubs. They exercise our own logic only: no game, no renderer, no
 * ZombieBuddy agent is involved, and nothing here proves in-game behaviour.
 */
public final class ProbeTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path sandbox = Files.createTempDirectory("vpgfix-test");
        ZomboidFileSystem.cacheDir = sandbox.toString();

        missingSquareIsReportedNotGuessed();
        roofObjectDetailsAreInTheReport();
        oneFailingGetterDoesNotLoseTheReport();
        columnCoversEveryRequestedLevel();
        reportsLandInOurOwnFolder();
        hashIsTheRealSha256(sandbox);
        startupBlockHasTheRequiredLines();

        System.out.println(checks + " checks, " + failures + " failure(s)");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void missingSquareIsReportedNotGuessed() {
        world(new IsoCell());
        String report = TileProbe.describeSquare(10, 20, 0);
        contains("missing square", report, "square 10,20,0");
        contains("missing square", report, "not loaded");
    }

    private static void roofObjectDetailsAreInTheReport() {
        IsoCell cell = new IsoCell();
        IsoGridSquare square = new IsoGridSquare();
        square.slopedRoof = true;
        square.outside = false;
        PropertyContainer squareProps = new PropertyContainer();
        squareProps.flags.add("HasSlopedRoof");
        square.properties = squareProps;

        IsoObject roof = new IsoObject();
        roof.spriteName = "roofs_01_12";
        roof.tileName = "roofs_01_12";
        roof.alpha = 0.5f;
        roof.targetAlpha = 1.0f;
        roof.renderYOffset = -32.0f;
        IsoSprite sprite = new IsoSprite();
        sprite.name = "roofs_01_12";
        PropertyContainer spriteProps = new PropertyContainer();
        spriteProps.flags.add("WestRoofT");
        spriteProps.values.put("RoofDirection", "W");
        sprite.properties = spriteProps;
        roof.sprite = sprite;
        square.objects.add(roof);

        cell.put(1000, 2000, 1, square);
        world(cell);

        String report = TileProbe.describeSquare(1000, 2000, 1);
        contains("roof square", report, "slopedRoof=true");
        contains("roof square", report, "objects=1");
        contains("roof sprite", report, "sprite=roofs_01_12");
        contains("roof alpha", report, "alpha=0.5");
        contains("roof offset", report, "renderYOffset=-32.0");
        contains("roof flag", report, "WestRoofT");
        contains("roof property", report, "RoofDirection=W");
    }

    private static void oneFailingGetterDoesNotLoseTheReport() {
        IsoCell cell = new IsoCell();
        IsoGridSquare square = new IsoGridSquare();
        square.failOnProperties = true;
        square.objects.add(new IsoObject());
        cell.put(5, 6, 0, square);
        world(cell);

        String report = TileProbe.describeSquare(5, 6, 0);
        contains("failing getter", report, "<error IllegalStateException>");
        contains("failing getter", report, "objects=1");
    }

    private static void columnCoversEveryRequestedLevel() {
        world(new IsoCell());
        String report = Api.probeColumn(7, 8, 2, 0);
        for (int z = 0; z <= 2; z++) {
            contains("column level " + z, report, "square 7,8," + z);
        }
    }

    private static void reportsLandInOurOwnFolder() {
        String path = Reports.write("probe", "body");
        check("report path is inside the VPGeometryFix folder",
                path.contains("VPGeometryFix"));
        check("report file exists", Files.exists(Path.of(path)));
    }

    private static void hashIsTheRealSha256(Path sandbox) throws Exception {
        Path file = sandbox.resolve("empty.bin");
        Files.write(file, new byte[0]);
        // SHA-256 of the empty byte sequence.
        check("sha256 of an empty file",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                        .equals(Environment.sha256(file)));
    }

    private static void startupBlockHasTheRequiredLines() {
        String block = Api.startupReport();
        for (String required : List.of(
                "[VPGeometryFix] Loaded",
                "[VPGeometryFix] PZ version:",
                "[VPGeometryFix] Viewpoint detected:",
                "[VPGeometryFix] ZombieBuddy detected:",
                "[VPGeometryFix] Debug mode:")) {
            contains("startup block", block, required);
        }
    }

    private static void world(IsoCell cell) {
        IsoWorld world = new IsoWorld();
        world.cell = cell;
        IsoWorld.instance = world;
    }

    private static void contains(String what, String haystack, String needle) {
        check(what + " contains \"" + needle + "\"", haystack.contains(needle));
    }

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }
}
