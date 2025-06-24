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

import kotlin.reflect.KClass

interface InstanceManager {

    fun <T : Any> getOrNull(clazz: KClass<T>, name: String = ""): T?

    fun <T : Any> get(clazz: KClass<T>, name: String = ""): T =
        getOrNull(clazz, name) ?: throw IllegalArgumentException("No instance of $clazz (name=$name) found")
}

inline fun <reified T : Any> InstanceManager.getOrNull(name: String = ""): T? = getOrNull(T::class, name)

inline fun <reified T : Any> InstanceManager.get(name: String = ""): T = get(T::class, name)
