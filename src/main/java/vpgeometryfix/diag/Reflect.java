package vpgeometryfix.diag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reflection helpers. All game and Viewpoint access in this mod goes through
 * here so the JAR has no link-time dependency on projectzomboid.jar,
 * Viewpoint or ZombieBuddy. A missing class/member yields {@code null},
 * never an exception.
 */
public final class Reflect {
    private Reflect() {}

    private static Set<ClassLoader> loaders() {
        Set<ClassLoader> set = new LinkedHashSet<>();
        set.add(Reflect.class.getClassLoader());
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        if (ctx != null) set.add(ctx);
        set.add(ClassLoader.getSystemClassLoader());
        set.remove(null);
        return set;
    }

    /**
     * Resolves a class WITHOUT running its static initializer, so a lookup
     * never changes game or Viewpoint state.
     */
    public static Class<?> find(String name) {
        for (ClassLoader l : loaders()) {
            try {
                return Class.forName(name, false, l);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try next loader
            }
        }
        return null;
    }

    /** Note: reading a static field initializes its class if not yet initialized. */
    public static Object staticField(String className, String field) {
        Class<?> c = find(className);
        if (c == null) return null;
        try {
            Field f = findField(c, field);
            if (f == null || !Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }

    /** Reads an instance field (any visibility, inherited), or returns null. */
    public static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = findField(target.getClass(), name);
            if (f == null || Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls a public no-arg instance method, or returns null. */
    public static Object call(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls a public static no-arg method, or returns null. */
    public static Object callStatic(Class<?> c, String method) {
        if (c == null) return null;
        try {
            Method m = c.getMethod(method);
            if (!Modifier.isStatic(m.getModifiers())) return null;
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Path codeSource(Class<?> c) {
        if (c == null) return null;
        try {
            CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return null;
            return Path.of(cs.getLocation().toURI());
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
