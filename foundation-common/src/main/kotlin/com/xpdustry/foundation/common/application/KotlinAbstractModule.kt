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
package com.xpdustry.foundation.common.application

import com.google.inject.AbstractModule
import com.google.inject.Binding
import com.google.inject.Provider
import com.google.inject.Singleton
import com.google.inject.binder.AnnotatedBindingBuilder
import com.google.inject.binder.LinkedBindingBuilder
import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.matcher.Matcher
import com.google.inject.spi.ProvisionListener
import kotlin.reflect.KClass

typealias KotlinProvisionListener = (ProvisionListener.ProvisionInvocation<*>) -> Unit

abstract class KotlinAbstractModule : AbstractModule() {

    abstract override fun configure()

    protected fun <T : Any> bind(clazz: KClass<T>): AnnotatedBindingBuilder<T> =
        bind(clazz.java)

    protected fun <T : Any, I : T> LinkedBindingBuilder<T>.implementation(clazz: KClass<I>): ScopedBindingBuilder =
        to(clazz.java)

    protected fun <T : Any> LinkedBindingBuilder<T>.provider(clazz: KClass<out Provider<out T>>): ScopedBindingBuilder =
        toProvider(clazz.java)

    protected fun <T : Any> LinkedBindingBuilder<T>.instance(instance: T): Unit =
        toInstance(instance)

    protected fun ScopedBindingBuilder.singleton(): Unit =
        `in`(Singleton::class.java)

    protected fun <T : Any> AnnotatedBindingBuilder<T>.annotated(clazz: KClass<out Annotation>): LinkedBindingBuilder<T> =
        annotatedWith(clazz.java)

    protected fun bindProvisionListener(matcher: Matcher<in Binding<*>>, listener: KotlinProvisionListener) =
        bindListener(
            matcher,
            object : ProvisionListener {
                override fun <T> onProvision(invocation: ProvisionListener.ProvisionInvocation<T>) {
                    listener(invocation)
                }
            },
        )
}
