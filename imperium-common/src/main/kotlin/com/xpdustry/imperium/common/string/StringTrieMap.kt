// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.string

interface StringTrieMap<V> {

    fun search(chars: CharSequence): List<Token<V>>

    operator fun get(chars: CharSequence): V?

    fun contains(chars: CharSequence, partial: Boolean): Boolean

    data class Token<V>(val word: String, val index: Int, val value: V)

    interface Mutable<V> : StringTrieMap<V> {
        fun put(chars: CharSequence, value: V): V?
    }

    companion object {
        fun <V> create(): Mutable<V> = StringTrieMapImpl()
    }
}
