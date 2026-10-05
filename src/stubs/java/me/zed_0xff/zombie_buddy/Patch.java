package me.zed_0xff.zombie_buddy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COMPILE-ONLY STUB, never packaged. Mirrors the annotation API of
 * ZombieBuddy 2.x (github.com/zed-0xff/ZombieBuddy, tag v2.3.4, MIT License,
 * Copyright (c) 2025 Andrey "Zed" Zaikin) - only the members this mod uses.
 * ZombieBuddy 3.x maps this package to me.zed_0xff.zombie_buddy.annotations
 * automatically (ZB2Compat transformer).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Patch {
    String className();

    String methodName();

    boolean isAdvice() default true;

    boolean warmUp() default false;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface OnEnter {
        boolean skipOn() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface OnExit {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Return {
        boolean readOnly() default true;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Argument {
        int value() default 0;

        boolean readOnly() default true;
    }
}
