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
package com.xpdustry.foundation.common.database.mongo

import org.bson.codecs.pojo.ClassModelBuilder
import org.bson.codecs.pojo.Convention
import org.bson.codecs.pojo.InstanceCreator
import org.bson.codecs.pojo.InstanceCreatorFactory
import org.bson.codecs.pojo.PropertyModel
import sun.misc.Unsafe
import java.lang.reflect.Method

// NOTE: Extremely unsafe, but it works.
// TODO: Maybe use the faster alternative sun.reflect.ReflectionFactory
object UnsafeInstanciationConvention : Convention {
    override fun apply(classModelBuilder: ClassModelBuilder<*>) = apply0(classModelBuilder)

    private fun <T : Any> apply0(classModelBuilder: ClassModelBuilder<T>) {
        classModelBuilder.instanceCreatorFactory(UnsafeInstanceCreatorFactory(classModelBuilder.type))
    }
}

private class UnsafeInstanceCreatorFactory<T : Any>(private val type: Class<T>) : InstanceCreatorFactory<T> {

    private val allocateInstance: Method
    private val unsafe: Unsafe

    init {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe")
        theUnsafe.isAccessible = true
        unsafe = theUnsafe.get(null) as Unsafe
        allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
    }

    override fun create(): InstanceCreator<T> = object : InstanceCreator<T> {

        @Suppress("UNCHECKED_CAST")
        private val instance = allocateInstance.invoke(unsafe, type) as T

        override fun <S : Any?> set(value: S, propertyModel: PropertyModel<S>) {
            propertyModel.propertyAccessor.set(instance, value)
        }

        override fun getInstance(): T = instance
    }
}
