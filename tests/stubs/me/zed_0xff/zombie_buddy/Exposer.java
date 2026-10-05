package me.zed_0xff.zombie_buddy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Stub of ZombieBuddy's Exposer; only the marker annotation is needed. */
public class Exposer {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LuaClass {
        String name() default "";
    }
}
