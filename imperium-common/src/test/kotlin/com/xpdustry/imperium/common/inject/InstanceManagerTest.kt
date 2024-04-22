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
package com.xpdustry.imperium.common.inject

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InstanceManagerTest {

    @Test
    fun `test simple`() {
        val manager = SimpleMutableInstanceManager {}
        manager.provider { "hello" }
        manager.provider { 42 }
        manager.provider { TestClass(get(), get()) }
        Assertions.assertEquals("hello", manager.get<String>())
        Assertions.assertEquals(42, manager.get<Int>())
        Assertions.assertEquals(TestClass("hello", 42), manager.get<TestClass>())
    }

    @Test
    fun `test named`() {
        val manager = SimpleMutableInstanceManager {}
        manager.provider("hello") { "hello" }
        manager.provider("world") { "world" }
        Assertions.assertEquals("hello", manager.get<String>("hello"))
        Assertions.assertEquals("world", manager.get<String>("world"))
        Assertions.assertNull(manager.getOrNull<String>())
    }

    @Test
    fun `test singleton`() {
        val manager = SimpleMutableInstanceManager {}
        manager.provider { TestClass("", 0) }
        val instance = manager.get<TestClass>()
        Assertions.assertSame(instance, manager.get<TestClass>())
    }

    @Test
    fun `test overwrite`() {
        val manager = SimpleMutableInstanceManager {}
        manager.provider { "hello" }
        manager.provider { 42 }
        manager.provider { "world" }
        Assertions.assertEquals("world", manager.get<String>())
        Assertions.assertEquals(42, manager.get<Int>())
    }

    data class TestClass(val text: String, val number: Int)
}
