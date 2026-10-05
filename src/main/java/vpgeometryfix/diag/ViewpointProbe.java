package vpgeometryfix.diag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only probes of Viewpoint's static state.
 *
 * Class and field names come from the third-party audit of Viewpoint
 * 0.1.5a-hotfix (github.com/kilroy94/project-viewpoint-vr, NativeInput.java /
 * ViewpointBackend.java). They are NOT a stable API: every read is reflective
 * and a missing member is reported as "unknown".
 *
 * Reading a static field initializes its class if it was not yet initialized.
 * Therefore these probes run only on explicit user actions (inspection,
 * VPGF.viewpointState()), never at startup and never per frame.
 */
public final class ViewpointProbe {
    static final String[][] STATE_FIELDS = {
            {"viewpoint.core.View", "enabled"},
            {"viewpoint.input.ThirdPerson", "active"},
            {"viewpoint.input.FreeCam", "active"},
            {"viewpoint.platform.IrisPacks", "active"},
            {"viewpoint.visibility.Rooms", "hiding"},
    };

    private ViewpointProbe() {}

    public static String stateLine() {
        if (!Env.detect(false).vpClassesFound) return "viewpoint not detected";
        StringBuilder sb = new StringBuilder();
        for (String[] f : STATE_FIELDS) {
            if (sb.length() > 0) sb.append(", ");
            String simple = f[0].substring(f[0].lastIndexOf('.') + 1);
            sb.append(simple).append('.').append(f[1]).append('=').append(read(f[0], f[1]));
        }
        return sb.toString();
    }

    /** True/false if View.enabled could be read, null if unknown. */
    public static Boolean firstPersonActive() {
        if (!Env.detect(false).vpClassesFound) return Boolean.FALSE;
        Object v = Reflect.staticField("viewpoint.core.View", "enabled");
        return v instanceof Boolean b ? b : null;
    }

    static String read(String cls, String field) {
        if (Reflect.find(cls) == null) return "unknown(class missing)";
        Object v = Reflect.staticField(cls, field);
        if (v == null) return "unknown";
        if (v instanceof Boolean || v instanceof Number || v instanceof String || v.getClass().isEnum()) return v.toString();
        return v.getClass().getSimpleName();
    }

    /**
     * Dumps primitive/String/enum static fields of a Viewpoint class.
     * For targeted investigation once the inventory names a class of interest.
     */
    public static List<String> staticPrimitives(String cls) {
        List<String> out = new ArrayList<>();
        Class<?> c = Reflect.find(cls);
        if (c == null) {
            out.add(cls + ": class not found");
            return out;
        }
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            Class<?> t = f.getType();
            if (!(t.isPrimitive() || t == String.class || t.isEnum())) {
                out.add(f.getName() + " : " + t.getSimpleName());
                continue;
            }
            try {
                f.setAccessible(true);
                out.add(f.getName() + " : " + t.getSimpleName() + " = " + f.get(null));
            } catch (Throwable e) {
                out.add(f.getName() + " : <inaccessible>");
            }
        }
        return out;
    }

    /**
     * Placeholder for Viewpoint's own first-person pick (which tile/object the
     * crosshair hits). The audit names {@code MousePick.read(...)} in the render
     * path, but its result storage is UNKNOWN. Run the class inventory
     * (Ctrl+Shift+F8) and fill this in once the members are identified.
     */
    public static String pickedTarget() {
        return null;
    }
}
