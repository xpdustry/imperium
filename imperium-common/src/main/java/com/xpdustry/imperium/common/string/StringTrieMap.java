package com.xpdustry.imperium.common.string;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface StringTrieMap<V> {

    static <V> StringTrieMap.Mutable<V> create() {
        return new StringTrieMapImpl<>();
    }

    List<Token<V>> search(final CharSequence chars);

    @Nullable V get(final CharSequence chars);

    boolean contains(final CharSequence chars, final boolean partial);

    record Token<V>(String word, int index, V value) {}

    interface Mutable<V> extends StringTrieMap<V> {

        @Nullable V put(final CharSequence chars, final V value);
    }
}
