// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.dependency

import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyServiceTest {

    @Test
    fun `resolves provider function and implementation bindings`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToFunc<Int>(::life)
            bindToImpl<TestClass, TestClass>()
        }

        assertEquals("hello", service.get<String>())
        assertEquals(42, service.get<Int>())
        assertEquals(TestClass("hello", 42), service.get<TestClass>())
    }

    @Test
    fun `supports named bindings`() {
        val service = DependencyService {
            bindToProv<String>("hello") { "hello" }
            bindToProv<String>("world") { "world" }
        }

        assertEquals("hello", service.get<String>("hello"))
        assertEquals("world", service.get<String>("world"))
        assertThrows(IllegalStateException::class.java) { service.get<String>() }
    }

    @Test
    fun `caches resolved instances as singletons`() {
        val service = DependencyService {
            bindToProv<String> { "" }
            bindToProv<Int> { 0 }
            bindToImpl<TestClass, TestClass>()
        }

        val instance = service.get<TestClass>()
        assertSame(instance, service.get<TestClass>())
    }

    @Test
    fun `uses the last binding for a key`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToProv<Int> { 42 }
            bindToProv<String> { "world" }
        }

        assertEquals("world", service.get<String>())
        assertEquals(42, service.get<Int>())
    }

    @Test
    fun `creates an injectable type without binding it`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToProv<Int> { 42 }
        }

        assertEquals(TestClass("hello", 42), service.create<TestClass>())
        assertEquals(setOf("hello", 42), service.getAll().toSet())
    }

    @Test
    fun `injects function value parameters`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToProv<Int> { 42 }
            bindToFunc<TestClass>(::createTestClass)
        }

        assertEquals(TestClass("hello", 42), service.get<TestClass>())
    }

    @Test
    fun `injects named constructor parameters`() {
        val service = DependencyService {
            bindToProv<String>("hello") { "hello" }
            bindToProv<Int> { 42 }
            bindToImpl<NamedTestClass, NamedTestClass>()
        }

        assertEquals(NamedTestClass("hello", 42), service.get<NamedTestClass>())
    }

    @Test
    fun `returns all resolved bindings`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToProv<Int> { 42 }
            bindToImpl<TestClass, TestClass>()
        }

        assertEquals(setOf("hello", 42, TestClass("hello", 42)), service.getAll().toSet())
    }

    @Test
    fun `fails to create a type without an injectable constructor`() {
        val service = DependencyService {}

        val exception = assertThrows(IllegalStateException::class.java) { service.create<NoInjectClass>() }
        assertTrue(exception.message!!.contains("No injectable constructor"))
    }

    @Test
    fun `uses an annotated secondary constructor when the class is not annotated`() {
        val service = DependencyService {
            bindToProv<String> { "hello" }
            bindToProv<Int> { 42 }
        }

        assertEquals(SecondaryInjectClass("hello", -8), service.create<SecondaryInjectClass>())
    }

    @Test
    fun `detects circular dependencies`() {
        val service = DependencyService {
            bindToImpl<CircularA, CircularA>()
            bindToImpl<CircularB, CircularB>()
        }

        val exception = assertThrows(IllegalStateException::class.java) { service.get<CircularA>() }
        assertTrue(exception.message!!.contains("Circular bindings detected"))
    }

    @Test
    fun `rejects functions with non value parameters`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DependencyService { bindToFunc<Int>(ProviderModule::life) }
            }

        assertTrue(exception.message!!.contains("not a value parameter"))
    }

    @Test
    fun `rejects generic binding keys`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                DependencyService { bindToProv<Supplier<String>> { Supplier { "hello" } } }
            }

        assertTrue(exception.message!!.contains("should not have type parameters"))
    }

    private fun life(): Int = 42

    private fun createTestClass(text: String, number: Int): TestClass = TestClass(text, number)

    @Inject data class TestClass(private val text: String, private val number: Int)

    @Inject data class NamedTestClass(@Named("hello") private val text: String, private val number: Int)

    data class NoInjectClass(private val text: String)

    data class SecondaryInjectClass(val text: String, val number: Int) {
        @Suppress("unused") @Inject constructor(text: String) : this(text, -8)
    }

    @Inject data class CircularA(private val dependency: CircularB)

    @Inject data class CircularB(private val dependency: CircularA)

    class ProviderModule {
        fun life(): Int = 42
    }
}
