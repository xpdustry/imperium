/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.inject

import com.google.inject.AbstractModule
import com.google.inject.Singleton
import com.google.inject.binder.AnnotatedBindingBuilder
import kotlin.reflect.KClass


class KotlinModule(private val block: KotlinModule.() -> Unit) : AbstractModule() {
    override fun configure() {
        block()
    }

    fun <T : Any> bind(clazz: KClass<T>): AnnotatedBindingBuilder<T> = bind(clazz.java)

    infix fun <T : Any, I : T> AnnotatedBindingBuilder<T>.toSingleton(clazz: KClass<I>) = to(clazz.java).`in`(Singleton::class.java)
}
