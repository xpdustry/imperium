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
package com.xpdustry.imperium.common.command

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.descriptor.CommandDescriptor
import org.incendo.cloud.annotations.descriptor.ImmutableCommandDescriptor
import org.incendo.cloud.annotations.extractor.CommandExtractor
import org.incendo.cloud.context.CommandContext

class ImperiumCommandExtractor<S : Any>(
    private val parser: AnnotationParser<S>,
    private val sender: KClass<S>,
    private val filter: (KFunction<*>) -> Boolean = { true }
) : CommandExtractor {
    override fun extractCommands(instance: Any): Collection<CommandDescriptor> {
        val descriptors = mutableListOf<CommandDescriptor>()

        for (function in instance::class.memberFunctions) {
            val command = function.findAnnotation<ImperiumCommand>() ?: continue
            if (!filter(function)) {
                continue
            }

            if (command.path.isEmpty()) {
                throw IllegalArgumentException("Command name cannot be empty")
            }

            val syntax = buildString {
                append(command.path.joinToString(" "))

                for (parameter in function.parameters) {
                    // Skip "this"
                    if (parameter.index == 0) {
                        continue
                    }

                    if (parameter.name!!.lowercase() != parameter.name) {
                        throw IllegalArgumentException(
                            "$function parameter names must be lowercase: ${parameter.name}")
                    }

                    if (parameter.type.classifier == CommandContext::class ||
                        parameter.type.classifier == sender) {
                        continue
                    }

                    append(" ")
                    if (parameter.isOptional || parameter.type.isMarkedNullable) {
                        append("[").append(parameter.name).append("]")
                    } else {
                        append("<").append(parameter.name).append(">")
                    }
                }
            }

            val processed =
                parser.syntaxParser().parseSyntax(function.javaMethod, parser.processString(syntax))
            descriptors +=
                ImmutableCommandDescriptor.builder()
                    .method(function.javaMethod!!)
                    .syntax(processed)
                    .commandToken(parser.processString(command.name))
                    .requiredSender(sender.java)
                    .build()
        }

        return descriptors
    }
}
