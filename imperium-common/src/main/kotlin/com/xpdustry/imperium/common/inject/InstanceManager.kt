// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.inject

import kotlin.reflect.KClass

interface InstanceManager {

    fun <T : Any> getOrNull(clazz: KClass<T>, name: String = ""): T?

    fun <T : Any> get(clazz: KClass<T>, name: String = ""): T =
        getOrNull(clazz, name) ?: throw IllegalArgumentException("No instance of $clazz (name=$name) found")
}

inline fun <reified T : Any> InstanceManager.getOrNull(name: String = ""): T? = getOrNull(T::class, name)

inline fun <reified T : Any> InstanceManager.get(name: String = ""): T = get(T::class, name)

interface MutableInstanceManager : InstanceManager {

    fun createAll()

    fun <T : Any> provider(clazz: KClass<T>, name: String, provider: InstanceProvider<out T>)

    fun interface Listener {
        fun onInstanceCreation(instance: Any)
    }
}

inline fun <reified T : Any> MutableInstanceManager.provider(
    name: String = "",
    noinline provider: InstanceManager.() -> T,
) = provider(T::class, name, provider)

inline fun <reified T : Any> MutableInstanceManager.provider(provider: InstanceProvider<T>) =
    provider(T::class, "", provider)

internal class SimpleMutableInstanceManager(private val listener: MutableInstanceManager.Listener) :
    MutableInstanceManager {
    private val instances = mutableMapOf<InstanceKey<*>, InstanceProvider<*>>()

    override fun createAll() {
        for (instance in instances) {
            getOrNull(instance.key.clazz, instance.key.name)
        }
    }

    override fun <T : Any> provider(clazz: KClass<T>, name: String, provider: InstanceProvider<out T>) {
        instances[InstanceKey(clazz, name)] = NotifyingInstanceProvider(provider)
    }

    override fun <T : Any> getOrNull(clazz: KClass<T>, name: String): T? {
        val key = InstanceKey(clazz, name)

        @Suppress("UNCHECKED_CAST") val instance = instances[key]?.create(ResolvingInstanceManager()) as T?
        return instance
    }

    private inner class ResolvingInstanceManager : InstanceManager {
        private val resolving = mutableSetOf<InstanceKey<*>>()

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getOrNull(clazz: KClass<T>, name: String): T? {
            val key = InstanceKey(clazz, name)
            if (key in resolving) {
                error("Circular dependency detected: $key")
            }

            resolving += key
            val provider = instances[key] ?: return null

            val instance = provider.create(this) as T?
            resolving -= key
            return instance
        }
    }

    private inner class NotifyingInstanceProvider<T : Any>(private val provider: InstanceProvider<T>) :
        InstanceProvider<T> {
        private var provided = false
        private var value: T? = null

        override fun create(instances: InstanceManager): T? {
            if (provided) {
                return value
            }
            value = provider.create(instances)
            provided = true
            value?.let(listener::onInstanceCreation)
            return value
        }
    }
}
