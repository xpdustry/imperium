package com.xpdustry.imperium.common.string;

import com.google.common.base.Preconditions;
import gnu.trove.map.TCharObjectMap;
import gnu.trove.map.hash.TCharObjectHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class StringTrieMapImpl<V> implements StringTrieMap.Mutable<V> {

    private @Nullable TCharObjectMap<StringTrieMapImpl<V>> children = null;
    private @Nullable V value = null;

    @Override
    public List<Token<V>> search(final CharSequence chars) {
        Preconditions.checkNotNull(chars, "chars");

        final List<Token<V>> tokens = new ArrayList<>();
        final List<Accumulator<V>> accumulators = new ArrayList<>();

        for (int i = 0; i < chars.length(); i++) {
            final var c = chars.charAt(i);

            if (this.children != null) {
                accumulators.add(new Accumulator<>(this, i));
            }

            final var iterator = accumulators.iterator();
            while (iterator.hasNext()) {
                final var accumulator = iterator.next();

                final var child = accumulator.node.children == null ? null : accumulator.node.children.get(c);
                if (child == null) {
                    iterator.remove();
                    continue;
                } else {
                    accumulator.node = child;
                }

                if (accumulator.node.value != null) {
                    tokens.add(new Token<>(
                            chars.subSequence(accumulator.index, i + 1).toString(),
                            accumulator.index,
                            accumulator.node.value));
                }
            }
        }

        return tokens;
    }

    @Override
    public V get(final CharSequence chars) {
        Preconditions.checkNotNull(chars, "chars");

        var node = this;
        for (int i = 0; i < chars.length(); i++) {
            final var c = chars.charAt(i);
            if (node.children == null) {
                return null;
            }
            node = node.children.get(c);
            if (node == null) {
                return null;
            }
        }

        return node.value;
    }

    @Override
    public boolean contains(final CharSequence chars, final boolean partial) {
        Preconditions.checkNotNull(chars, "chars");

        var node = this;
        for (int i = 0; i < chars.length(); i++) {
            final var c = chars.charAt(i);
            if (node.children == null) {
                return false;
            }
            node = node.children.get(c);
            if (node == null) {
                return false;
            }
        }

        return node.value != null || partial;
    }

    @Override
    public V put(final CharSequence chars, final V value) {
        Preconditions.checkNotNull(chars, "chars");
        Preconditions.checkNotNull(value, "value");

        StringTrieMapImpl<V> node = this;

        for (int i = 0; i < chars.length(); i++) {
            final var c = chars.charAt(i);
            if (node.children == null) {
                node.children = new TCharObjectHashMap<>();
            }
            var next = node.children.get(c);
            if (next == null) {
                next = new StringTrieMapImpl<>();
                node.children.put(c, next);
            }
            node = next;
        }

        final var previous = node.value;
        node.value = value;
        return previous;
    }

    private static final class Accumulator<V> {
        private StringTrieMapImpl<V> node;
        private final int index;

        private Accumulator(final StringTrieMapImpl<V> node, final int index) {
            this.node = node;
            this.index = index;
        }
    }
}
