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
package com.xpdustry.imperium.common.collection

class MutableUnionSet<E> {

    private val map = mutableMapOf<E, MutableSet<E>>()

    val elements: Set<E>
        get() = map.keys

    fun addElement(element: E): Boolean {
        if (element in map) return false
        val set = mutableSetOf<E>()
        set += element
        map[element] = set
        return true
    }

    fun removeElement(element: E) {
        map.remove(element)
        map.values.forEach { it -= element }
    }

    fun getUnion(element: E) = map[element] ?: emptySet()

    fun setUnion(elementA: E, elementB: E) {
        val setA = map[elementA] ?: error("$elementA not found in set.")
        val setB = map[elementB] ?: error("$elementB not found in set.")
        if (elementB !in setA) {
            if (setA.size > setB.size) {
                setA += setB
                setB.forEach { map[it] = setA }
            } else {
                setB += setA
                setA.forEach { map[it] = setB }
            }
        }
    }

    fun removeUnion(elementA: E, elementB: E): Boolean {
        val setA = map[elementA] ?: error("$elementA not found in set.")
        val setB = map[elementB] ?: error("$elementB not found in set.")
        if (elementB in setA) {
            setA -= elementB
            setB -= elementA
            return true
        }
        return false
    }

    fun clear() {
        map.clear()
    }
}
