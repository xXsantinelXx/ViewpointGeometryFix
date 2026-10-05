package pzmod.vpgeometryfix;

/**
 * ZombieBuddy calls main() when it loads this mod's JAR, which happens
 * before the game is loaded. Only facts that are already available are
 * logged here; the full startup block comes from the Lua side at
 * OnGameStart, when Core and the mod list can be read.
 *
 * This class registers no patches. Loading this mod changes no game
 * behaviour at all — that is the point of the diagnosis-only release.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Log.line("Java mod loaded (version " + Api.VERSION + ", diagnosis only, no patches)");
        Log.line("ZombieBuddy detected: " + Environment.zombieBuddySummary());
        Log.line("Viewpoint detected: " + Environment.viewpointSummary());
        Log.debug("debug mode was on before the game started "
                + "(system property vpgeometryfix.debug)");
    }
}
