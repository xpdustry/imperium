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
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.localization.LocalizationSource
import io.leangen.geantyref.TypeToken
import java.util.concurrent.Executor
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.MethodCommandExecutionHandler
import org.incendo.cloud.key.CloudKey
import org.incendo.cloud.meta.CommandMetaBuilder
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.parser.ParserParameter
import org.incendo.cloud.parser.ParserParameters
import org.incendo.cloud.parser.standard.EnumParser
import org.incendo.cloud.permission.Permission
import org.incendo.cloud.translations.LocaleExtractor
import org.incendo.cloud.translations.TranslationBundle

fun <S : Any, E : Enum<E>> enumParser(klass: KClass<E>): ParserDescriptor<S, E> =
    EnumParser.enumParser(klass.java)

fun <C : Any> AnnotationParser<C>.installKotlinSupport(main: Executor) {
    val dispatcher = CoroutineScope(main.asCoroutineDispatcher() + SupervisorJob())
    registerCommandExecutionMethodFactory({ it.kotlinFunction != null }) { context ->
        val function = context.method().kotlinFunction!!
        if (function.isSuspend) {
            createKotlinMethodInvoker<C>(ImperiumScope.MAIN, context)
        } else {
            createKotlinMethodInvoker<C>(dispatcher, context)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <C : Any> createKotlinMethodInvoker(
    scope: CoroutineScope,
    context: MethodCommandExecutionHandler.CommandMethodContext<C>
) =
    Class.forName(
            "org.incendo.cloud.kotlin.coroutines.annotations.KotlinMethodCommandExecutionHandler")
        .kotlin
        .primaryConstructor!!
        .apply { isAccessible = true }
        .call(scope, scope.coroutineContext, context) as MethodCommandExecutionHandler<C>

fun <T> AnnotationParser<T>.registerImperiumCommand(source: LocalizationSource) =
    registerBuilderModifier(ImperiumCommand::class.java) { annotation, builder ->
        builder.commandDescription(
            LocalisableDescription(
                "imperium.command.[${annotation.pathWithoutAliases.joinToString(".")}].description",
                source))
    }

fun <C> CommandManager<C>.installCoreTranslations(extractor: LocaleExtractor<C>) {
    captionRegistry().registerProvider(TranslationBundle.core(extractor))
}

private val RANK_PARAMETER = ParserParameter("imperium:rank", TypeToken.get(Rank::class.java))

@Suppress("UNCHECKED_CAST")
private val SCOPE_PARAMETER =
    ParserParameter("imperium:scope", TypeToken.get(typeOf<List<MindustryGamemode>>().javaType))
        as ParserParameter<List<MindustryGamemode>>

private val IMPERIUM_PERMISSION = CloudKey.of("imperium:permission", Permission::class.java)

fun CommandMetaBuilder.withImperiumAnnotations(parameters: ParserParameters): CommandMetaBuilder {
    val rank = parameters.get(RANK_PARAMETER, Rank.EVERYONE)
    val scope = parameters.get(SCOPE_PARAMETER, emptyList())
    var permission = Permission.permission("imperium.rank.${rank.name.lowercase()}")
    if (scope.isNotEmpty()) {
        permission =
            Permission.allOf(
                permission,
                Permission.anyOf(
                    buildList {
                        add(Permission.permission("imperium.rank.${Rank.ADMIN.name.lowercase()}"))
                        scope.forEach {
                            add(Permission.permission("imperium.gamemode.${it.name.lowercase()}"))
                        }
                    }))
    }
    return with(IMPERIUM_PERMISSION, permission)
}

fun <T> AnnotationParser<T>.registerImperiumAnnotations() {
    manager().parserRegistry().registerAnnotationMapper(RequireRank::class.java) { annotation, _ ->
        ParserParameters.single(RANK_PARAMETER, annotation.rank)
    }
    manager().parserRegistry().registerAnnotationMapper(RequireScope::class.java) { annotation, _ ->
        ParserParameters.single(SCOPE_PARAMETER, annotation.gamemodes.toList())
    }
    manager().parserRegistry().registerAnnotationMapper(ImperiumPermission::class.java) {
        annotation,
        _ ->
        ParserParameters.empty().apply {
            store(RANK_PARAMETER, annotation.rank)
            store(SCOPE_PARAMETER, annotation.gamemodes.toList())
        }
    }
    registerBuilderDecorator { it.permission(it.meta().get(IMPERIUM_PERMISSION)) }
}
