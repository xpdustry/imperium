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

import kotlin.reflect.KClass

interface InstanceManager {
    fun createSingletons()

    fun <T : Any> getOrNull(clazz: KClass<T>, name: String = ""): T?

    fun <T : Any> get(clazz: KClass<T>, name: String = ""): T =
        getOrNull(clazz, name)
            ?: throw IllegalArgumentException("No instance of $clazz (name=$name) found")

    fun interface Listener {
        fun onInstanceProvision(instance: Any)
    }
}

inline fun <reified T : Any> InstanceManager.getOrNull(name: String = ""): T? =
    getOrNull(T::class, name)

inline fun <reified T : Any> InstanceManager.get(name: String = ""): T = get(T::class, name)

internal class SimpleInstanceManager(
    private val module: Module,
    private val listener: InstanceManager.Listener
) : InstanceManager {
    override fun createSingletons() {
        for (instance in module.instances) {
            if (instance.value is SingleInstanceFactory) {
                getOrNull(instance.key.clazz, instance.key.name)
            }
        }
    }

    override fun <T : Any> getOrNull(clazz: KClass<T>, name: String): T? {
        val key = InstanceKey(clazz, name)

        @Suppress("UNCHECKED_CAST")
        val instance = module.instances[key]?.create(ResolvingInstanceManager()) as T?
        if (instance != null) {
            listener.onInstanceProvision(instance)
        }
        return instance
    }

    private inner class ResolvingInstanceManager : InstanceManager {
        private val resolving = mutableSetOf<InstanceKey<*>>()

        override fun createSingletons() = Unit

        override fun <T : Any> getOrNull(clazz: KClass<T>, name: String): T? {
            val key = InstanceKey(clazz, name)
            if (key in resolving) {
                throw IllegalStateException("Circular dependency detected: $key")
            }

            resolving += key
            val provider = module.instances[key] ?: return null

            @Suppress("UNCHECKED_CAST") val instance = provider.create(this) as T?
            resolving -= key

            if (instance != null) {
                listener.onInstanceProvision(instance)
            }
            return instance
        }
    }
}
