package com.xpdustry.imperium.common.functional;

@FunctionalInterface
public interface ThrowingConsumer<V, T extends Throwable> {
    void accept(final V value) throws T;
}
