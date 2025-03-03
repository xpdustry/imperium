package com.xpdustry.imperium.common.collection;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface CharTrieMap<V> {

    static <V> CharTrieMap.Mutable<V> create() {
        return new CharTrieMapImpl<>();
    }

    @Nullable V get(final char[] chars);

    boolean contains(final char[] chars, final boolean partial);

    List<Token<V>> search(final String text);

    record Token<V>(String word, int index, V value) { }

    interface Mutable<V> extends CharTrieMap<V> {

        @Nullable V put(final char[] chars, final V value);
    }
}
