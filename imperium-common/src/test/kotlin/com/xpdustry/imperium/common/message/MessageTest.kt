// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.message

import io.github.classgraph.ClassGraph
import kotlin.reflect.jvm.jvmName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class MessageTest {

    @Test
    fun `test messages serializable`() {
        val graph =
            ClassGraph().enableAnnotationInfo().enableClassInfo().acceptPackages("com.xpdustry.imperium.common").scan()
        (graph.getClassesImplementing(Message::class.jvmName))
            .filter { it.isStandardClass }
            .forEach { info ->
                if (info.getAnnotationInfo(Serializable::class.java) == null) {
                    fail("Class ${info.name} is not serializable")
                }
            }
    }
}
