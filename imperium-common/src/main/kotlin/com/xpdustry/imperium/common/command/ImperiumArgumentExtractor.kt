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

import com.xpdustry.imperium.common.localization.LocalizationSource
import java.lang.reflect.Method
import org.incendo.cloud.annotations.ArgumentMode
import org.incendo.cloud.annotations.SyntaxFragment
import org.incendo.cloud.annotations.descriptor.ArgumentDescriptor
import org.incendo.cloud.annotations.extractor.ArgumentExtractor

class ImperiumArgumentExtractor(private val source: LocalizationSource) : ArgumentExtractor {
    override fun extractArguments(
        syntax: List<SyntaxFragment>,
        method: Method
    ): Collection<ArgumentDescriptor> {
        val path =
            syntax
                .asSequence()
                .filter { it.argumentMode() == ArgumentMode.LITERAL }
                .joinToString(".", transform = SyntaxFragment::major)
        val arguments =
            syntax
                .asSequence()
                .filter { it.argumentMode() != ArgumentMode.LITERAL }
                .associateBy(SyntaxFragment::major)
        val descriptors = mutableListOf<ArgumentDescriptor>()
        for (parameter in method.parameters) {
            val fragment = arguments[parameter.name] ?: continue
            descriptors +=
                ArgumentDescriptor.builder()
                    .parameter(parameter)
                    .name(fragment.major())
                    .description(
                        LocalisableDescription(
                            "imperium.command.[$path].argument.${fragment.major()}.description",
                            source))
                    .build()
        }
        return descriptors
    }
}
