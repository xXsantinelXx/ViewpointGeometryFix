package vpgeometryfix.doctor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * VPGF Doctor: runs OUTSIDE the game (double-click VPGF-Doctor.bat).
 * Read-only: finds Project Zomboid, ZombieBuddy, Viewpoint and this mod,
 * checks the installation, extracts the relevant console.txt lines and lists
 * Viewpoint's classes straight from its JAR. Writes one report file.
 * It never writes to the game, Workshop or Zomboid folders.
 *
 * Usage: java -jar VPGF-Doctor.jar [--steam-lib DIR]... [--zomboid DIR] [--out FILE]
 */
public final class Doctor {
    static final String VERSION = version();
    static final String MOD_ID = "ViewpointGeometryFix";
    static final String PZ_APP = "108600";
    static final Map<String, String> PINS = Map.of(
            "e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33", "projectzomboid.jar 42.21.0",
            "94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f", "Viewpoint 0.1.5a-hotfix",
            "6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6", "ZombieBuddy 2.3.2 (original)",
            "dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283", "ZombieBuddy 2.3.2 (B42.21 temporary fix)");
    static final String[] KEYWORDS = {"render", "cull", "visib", "mesh", "vertex", "model", "roof", "wall", "tile",
            "sprite", "chunk", "room", "floor", "shell", "far", "pick", "building", "geometry", "cutaway", "batch",
            "scene", "world", "occlu", "stair", "depth", "bake"};

    final List<Path> steamLibs = new ArrayList<>();
    Path zomboid;
    Path out;
    final StringBuilder report = new StringBuilder();
    final List<String> findings = new ArrayList<>();
    final StringBuilder classes = new StringBuilder();

    public static void main(String[] args) throws Exception {
        Doctor d = new Doctor();
        d.parse(args);
        Path written = d.run();
        System.out.println();
        System.out.println("Bericht geschrieben: " + written.toAbsolutePath());
    }

