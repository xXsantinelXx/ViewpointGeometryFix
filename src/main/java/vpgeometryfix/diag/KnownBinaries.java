package vpgeometryfix.diag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * SHA-256 identification of the binaries we diagnose against.
 *
 * Source of the hashes: {@code viewpoint/pins.json} in the public repository
 * github.com/kilroy94/project-viewpoint-vr (audit of the installed Workshop
 * files, 2026-09/10). These are third-party measurements; a match is strong
 * evidence of the exact build, a mismatch only means "not that audited build"
 * (e.g. a newer Workshop update), not an error.
 */
public final class KnownBinaries {
    public static final Map<String, String> PINS = Map.of(
            "e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33",
            "projectzomboid.jar 42.21.0",
            "94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f",
            "Viewpoint 0.1.5a-hotfix",
            "6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6",
            "ZombieBuddy 2.3.2 (original)",
            "dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283",
            "ZombieBuddy 2.3.2 (B42.21 temporary fix)");

    private KnownBinaries() {}

    public static String identify(String sha256) {
        if (sha256 == null) return "unknown";
        String label = PINS.get(sha256);
        return label == null ? "not an audited build" : label;
    }

    public static String sha256(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) != -1; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
