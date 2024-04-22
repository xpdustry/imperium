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
package com.xpdustry.imperium.common.geometry

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClusterManagerTest {

    @Test
    fun `test blocks that share a side`() {
        val manager = createManager()
        manager.addElement(createBlock(0, 0, 1))
        manager.addElement(createBlock(1, 0, 1))
        Assertions.assertEquals(1, manager.clusters.size)

        val cluster = manager.clusters[0]
        Assertions.assertEquals(2, cluster.blocks.size)
        Assertions.assertEquals(0, cluster.x)
        Assertions.assertEquals(0, cluster.y)
        Assertions.assertEquals(2, cluster.w)
        Assertions.assertEquals(1, cluster.h)
    }

    @Test
    fun `test blocks that do not share a side`() {
        val manager = createManager()
        manager.addElement(createBlock(2, 2, 2))
        manager.addElement(createBlock(-2, 0, 1))
        manager.addElement(createBlock(10, 10, 10))
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test blocks that partially share a side`() {
        val manager = createManager()
        manager.addElement(createBlock(1, 1, 2))
        manager.addElement(createBlock(3, 2, 2))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test blocks that only share a corner`() {
        val manager = createManager()
        manager.addElement(createBlock(0, 0, 1))
        manager.addElement(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
    }

    @Test
    fun `test simple remove 1`() {
        val manager = createManager()
        for (x in 0..4) {
            for (y in 0..4) {
                manager.addElement(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(25, manager.clusters[0].blocks.size)

        // Removes a U shape inside the 5 by 5 square
        for (x in 1..3) {
            for (y in 1..3) {
                if (x == 1 && (y == 1 || y == 2)) continue
                manager.removeElement(x, y)
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test simple remove 2`() {
        val manager = createManager()
        for (x in 0..2) {
            for (y in 0..5) {
                manager.addElement(createBlock(x, y, 1))
            }
        }

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(18, manager.clusters[0].blocks.size)

        manager.removeElement(0, 1)
        manager.removeElement(1, 1)

        Assertions.assertEquals(1, manager.clusters.size)
        Assertions.assertEquals(16, manager.clusters[0].blocks.size)
    }

    @Test
    fun `test remove split`() {
        val manager = createManager()
        for (x in 0..2) {
            manager.addElement(createBlock(x, 0, 1))
        }
        manager.addElement(createBlock(1, 1, 1))
        Assertions.assertEquals(1, manager.clusters.size)
        manager.removeElement(1, 0)
        Assertions.assertEquals(3, manager.clusters.size)
    }

    @Test
    fun `test simple merge`() {
        val manager = createManager()
        for (y in 0..2) {
            for (x in 0..2) {
                manager.addElement(createBlock(x, y * 2, 1))
            }
        }
        Assertions.assertEquals(3, manager.clusters.size)
        manager.addElement(createBlock(1, 1, 1))
        Assertions.assertEquals(2, manager.clusters.size)
        manager.addElement(createBlock(1, 3, 1))
        Assertions.assertEquals(1, manager.clusters.size)
    }

    @Test
    fun `test error on add to occupied`() {
        val manager = createManager()
        manager.addElement(createBlock(0, 0, 1))
        assertThrows<IllegalStateException> { manager.addElement(createBlock(0, 0, 1)) }
    }

    private fun createManager() = ClusterManager<Unit> { _, _ -> }

    private fun createBlock(x: Int, y: Int, size: Int) = Cluster.Block(x, y, size, Unit)
}
