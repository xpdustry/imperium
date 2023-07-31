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
package com.xpdustry.imperium.common.database.mongo

import org.bson.codecs.pojo.ClassModelBuilder
import org.bson.codecs.pojo.Convention
import org.bson.codecs.pojo.InstanceCreator
import org.bson.codecs.pojo.InstanceCreatorFactory
import org.bson.codecs.pojo.PropertyModel
import org.objenesis.strategy.StdInstantiatorStrategy

object ConstructorFreeInstanciationConvention : Convention {
    override fun apply(classModelBuilder: ClassModelBuilder<*>) = apply0(classModelBuilder)

    private fun <T : Any> apply0(classModelBuilder: ClassModelBuilder<T>) {
        classModelBuilder.instanceCreatorFactory(KryoInstanceCreatorFactory(classModelBuilder.type))
    }
}

private class KryoInstanceCreatorFactory<T : Any>(private val type: Class<T>) : InstanceCreatorFactory<T> {
    override fun create(): InstanceCreator<T> = object : InstanceCreator<T> {
        private val instance = STRATEGY.newInstantiatorOf(type).newInstance()
        override fun getInstance(): T = instance
        override fun <S : Any?> set(value: S, propertyModel: PropertyModel<S>) =
            propertyModel.propertyAccessor.set(instance, value)
    }

    companion object {
        private val STRATEGY = StdInstantiatorStrategy()
    }
}
