// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.string

internal class StringTrieMapImpl<V> : StringTrieMap.Mutable<V> {
    private val children = hashMapOf<Char, StringTrieMapImpl<V>>()
    private var value: V? = null

    override fun search(chars: CharSequence): List<StringTrieMap.Token<V>> {
        val tokens = arrayListOf<StringTrieMap.Token<V>>()
        val accumulators = arrayListOf<Accumulator<V>>()

        for (index in chars.indices) {
            val char = chars[index]

            if (children.isNotEmpty()) {
                accumulators += Accumulator(this, index)
            }

            val iterator = accumulators.iterator()
            while (iterator.hasNext()) {
                val accumulator = iterator.next()
                val child = accumulator.node.children[char]
                if (child == null) {
                    iterator.remove()
                    continue
                }

                accumulator.node = child
                val tokenValue = child.value
                if (tokenValue != null) {
                    tokens +=
                        StringTrieMap.Token(
                            chars.subSequence(accumulator.index, index + 1).toString(),
                            accumulator.index,
                            tokenValue,
                        )
                }
            }
        }

        return tokens
    }

    override fun get(chars: CharSequence): V? {
        var node = this
        for (char in chars) {
            node = node.children[char] ?: return null
        }
        return node.value
    }

    override fun contains(chars: CharSequence, partial: Boolean): Boolean {
        var node = this
        for (char in chars) {
            node = node.children[char] ?: return false
        }
        return node.value != null || partial
    }

    override fun put(chars: CharSequence, value: V): V? {
        var node = this
        for (char in chars) {
            node = node.children.getOrPut(char) { StringTrieMapImpl() }
        }

        val previous = node.value
        node.value = value
        return previous
    }

    private data class Accumulator<V>(var node: StringTrieMapImpl<V>, val index: Int)
}
