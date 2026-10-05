package se.krka.kahlua.integration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-ONLY STUB. Never packaged into the mod JAR.
 *
 * Mirrors the Kahlua annotation shipped inside projectzomboid.jar so the mod
 * can be compiled without the proprietary game JAR. At runtime the game's own
 * class (same binary name) is used. ZombieBuddy reads name() and global() of
 * this annotation to register static methods as global Lua functions
 * (verified in ZombieBuddy v2.3.2 PatchEngine and v3 Exposer sources).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LuaMethod {
    String name() default "";

    boolean global() default false;
}
