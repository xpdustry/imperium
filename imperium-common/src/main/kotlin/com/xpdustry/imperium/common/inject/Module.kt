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
package com.xpdustry.imperium.common.inject

import kotlin.reflect.KClass

interface Module {
    val name: String
    val instances: Map<InstanceKey<*>, InstanceFactory<*>>
    fun include(module: Module)
    fun <T : Any> single(clazz: KClass<T>, name: String, creator: InstanceFactory<out T>)
    fun <T : Any> factory(clazz: KClass<T>, name: String, creator: InstanceFactory<out T>)
}

fun module(name: String, init: Module.() -> Unit): Module {
    val module = SimpleModule(name)
    module.init()
    return module
}

inline fun <reified T : Any> Module.single(name: String = "", crossinline creator: InstanceManager.() -> T) =
    single(T::class, name) { creator(it) }

inline fun <reified T : Any> Module.single(name: String, creator: InstanceFactory<out T>) =
    single(T::class, name, creator)

inline fun <reified T : Any> Module.single(creator: InstanceFactory<out T>) =
    single(T::class, "", creator)

inline fun <reified T : Any> Module.factory(name: String = "", crossinline creator: InstanceManager.() -> T) =
    factory(T::class, name) { creator(it) }

inline fun <reified T : Any> Module.factory(name: String, creator: InstanceFactory<out T>) =
    factory(T::class, name, creator)

inline fun <reified T : Any> Module.factory(creator: InstanceFactory<out T>) =
    factory(T::class, "", creator)

private class SimpleModule(override val name: String) : Module {
    private val _instances = mutableMapOf<InstanceKey<*>, InstanceFactory<*>>()
    private val modules = mutableListOf<Module>()

    override val instances: Map<InstanceKey<*>, InstanceFactory<*>> get() {
        val result = mutableMapOf<InstanceKey<*>, InstanceFactory<*>>()
        modules.forEach { result.putAll(it.instances) }
        result.putAll(_instances)
        return result
    }

    override fun include(module: Module) {
        if (module in modules) {
            throw IllegalArgumentException("Module ${module.name} already included")
        }
        modules += module
    }

    override fun <T : Any> single(clazz: KClass<T>, name: String, creator: InstanceFactory<out T>) {
        factory(clazz, name, SingleInstanceFactory(creator))
    }

    override fun <T : Any> factory(clazz: KClass<T>, name: String, creator: InstanceFactory<out T>) {
        val key = InstanceKey(clazz, name)
        if (!_instances.containsKey(key)) {
            _instances[key] = creator
        } else {
            throw IllegalArgumentException("Instance $key already exists in module $name")
        }
    }
}

internal class SingleInstanceFactory<T : Any>(private val provider: InstanceFactory<T>) : InstanceFactory<T> {
    private var provided = false
    private var value: T? = null
    override fun create(instances: InstanceManager): T? {
        if (provided) {
            return value
        }
        value = provider.create(instances)
        provided = true
        return value
    }
}
