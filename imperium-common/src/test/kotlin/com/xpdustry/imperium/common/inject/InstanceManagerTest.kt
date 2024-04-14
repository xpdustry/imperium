/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.common.inject

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InstanceManagerTest {

    @Test
    fun `test simple`() {
        val module =
            module("test") {
                factory { "hello" }
                factory { 42 }
                factory { TestClass(get(), get()) }
            }
        val manager = SimpleInstanceManager(module, EMPTY_LISTENER)
        Assertions.assertEquals("hello", manager.get<String>())
        Assertions.assertEquals(42, manager.get<Int>())
        Assertions.assertEquals(TestClass("hello", 42), manager.get<TestClass>())
    }

    @Test
    fun `test named`() {
        val module =
            module("test") {
                factory("hello") { "hello" }
                factory("world") { "world" }
            }
        val manager = SimpleInstanceManager(module, EMPTY_LISTENER)
        Assertions.assertEquals("hello", manager.get<String>("hello"))
        Assertions.assertEquals("world", manager.get<String>("world"))
        Assertions.assertNull(manager.getOrNull<String>())
    }

    @Test
    fun `test singleton`() {
        val module = module("test") { single { TestClass("", 0) } }
        val manager = SimpleInstanceManager(module, EMPTY_LISTENER)
        val instance = manager.get<TestClass>()
        Assertions.assertSame(instance, manager.get<TestClass>())
    }

    @Test
    fun `test error on duplicate factory`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            module("test") {
                factory { "hello" }
                factory { "world" }
            }
        }
    }

    @Test
    fun `test error on duplicate module`() {
        val moduleA = module("a") {}
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            module("test") {
                include(moduleA)
                include(moduleA)
            }
        }
    }

    @Test
    fun `test module overwrite`() {
        val moduleA =
            module("a") {
                factory { "hello" }
                factory { 42 }
            }
        val moduleB =
            module("b") {
                include(moduleA)
                factory { "world" }
            }
        val manager = SimpleInstanceManager(moduleB, EMPTY_LISTENER)
        Assertions.assertEquals("world", manager.get<String>())
        Assertions.assertEquals(42, manager.get<Int>())
    }

    data class TestClass(val text: String, val number: Int)

    companion object {
        private val EMPTY_LISTENER = InstanceManager.Listener {}
    }
}
