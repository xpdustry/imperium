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
package com.xpdustry.imperium.discord.command

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumArgumentExtractor
import com.xpdustry.imperium.common.command.ImperiumCommandExtractor
import com.xpdustry.imperium.common.command.enumParser
import com.xpdustry.imperium.common.command.installCoroutineSupportImperium
import com.xpdustry.imperium.common.command.registerImperiumCommand
import com.xpdustry.imperium.common.command.registerImperiumPermission
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.localization.LocalizationSource
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.command.parser.UserParser
import com.xpdustry.imperium.discord.commands.PunishmentDuration
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import io.leangen.geantyref.TypeToken
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.discord.jda5.JDA5CommandManager
import org.incendo.cloud.discord.slash.CommandScope
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.key.CloudKey
import org.incendo.cloud.kotlin.coroutines.asParserDescriptor
import org.incendo.cloud.meta.CommandMeta
import org.incendo.cloud.parser.ParserParameter
import org.incendo.cloud.parser.ParserParameters

class CloudCommandRegistry(
    private val discord: DiscordService,
    users: UserManager,
    source: LocalizationSource
) : AnnotationScanner, ImperiumApplication.Listener {

    private val manager =
        JDA5CommandManager(ExecutionCoordinator.simpleCoordinator()) {
                InteractionSender.Slash(it.interactionEvent() as SlashCommandInteractionEvent)
            }
            .apply {
                parserRegistry()
                    .registerParser(enumParser(PunishmentDuration::class))
                    .registerParser(enumParser(Rank::class))
                    .registerParser(enumParser(MindustryGamemode::class))
                    .registerParser(
                        UserParser<InteractionSender.Slash>(users)
                            .asParserDescriptor(
                                ImperiumScope.MAIN, ImperiumScope.MAIN.coroutineContext))

                permissionPredicate { sender, permission ->
                    if (permission.isEmpty()) true
                    else if (permission.startsWith("imperium.rank."))
                        runBlocking {
                            discord.isAllowed(
                                sender.member.user,
                                Rank.valueOf(permission.removePrefix("imperium.rank.").uppercase()))
                        }
                    else false
                }

                registerCommandPostProcessor {
                    runBlocking {
                        it.commandContext()
                            .sender()
                            .interaction
                            .deferReply(it.command().commandMeta()[EPHEMERAL_CLOUD_KEY]!!)
                            .await()
                    }
                }
            }

    private val parser =
        AnnotationParser(manager, InteractionSender.Slash::class.java) {
                CommandMeta.builder()
                    .with(EPHEMERAL_CLOUD_KEY, it.get(EPHEMERAL_PARSER_KEY, true))
                    .build()
            }
            .apply {
                installCoroutineSupportImperium()
                commandExtractor(ImperiumCommandExtractor(this, InteractionSender.Slash::class))
                argumentExtractor(ImperiumArgumentExtractor(source))
                registerImperiumCommand(source)
                registerImperiumPermission()
                registerAnnotationMapper(NonEphemeral::class.java) {
                    ParserParameters.single(EPHEMERAL_PARSER_KEY, false)
                }
            }

    override fun scan(instance: Any) {
        parser.parse(instance)
    }

    override fun onImperiumInit() {
        runBlocking {
            discord.jda
                .updateCommands()
                .addCommands(
                    manager
                        .commandFactory()
                        .createCommands(CommandScope.guilds(discord.getMainServer().idLong)))
                .await()
        }
        discord.jda.addEventListener(manager.createListener())
    }
}

private val EPHEMERAL_PARSER_KEY =
    ParserParameter("imperium:ephemeral", TypeToken.get(Boolean::class.javaObjectType))
private val EPHEMERAL_CLOUD_KEY =
    CloudKey.of("imperium:ephemeral", TypeToken.get(Boolean::class.javaObjectType))
