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

class BlockClusterManager<T : Any>(private val listener: Listener<T>) {
    val clusters: List<Cluster<T>> get() = _clusters
    private val _clusters = mutableListOf<Cluster<T>>()

    fun removeElement(x: Int, y: Int) {
        for (i in _clusters.indices) {
            for (e in _clusters[i]._blocks) {
                if (e.x == x && e.y == y) {
                    _clusters.removeAt(i)._blocks.forEach { addElement(it) }
                    return
                }
            }
        }
    }

    fun addElement(block: ClusterBlock<T>) {
        val candidates = mutableListOf<Cluster<T>>()
        for (cluster in _clusters) {
            if (canBePartOfCluster(cluster, block)) {
                candidates += cluster
            }
        }
        if (candidates.isEmpty()) {
            val cluster = Cluster<T>()
            cluster._blocks += block
            cluster.update()
            _clusters += cluster
            listener.onClusterUpdate(cluster)
        } else if (candidates.size == 1) {
            candidates[0]._blocks += block
            candidates[0].update()
            listener.onClusterUpdate(candidates[0])
        } else {
            val cluster = Cluster<T>()
            for (candidate in candidates) {
                cluster._blocks += candidate._blocks
                _clusters.remove(candidate)
            }
            cluster.update()
            _clusters += cluster
            listener.onClusterUpdate(cluster)
        }
    }

    fun reset() {
        _clusters.clear()
    }

    // Uses a rectangle overlap algorithm to return whether the covered area is greater than 1
    private fun canBePartOfCluster(cluster: Cluster<T>, block: ClusterBlock<T>): Boolean {
        val x1 = maxOf(cluster.x, block.x) - 1
        val y1 = maxOf(cluster.y, block.y) - 1
        val x2 = minOf(cluster.x + cluster.w, block.x + block.size) + 1
        val y2 = minOf(cluster.y + cluster.h, block.y + block.size) + 1
        return (x2 - x1) * (y2 - y1) > 1
    }

    fun interface Listener<T : Any> {
        fun onClusterUpdate(cluster: Cluster<T>)
    }
}

class Cluster<T : Any> {
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
    internal val _blocks: MutableList<ClusterBlock<T>> = mutableListOf()

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
    }
}

data class ClusterBlock<T : Any>(
    val x: Int,
    val y: Int,
    val size: Int,
    val builder: InetAddress,
    val data: T,
)