    void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--steam-lib" -> steamLibs.add(Path.of(args[++i]));
                case "--zomboid" -> zomboid = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (zomboid == null) zomboid = Path.of(System.getProperty("user.home"), "Zomboid");
        if (out == null) out = Path.of("VPGF-Report.txt");
        if (steamLibs.isEmpty()) steamLibs.addAll(discoverSteamLibraries());
    }

    Path run() throws IOException {
        line("VPGF Doctor " + VERSION + " - " + LocalDateTime.now().withNano(0));
        line("Nur lesend. Schickt diesen Bericht (ohne die Datei VPGF-Viewpoint-Classes.txt) an den Entwickler.");
        line("");
        section("1 Pfade");
        line("Zomboid-Benutzerordner: " + zomboid + (Files.isDirectory(zomboid) ? "" : "  (FEHLT)"));
        for (Path lib : steamLibs) line("Steam-Bibliothek: " + lib);
        if (steamLibs.isEmpty()) finding("Keine Steam-Bibliothek gefunden. Bat mit --steam-lib \"D:\\SteamLibrary\" starten.");

        Path pz = checkGame();
        checkZombieBuddy(pz);
        checkViewpoint();
        checkMod();
        checkConsole();

        section("ERGEBNIS");
        if (findings.isEmpty()) line("Keine Installationsprobleme gefunden.");
        for (String f : findings) line("* " + f);

        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        if (classes.length() > 0) {
            Path cls = out.resolveSibling("VPGF-Viewpoint-Classes.txt");
            Files.writeString(cls, classes.toString(), StandardCharsets.UTF_8);
        }
        return out;
    }

    // ------------------------------------------------------------------ game

    Path checkGame() {
        section("2 Project Zomboid");
        Path found = null;
        for (Path lib : steamLibs) {
            Path pz = lib.resolve("steamapps/common/ProjectZomboid");
            if (Files.isDirectory(pz)) {
                found = pz;
                break;
            }
        }
        if (found == null) {
            line("Installation nicht gefunden.");
            finding("Project Zomboid nicht gefunden - Pfad der Steam-Bibliothek mit --steam-lib angeben.");
            return null;
        }
        line("Installation: " + found);
        Path jar = found.resolve("projectzomboid.jar");
        line("projectzomboid.jar: " + describeJar(jar));
        for (String json : new String[] {"ProjectZomboid64.json", "ProjectZomboid64ShowConsole.json"}) {
            Path f = found.resolve(json);
            if (!Files.isRegularFile(f)) continue;
            String text = readText(f);
            boolean agent = text.toLowerCase(Locale.ROOT).contains("javaagent");
            line(json + ": javaagent " + (agent ? "eingetragen" : "NICHT eingetragen"));
            for (String l : text.split("\\R")) if (l.toLowerCase(Locale.ROOT).contains("javaagent")) line("    " + l.strip());
        }
        return found;
    }

    // ----------------------------------------------------------- zombiebuddy

    void checkZombieBuddy(Path pz) {
        section("3 ZombieBuddy");
        List<Path> jars = new ArrayList<>();
        if (pz != null) {
            Path direct = pz.resolve("ZombieBuddy.jar");
            if (Files.isRegularFile(direct)) jars.add(direct);
        }
        for (Path mod : workshopMods()) {
            ModInfo mi = ModInfo.read(mod.resolve("mod.info"));
            if (mi != null && "ZombieBuddy".equalsIgnoreCase(strip(mi.get("id")))) {
                line("Workshop-Mod: " + mod.getParent() + " (version " + mi.get("modversion") + ")");
                jars.addAll(findJars(mod.getParent()));
            }
        }
        if (jars.isEmpty()) {
            line("ZombieBuddy.jar nicht gefunden.");
            finding("ZombieBuddy nicht gefunden. Ohne ZombieBuddy laeuft nur der Lua-Teil der Mod.");
        }
        for (Path j : jars) line(j + ": " + describeJar(j) + implVersion(j));
    }

    // -------------------------------------------------------------- viewpoint

    void checkViewpoint() {
        section("4 Viewpoint");
        boolean any = false;
        for (Path mod : workshopMods()) {
            ModInfo mi = ModInfo.read(mod.resolve("mod.info"));
            if (mi == null) continue;
            String id = strip(mi.get("id"));
            if (id == null || !id.toLowerCase(Locale.ROOT).contains("viewpoint") || id.equalsIgnoreCase(MOD_ID)) continue;
            any = true;
            line("Mod: id=" + id + " name=" + mi.get("name") + " modversion=" + mi.get("modversion"));
            line("    Ordner: " + mod);
            line("    javaJarFile=" + mi.get("javaJarFile") + " javaPkgName=" + mi.get("javaPkgName")
                    + " require=" + mi.get("require"));
            for (Path jar : findJars(mod)) {
                line("    " + mod.relativize(jar) + ": " + describeJar(jar));
                if (id.equalsIgnoreCase("Viewpoint")) listClasses(jar);
            }
        }
        if (!any) {
            line("Keine Viewpoint-Mod im Workshop-Ordner gefunden.");
            finding("Viewpoint nicht gefunden (nur Workshop-Ordner durchsucht).");
        }
    }

    void listClasses(Path jar) {
        List<ClassFileInfo> infos = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : jf.stream().toList()) {
                if (!e.getName().endsWith(".class") || e.getName().contains("module-info")) continue;
                try (InputStream in = jf.getInputStream(e)) {
                    infos.add(ClassFileInfo.read(in.readAllBytes()));
                } catch (IOException | RuntimeException ex) {
                    classes.append("# unreadable ").append(e.getName()).append(": ").append(ex).append('\n');
                }
            }
        } catch (IOException e) {
            line("    Klassen nicht lesbar: " + e);
            return;
        }
        infos.sort((a, b) -> a.name.compareTo(b.name));
        classes.append("# Viewpoint-Klassen aus ").append(jar).append("\n# LOKAL - enthaelt Signaturen proprietaeren Codes, nicht veroeffentlichen\n\n");
        List<String> matches = new ArrayList<>();
        for (ClassFileInfo c : infos) {
            boolean hit = matches(c.name);
            classes.append(hit ? "* " : "  ").append(c.name).append(" extends ").append(c.superName)
                    .append(" (").append(c.fields.size()).append(" fields, ").append(c.methods.size()).append(" methods)\n");
            if (hit) {
                for (String f : c.fields) classes.append("      F ").append(f).append('\n');
                for (String m : c.methods) classes.append("      M ").append(m).append('\n');
                matches.add(c.name + " (" + c.methods.size() + " Methoden)");
            }
        }
        line("    Klassen gesamt: " + infos.size() + ", davon mit Geometrie-/Render-Stichwort: " + matches.size());
        for (String m : matches) line("      " + m);
        line("    Volle Signaturen: VPGF-Viewpoint-Classes.txt (nur lokal)");
    }

    static boolean matches(String className) {
        String lower = className.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) if (lower.contains(k)) return true;
        return false;
    }

    // -------------------------------------------------------------- this mod

    void checkMod() {
        section("5 ViewpointGeometryFix (diese Mod)");
        Path expected = zomboid.resolve("mods").resolve(MOD_ID).resolve("42").resolve("mod.info");
        List<Path> infos = new ArrayList<>();
        Path mods = zomboid.resolve("mods");
        if (Files.isDirectory(mods)) {
            try (Stream<Path> s = Files.walk(mods, 6)) {
                s.filter(p -> p.getFileName().toString().equals("mod.info")).forEach(p -> {
                    ModInfo mi = ModInfo.read(p);
                    if (mi != null && MOD_ID.equalsIgnoreCase(strip(mi.get("id")))) infos.add(p);
                });
            } catch (IOException e) {
                line("mods-Ordner nicht lesbar: " + e);
            }
        } else {
            line("Ordner fehlt: " + mods);
        }
        for (Path mod : workshopMods()) {
            ModInfo mi = ModInfo.read(mod.resolve("mod.info"));
            if (mi != null && MOD_ID.equalsIgnoreCase(strip(mi.get("id")))) infos.add(mod.resolve("mod.info"));
        }
        if (infos.isEmpty()) {
            line("Nicht installiert.");
            finding("Mod nicht gefunden. Erwartet: " + expected);
            return;
        }
        for (Path p : infos) {
            ModInfo mi = ModInfo.read(p);
            Path dir = p.getParent();
            line("mod.info: " + p + " (modversion " + mi.get("modversion") + ")");
            Path jar = dir.resolve(mi.get("javaJarFile") == null ? "-" : mi.get("javaJarFile"));
            Path lua = dir.resolve("media/lua/client/VPGeometryFix_Main.lua");
            line("    JAR: " + (Files.isRegularFile(jar) ? "vorhanden" : "FEHLT (" + jar + ")"));
            line("    Lua: " + (Files.isRegularFile(lua) ? "vorhanden" : "FEHLT (" + lua + ")"));
            line("    common-Ordner: " + (Files.isDirectory(dir.getParent().resolve("common")) ? "vorhanden" : "FEHLT"));
            if (!p.normalize().equals(expected.normalize()) && p.startsWith(mods)) {
                finding("Mod liegt an falscher Stelle: " + p + " - richtig waere " + expected);
            }
            if (!Files.isRegularFile(lua)) finding("Lua-Datei der Mod fehlt - ZIP neu entpacken.");
        }
        if (infos.size() > 1) finding("Mod mehrfach installiert - alle Kopien bis auf " + expected + " loeschen.");
    }

    // ------------------------------------------------------------ console.txt

    static final Pattern RELEVANT = Pattern.compile(
            "(?i)(vpgeometryfix|zombiebuddy|\\[zb|viewpoint|javaagent|VPGeometryFix_Main\\.lua|loading mod|mod failed)");
    static final Pattern ERROR = Pattern.compile("(?i)(exception|error|stack trace|attempted index|non-table)");

    void checkConsole() {
        section("6 console.txt (letzter Spielstart)");
        Path console = zomboid.resolve("console.txt");
        if (!Files.isRegularFile(console)) {
            line("Nicht vorhanden: " + console);
            finding("console.txt fehlt - Spiel einmal starten und bis ins Hauptmenue laufen lassen.");
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(console, StandardCharsets.ISO_8859_1);
            line("Datei: " + console + ", " + lines.size() + " Zeilen, geaendert "
                    + Instant.ofEpochMilli(Files.getLastModifiedTime(console).toMillis()));
        } catch (IOException e) {
            line("Nicht lesbar: " + e);
            return;
        }
        Set<String> picked = new LinkedHashSet<>();
        int errors = 0;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (RELEVANT.matcher(l).find()) {
                picked.add(i + 1 + ": " + l);
                // keep the error lines right after one of ours (stack traces)
                for (int k = i + 1; k < Math.min(lines.size(), i + 4); k++) {
                    if (ERROR.matcher(lines.get(k)).find()) picked.add(k + 1 + ": " + lines.get(k));
                }
            }
            if (ERROR.matcher(l).find()) errors++;
        }
        boolean luaLoaded = lines.stream().anyMatch(l -> l.contains("[VPGeometryFix] Lua loaded"));
        boolean startBlock = lines.stream().anyMatch(l -> l.contains("[VPGeometryFix] Loaded"));
        boolean javaLoaded = lines.stream().anyMatch(l -> l.contains("[VPGeometryFix] Viewpoint detected:") && !l.contains("Java state unknown"));
        boolean zbSeen = lines.stream().anyMatch(l -> l.toLowerCase(Locale.ROOT).contains("zombiebuddy") || l.contains("[ZB"));
        boolean modListed = lines.stream().anyMatch(l -> l.contains(MOD_ID));
        line("Mod-Lua geladen: " + yes(luaLoaded) + ", Startblock: " + yes(startBlock)
                + ", Java-Teil: " + yes(javaLoaded) + ", ZombieBuddy-Zeilen: " + yes(zbSeen)
                + ", Mod-ID erwaehnt: " + yes(modListed) + ", Fehlerzeilen gesamt: " + errors);
        line("");
        List<String> list = new ArrayList<>(picked);
        int from = Math.max(0, list.size() - 120);
        if (from > 0) line("(" + from + " aeltere relevante Zeilen ausgelassen)");
        for (String l : list.subList(from, list.size())) line(l.length() > 400 ? l.substring(0, 400) + " ..." : l);

        if (!modListed) {
            finding("console.txt erwaehnt ViewpointGeometryFix nicht: das Spiel hat die Mod NICHT geladen. "
                    + "Im Hauptmenue unter Mods aktivieren (B42: auch in der Mod-Auswahl des Spielstands) und Ordner pruefen (Abschnitt 5).");
        } else if (!luaLoaded) {
            finding("Mod wird erwaehnt, aber die Lua-Datei lief nicht (keine Zeile 'Lua loaded') - siehe Fehlerzeilen oben.");
        }
        if (!zbSeen) finding("Keine ZombieBuddy-Zeilen in console.txt: der ZombieBuddy-Java-Agent ist nicht aktiv (Abschnitt 2/3).");
        if (luaLoaded && !javaLoaded) finding("Lua laeuft, aber der Java-Teil nicht - ZombieBuddy-Freigabe fuer ViewpointGeometryFix pruefen.");
    }

    // ---------------------------------------------------------------- helpers

    List<Path> workshopMods() {
        List<Path> out = new ArrayList<>();
        for (Path lib : steamLibs) {
            Path ws = lib.resolve("steamapps/workshop/content").resolve(PZ_APP);
            if (!Files.isDirectory(ws)) continue;
            try (Stream<Path> s = Files.walk(ws, 6)) {
                s.filter(p -> p.getFileName().toString().equals("mod.info")).forEach(p -> out.add(p.getParent()));
            } catch (IOException ignored) {
                // unreadable workshop folder: skip
            }
        }
        return out;
    }

    static List<Path> findJars(Path dir) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir, 8)) {
            s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // nothing
        }
        return out;
    }

    static String describeJar(Path jar) {
        if (!Files.isRegularFile(jar)) return "FEHLT";
        String sha = sha256(jar);
        String label = PINS.get(sha);
        return "sha256 " + (sha == null ? "?" : sha.substring(0, 16) + "...") + " -> "
                + (label == null ? "kein auditierter Build" : label);
    }

    static String implVersion(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var mf = jf.getManifest();
            String v = mf == null ? null : mf.getMainAttributes().getValue("Implementation-Version");
            return v == null ? "" : " (Implementation-Version " + v + ")";
        } catch (IOException e) {
            return "";
        }
    }

    static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) != -1; ) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }

    static String readText(Path f) {
        try {
            return Files.readString(f, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            return "";
        }
    }

    static String strip(String id) {
        return id == null ? null : id.replace("\\", "").strip();
    }

    static String yes(boolean b) {
        return b ? "ja" : "NEIN";
    }

    /** Steam roots on common Windows locations plus libraries listed in libraryfolders.vdf. */
    static List<Path> discoverSteamLibraries() {
        Set<Path> roots = new LinkedHashSet<>();
        List<String> bases = new ArrayList<>(List.of("C:/Program Files (x86)/Steam", "C:/Program Files/Steam"));
        for (char d = 'C'; d <= 'Z'; d++) {
            bases.add(d + ":/SteamLibrary");
            bases.add(d + ":/Steam");
            bases.add(d + ":/Games/Steam");
            bases.add(d + ":/Games/SteamLibrary");
            bases.add(d + ":/Program Files (x86)/Steam");
        }
        for (String b : bases) {
            Path p = Path.of(b);
            if (Files.isDirectory(p.resolve("steamapps"))) roots.add(p);
        }
        Pattern path = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");
        for (Path r : new ArrayList<>(roots)) {
            Path vdf = r.resolve("steamapps/libraryfolders.vdf");
            if (!Files.isRegularFile(vdf)) continue;
            Matcher m = path.matcher(readText(vdf));
            while (m.find()) {
                Path lib = Path.of(m.group(1).replace("\\\\", "\\"));
                if (Files.isDirectory(lib.resolve("steamapps"))) roots.add(lib);
            }
        }
        return new ArrayList<>(roots);
    }

    void section(String title) {
        report.append("\n=== ").append(title).append(" ===\n");
        System.out.println("=== " + title);
    }

    void line(String s) {
        report.append(s).append('\n');
    }

    void finding(String s) {
        findings.add(s);
    }

    static String version() {
        String v = Doctor.class.getPackage() == null ? null : Doctor.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    /** Tiny mod.info reader (key=value). */
    static final class ModInfo {
        final Map<String, String> values = new LinkedHashMap<>();

        String get(String k) {
            return values.get(k);
        }

        static ModInfo read(Path f) {
            if (!Files.isRegularFile(f)) return null;
            ModInfo mi = new ModInfo();
            for (String raw : readText(f).split("\\R")) {
                String l = raw.strip();
                int eq = l.indexOf('=');
                if (l.startsWith("#") || eq <= 0) continue;
                mi.values.putIfAbsent(l.substring(0, eq).strip(), l.substring(eq + 1).strip());
            }
            return mi;
        }
    }
}
