package com.xpdustry.imperium.common.functional;

public sealed interface JImperiumResult<V, E> {
    static <V, E> JImperiumResult<V, E> success(final V value) {
        return new Success<>(value);
    }

    static <V, E> JImperiumResult<V, E> failure(final E error) {
        return new Failure<>(error);
    }

    V value();

    E error();

    record Success<V, E>(V value) implements JImperiumResult<V, E> {
        @Override
        public E error() {
            throw new NullPointerException("This success has no error");
        }
    }

    record Failure<V, E>(E error) implements JImperiumResult<V, E> {
        @Override
        public V value() {
            throw new NullPointerException("This failure has no value");
        }
    }
}
