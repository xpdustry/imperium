package com.xpdustry.imperium.common.collection;

import gnu.trove.map.TCharObjectMap;
import gnu.trove.map.hash.TCharObjectHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class CharTrieMapImpl<V> implements CharTrieMap.Mutable<V> {

    private @Nullable TCharObjectMap<CharTrieMapImpl<V>> children = null;
    private @Nullable V value = null;

    @Override
    public V put(final char[] chars, final V value) {
        Objects.requireNonNull(chars);
        Objects.requireNonNull(value);

        CharTrieMapImpl<V> node = this;

        for (final var c : chars) {
            if (node.children == null) {
                node.children = new TCharObjectHashMap<>();
            }
            var next = node.children.get(c);
            if (next == null) {
                next = new CharTrieMapImpl<>();
                node.children.put(c, next);
            }
            node = next;
        }

        final var previous = node.value;
        node.value = value;
        return previous;
    }

    @Override
    public V get(final char[] chars) {
        Objects.requireNonNull(chars);

        var node = this;
        for (final var c : chars) {
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
    public boolean contains(final char[] chars, final boolean partial) {
        Objects.requireNonNull(chars);

        var node = this;
        for (final var c : chars) {
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
    public List<Token<V>> search(final String text) {
        final List<Token<V>> tokens = new ArrayList<>();
        final List<Accumulator<V>> accumulators = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            final var c = text.charAt(i);

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
                    accumulator.buffer.append(c);
                }

                if (accumulator.node.value != null) {
                    tokens.add(new Token<>(accumulator.buffer.toString(), accumulator.index, accumulator.node.value));
                }
            }
        }

        return tokens;
    }

    private static final class Accumulator<V> {
        private CharTrieMapImpl<V> node;
        private final int index;
        private final StringBuilder buffer = new StringBuilder();

        private Accumulator(final CharTrieMapImpl<V> node, final int index) {
            this.node = node;
            this.index = index;
        }
    }
}
