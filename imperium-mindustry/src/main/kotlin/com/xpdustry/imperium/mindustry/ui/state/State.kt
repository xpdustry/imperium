/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.mindustry.ui.state

interface State {
    operator fun <V : Any> get(key: StateKey<V>): V?

    operator fun <V : Any> set(key: StateKey<V>, value: V): V?

    fun <V : Any> remove(key: StateKey<V>): V?

    operator fun contains(key: StateKey<*>): Boolean
}

operator fun <V : Any> State.minusAssign(key: StateKey<V>) {
    remove(key)
}

fun stateOf(): State = SimpleState()

@Suppress("UNCHECKED_CAST")
private class SimpleState : State {
    private val map: MutableMap<String, Any> = mutableMapOf()

    override fun <V : Any> get(key: StateKey<V>): V? = map[key.name] as V?

    override fun <V : Any> set(key: StateKey<V>, value: V): V? = map.put(key.name, value) as V?

    override fun <V : Any> remove(key: StateKey<V>): V? = map.remove(key.name) as V?

    override fun contains(key: StateKey<*>): Boolean = map.containsKey(key.name)
}
