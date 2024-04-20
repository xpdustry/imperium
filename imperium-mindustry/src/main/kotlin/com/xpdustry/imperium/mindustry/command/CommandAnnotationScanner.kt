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
package com.xpdustry.imperium.mindustry.command

import arc.util.CommandHandler
import com.xpdustry.distributor.annotation.PluginAnnotationScanner
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.distributor.command.DescriptionFacade
import com.xpdustry.distributor.command.cloud.ArcCommandManager
import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.imperium.common.command.ImperiumArgumentExtractor
import com.xpdustry.imperium.common.command.ImperiumCommandExtractor
import com.xpdustry.imperium.common.command.LocalisableDescription
import com.xpdustry.imperium.common.command.installCoreTranslations
import com.xpdustry.imperium.common.command.installKotlinSupport
import com.xpdustry.imperium.common.command.registerImperiumCommand
import com.xpdustry.imperium.common.command.registerImperiumPermission
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.localization.LocalizationSource
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import java.util.Optional
import java.util.concurrent.Executor
import kotlin.reflect.full.hasAnnotation
import mindustry.Vars
import mindustry.server.ServerControl
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.caption.CaptionFormatter
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.setting.ManagerSetting
import org.incendo.cloud.translations.TranslationBundle

class CommandAnnotationScanner(
    private val plugin: MindustryPlugin,
    private val config: ImperiumConfig,
    private val source: LocalizationSource,
    private val main: Executor
) : PluginAnnotationScanner<Unit> {

    private lateinit var clientCommandManager: AnnotationParser<CommandSender>
    private lateinit var serverCommandManager: AnnotationParser<CommandSender>
    private var initialized = false

    override fun scan(instance: Any): Optional<Unit> {
        if (!initialized) {
            clientCommandManager =
                createArcCommandManagerParser<ClientSide>(plugin, Vars.netServer.clientCommands)
            serverCommandManager =
                createArcCommandManagerParser<ServerSide>(plugin, ServerControl.instance.handler)
            initialized = true
        }
        clientCommandManager.parse(instance)
        serverCommandManager.parse(instance)
        return Optional.empty<Unit>()
    }

    private inline fun <reified T : Annotation> createArcCommandManagerParser(
        plugin: MindustryPlugin,
        handler: CommandHandler
    ): AnnotationParser<CommandSender> {
        val manager =
            ArcCommandManager(
                plugin, ExecutionCoordinator.asyncCoordinator(), SenderMapper.identity()) {
                    if (it is LocalisableDescription)
                        DescriptionFacade.localized(it.key, config.language)
                    else DescriptionFacade.text(it.textDescription())
                }

        manager.initialize(handler)
        manager.settings().set(ManagerSetting.OVERRIDE_EXISTING_COMMANDS, true)

        manager.installCoreTranslations(CommandSender::getLocale)
        manager.captionFormatter(CaptionFormatter.placeholderReplacing())
        manager
            .captionRegistry()
            .registerProvider(
                TranslationBundle.resourceBundle(
                    "com/xpdustry/imperium/mindustry/cloud_bundle", CommandSender::getLocale))

        return AnnotationParser(manager, CommandSender::class.java).apply {
            installKotlinSupport(main)
            commandExtractor(
                ImperiumCommandExtractor(this, CommandSender::class) { it.hasAnnotation<T>() })
            argumentExtractor(ImperiumArgumentExtractor(source))
            registerImperiumCommand(source)
            registerImperiumPermission()
        }
    }
}
