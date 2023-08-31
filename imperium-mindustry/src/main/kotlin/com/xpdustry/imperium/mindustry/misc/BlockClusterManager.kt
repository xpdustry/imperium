/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.mindustry.misc

import java.net.InetAddress
import java.util.LinkedList

class BlockClusterManager<T : Any>(private val listener: Listener<T>) {
    val clusters: List<Cluster<T>> get() = _clusters
    private val _clusters = mutableListOf<Cluster<T>>()

    fun addElement(block: ClusterBlock<T>) {
        val candidates = mutableListOf<Int>()
        for (i in _clusters.indices) {
            if (canBePartOfCluster(_clusters[i], block)) {
                candidates += i
            }
        }
        if (candidates.isEmpty()) {
            val cluster = Cluster<T>()
            cluster._blocks += block
            cluster.update()
            _clusters += cluster
            listener.onClusterEvent(cluster, Event.NEW)
        } else if (candidates.size == 1) {
            _clusters[candidates[0]]._blocks += block
            _clusters[candidates[0]].update()
            listener.onClusterEvent(_clusters[candidates[0]], Event.UPDATE)
        } else {
            val cluster = Cluster<T>()
            for ((shift, i) in candidates.withIndex()) {
                val target = _clusters.removeAt(i - shift)
                cluster._blocks += target._blocks
                listener.onClusterEvent(target, Event.REMOVE)
            }
            cluster._blocks += block
            cluster.update()
            _clusters += cluster
            listener.onClusterEvent(cluster, Event.NEW)
        }

        reorder()
    }

    fun removeElement(x: Int, y: Int) {
        var c = -1
        var b = -1
        for (i in _clusters.indices) {
            b = _clusters[i]._blocks.indexOfFirst { it.x == x && it.y == y }
            if (b != -1) {
                c = i
                break
            }
        }

        if (c == -1) {
            return
        }

        val blocks = LinkedList(_clusters[c]._blocks.toMutableList().apply { removeAt(b) })
        if (blocks.isEmpty()) {
            listener.onClusterEvent(_clusters.removeAt(c), Event.REMOVE)
            return
        }

        val result = mutableListOf<Cluster<T>>()
        while (blocks.isNotEmpty()) {
            val cluster = Cluster(listOf(blocks.pop()))
            var added: Boolean
            do {
                added = false
                for (i in blocks.indices) {
                    if (canBePartOfCluster(cluster, blocks[i])) {
                        cluster._blocks += blocks.removeAt(i)
                        added = true
                        break
                    }
                }
            } while (blocks.isNotEmpty() && added)
            result += cluster
        }

        if (result.size == 1) {
            _clusters[c] = result[0]
            _clusters[c].update()
            reorder()
            listener.onClusterEvent(_clusters[c], Event.UPDATE)
            return
        }

        listener.onClusterEvent(_clusters.removeAt(c), Event.REMOVE)
        for (cluster in result) {
            _clusters += cluster
            cluster.update()
            listener.onClusterEvent(cluster, Event.NEW)
        }

        reorder()
    }

    fun reset() {
        _clusters.clear()
    }

    private fun reorder() {
        _clusters.sortWith(compareBy(Cluster<T>::x, Cluster<T>::y))
    }

    private fun canBePartOfCluster(cluster: Cluster<T>, block: ClusterBlock<T>): Boolean = cluster._blocks.any {
        val r1 = it.x + it.size + 1
        val l1 = it.x - 1
        val b1 = it.y - 1
        val t1 = it.y + it.size + 1

        val r2 = block.x + block.size + 1
        val l2 = block.x - 1
        val b2 = block.y - 1
        val t2 = block.y + block.size + 1

        val x1 = maxOf(l1, l2)
        val y1 = maxOf(b1, b2)
        val x2 = minOf(r1, r2)
        val y2 = minOf(t1, t2)

        maxOf(0, x2 - x1) * maxOf(0, y2 - y1) > 4
    }

    fun interface Listener<T : Any> {
        fun onClusterEvent(cluster: Cluster<T>, event: Event)
    }

    enum class Event {
        NEW, UPDATE, REMOVE
    }
}

class Cluster<T : Any>(blocks: List<ClusterBlock<T>> = emptyList()) {
    var x: Int = 0
        private set
    var y: Int = 0
        private set
    var w: Int = 0
        private set
    var h: Int = 0
        private set

    val blocks: List<ClusterBlock<T>> get() = _blocks

    @Suppress("PropertyName")
    internal val _blocks: MutableList<ClusterBlock<T>> = blocks.toMutableList()

    fun copy() = Cluster<T>().also {
        it.x = x
        it.y = y
        it.w = w
        it.h = h
        it._blocks += _blocks
    }

    internal fun update() {
        x = _blocks.minOf { it.x }
        y = _blocks.minOf { it.y }
        w = _blocks.maxOf { it.x + it.size } - x
        h = _blocks.maxOf { it.y + it.size } - y
        _blocks.sortWith(compareBy(ClusterBlock<T>::x, ClusterBlock<T>::y))
    }
}

data class ClusterBlock<T : Any>(
    val x: Int,
    val y: Int,
    val size: Int,
    val builder: InetAddress,
    val data: T,
)
