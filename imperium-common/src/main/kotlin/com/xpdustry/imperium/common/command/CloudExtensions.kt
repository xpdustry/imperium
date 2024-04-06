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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.localization.LocalizationSource
import kotlin.reflect.KClass
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.parser.standard.EnumParser
import org.incendo.cloud.permission.Permission.allOf
import org.incendo.cloud.permission.Permission.anyOf
import org.incendo.cloud.permission.Permission.permission

fun <S : Any, E : Enum<E>> enumParser(klass: KClass<E>): ParserDescriptor<S, E> =
    EnumParser.enumParser(klass.java)

fun AnnotationParser<*>.installCoroutineSupportImperium() =
    installCoroutineSupport(ImperiumScope.MAIN, ImperiumScope.MAIN.coroutineContext, true)

fun <T> AnnotationParser<T>.registerImperiumCommand(source: LocalizationSource) =
    registerBuilderModifier(ImperiumCommand::class.java) { annotation, builder ->
        builder.commandDescription(
            LocalisableDescription(
                "imperium.command.[${annotation.pathWithoutAliases.joinToString(".")}].description",
                source))
    }

fun <T> AnnotationParser<T>.registerImperiumPermission() =
    registerBuilderModifier(ImperiumPermission::class.java) { annotation, builder ->
        var permission = permission("imperium.rank.${annotation.rank.name.lowercase()}")
        if (annotation.gamemodes.isNotEmpty()) {
            permission =
                allOf(
                    permission,
                    anyOf(
                        annotation.gamemodes.map {
                            permission("imperium.gamemode.${it.name.lowercase()}")
                        }))
        }
        permission = anyOf(permission, permission("imperium.rank.${Rank.ADMIN.name.lowercase()}"))
        builder.permission(permission)
    }
