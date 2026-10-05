package zombie.core.properties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stub of zombie.core.properties.PropertyContainer. */
public class PropertyContainer {

    public final ArrayList<String> flags = new ArrayList<>();
    public final Map<String, String> values = new LinkedHashMap<>();

    public ArrayList<String> getFlagsList() {
        return flags;
    }

    public ArrayList<String> getPropertyNames() {
        return new ArrayList<>(values.keySet());
    }

    public String get(String name) {
        return values.get(name);
    }
}
