package zombie.iso;

import java.util.HashMap;
import java.util.Map;

/** Stub of zombie.iso.IsoCell. */
public class IsoCell {

    public final Map<String, IsoGridSquare> squares = new HashMap<>();

    public void put(int x, int y, int z, IsoGridSquare square) {
        squares.put(x + "/" + y + "/" + z, square);
    }

    public IsoGridSquare getGridSquare(int x, int y, int z) {
        return squares.get(x + "/" + y + "/" + z);
    }
}
