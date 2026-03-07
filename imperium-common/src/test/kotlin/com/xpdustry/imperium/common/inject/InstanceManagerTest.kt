// SPDX-License-Identifier: GPL-3.0-only
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
