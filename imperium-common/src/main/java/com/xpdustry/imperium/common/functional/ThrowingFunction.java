package com.xpdustry.imperium.common.functional;

@FunctionalInterface
public interface ThrowingFunction<I, O, T extends Throwable> {

    O apply(final I input) throws T;
}
