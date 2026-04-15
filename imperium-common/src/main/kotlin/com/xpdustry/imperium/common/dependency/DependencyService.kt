// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.dependency

import java.util.SequencedSet
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.cast
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure
import okio.withLock

class DependencyService(val configure: Binder.() -> Unit) {

    private val factories = hashMapOf<Key<*>, (ResolutionContext) -> Any>()
    private val instances = linkedMapOf<Key<*>, Any>()
    private val lock = ReentrantLock()

    init {
        Binder().configure()
    }

    inline fun <reified T : Any> get(name: String = ""): T = this.get(T::class, name)

    fun <T : Any> get(type: KClass<T>, name: String): T {
        return this.resolve(Key(type, name), ResolutionContext())
    }

    fun getAll(): Collection<Any> {
        val context = ResolutionContext()
        this.factories.keys.forEach { key -> this.resolve(key, context) }
        return instances.values
    }

    inline fun <reified T : Any> create(): T = this.create(T::class)

    fun <T : Any> create(type: KClass<T>): T {
        return this.resolve(type.getInjectableConstructor(), ResolutionContext())
    }

    private fun <T : Any> resolve(function: KFunction<T>, context: ResolutionContext): T =
        function.callBy(function.getParametersWithKeys().mapValues { (_, key) -> this.resolve(key, context) })

    private fun <T : Any> resolve(key: Key<T>, context: ResolutionContext): T =
        lock.withLock {
            if (this.instances.containsKey(key)) {
                return key.type.cast(this.instances[key]!!)
            }
            if (!context.visiting.add(key)) {
                error("Circular bindings detected: ${context.visiting.joinToString(" -> ")}")
            }
            val factory = this.factories[key] ?: error("No bindings found for $key")
            val instance = factory(context)
            this.instances[key] = instance
            context.visiting.remove(key)
            return key.type.cast(instance)
        }

    private fun KFunction<*>.getParametersWithKeys() =
        parameters.associateWith {
            require(it.kind == KParameter.Kind.VALUE) { "${it.name} is not a value parameter in $this" }
            Key(it.type.jvmErasure, it.findAnnotation<Named>()?.value ?: "")
        }

    private fun <T : Any> KClass<T>.getInjectableConstructor(): KFunction<T> {
        if (this.hasAnnotation<Inject>()) {
            return this.primaryConstructor
                ?: error("$this has @Inject at the top level, but has no primary constructor")
        }
        return this.constructors.firstOrNull { it.hasAnnotation<Inject>() }
            ?: error("No injectable constructor for $this")
    }

    inner class Binder {

        inline fun <reified T : Any> bindToProv(name: String = "", noinline provider: () -> T) =
            bindToProv(T::class, name, provider)

        fun <T : Any> bindToProv(type: KClass<T>, name: String, provider: () -> T) {
            factories[Key(type, name)] = { provider() }
        }

        inline fun <reified T : Any, reified I : T> bindToImpl(name: String = "") = bindToImpl(T::class, name, I::class)

        fun <T : Any, I : T> bindToImpl(type: KClass<T>, name: String, impl: KClass<I>) {
            bindToFunc(type, name, impl.getInjectableConstructor())
        }

        inline fun <reified T : Any> bindToFunc(function: KFunction<T>, name: String = "") =
            bindToFunc(T::class, name, function)

        fun <T : Any> bindToFunc(type: KClass<T>, name: String, function: KFunction<T>) {
            val _ = function.getParametersWithKeys()
            function.isAccessible = true
            factories[Key(type, name)] = { resolve(function, it) }
        }
    }

    private data class Key<T : Any>(val type: KClass<T>, val name: String) {
        init {
            require(type.typeParameters.isEmpty()) { "Type $type should not have type parameters" }
        }
    }

    private data class ResolutionContext(val visiting: SequencedSet<Key<*>> = linkedSetOf())
}
