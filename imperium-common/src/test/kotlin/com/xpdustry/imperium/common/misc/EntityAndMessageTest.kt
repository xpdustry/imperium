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
package com.xpdustry.imperium.common.misc

import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.message.Message
import io.github.classgraph.ClassGraph
import kotlin.reflect.jvm.jvmName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class EntityAndMessageTest {

    @Test
    fun `test entities and messages serializable`() {
        val graph =
            ClassGraph()
                .enableAnnotationInfo()
                .enableClassInfo()
                .acceptPackages("com.xpdustry.imperium.common")
                .scan()
        (graph.getClassesImplementing(Entity::class.jvmName) +
                graph.getClassesImplementing(Message::class.jvmName))
            .filter { it.isStandardClass }
            .forEach { info ->
                if (info.getAnnotationInfo(Serializable::class.java) == null) {
                    fail("Class ${info.name} is not serializable")
                }
            }
    }
}
