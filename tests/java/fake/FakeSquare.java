package fake;

import java.util.ArrayList;
import java.util.List;

/** Test double for zombie.iso.IsoGridSquare: only the accessors the inspector reads. */
public final class FakeSquare {
    public final int x, y, z;
    private final ArrayList<Object> objects = new ArrayList<>();
    private final FakeList special = new FakeList();
    public FakeSquare self = this; // cycle

    public FakeSquare(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public List<Object> getObjects() { return objects; }
    public FakeList getSpecialObjects() { return special; }

    /** Mimics PZArrayList: size()/get(int) but not a java.util.List. */
    public static final class FakeList {
        final ArrayList<Object> items = new ArrayList<>();
        public int size() { return items.size(); }
        public Object get(int i) { return items.get(i); }
        public void add(Object o) { items.add(o); }
    }

    public static final class FakeSprite {
        public String name;
        public int[] flags = {1, 2, 3};
        public FakeSprite(String n) { name = n; }
        public String getName() { return name; }
    }

    public static final class FakeObject {
        public FakeSprite sprite;
        public float alpha = 0.5f;
        public FakeObject(String spriteName) { sprite = new FakeSprite(spriteName); }
        public FakeSprite getSprite() { return sprite; }
    }
}
