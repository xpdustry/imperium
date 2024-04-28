/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry

import arc.Application
import arc.ApplicationListener
import arc.Core
import com.xpdustry.distributor.DistributorProvider
import com.xpdustry.distributor.annotation.PluginAnnotationScanner
import com.xpdustry.distributor.annotation.method.MethodAnnotationScanner
import com.xpdustry.distributor.localization.LocalizationSourceRegistry
import com.xpdustry.distributor.permission.rank.RankPermissionSource
import com.xpdustry.distributor.permission.rank.RankProvider
import com.xpdustry.distributor.plugin.AbstractMindustryPlugin
import com.xpdustry.distributor.util.Priority
import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.registerCommonModule
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatTranslatorListener
import com.xpdustry.imperium.mindustry.command.CommandAnnotationScanner
import com.xpdustry.imperium.mindustry.command.HelpCommand
import com.xpdustry.imperium.mindustry.config.ConventionListener
import com.xpdustry.imperium.mindustry.game.GameListener
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener
import com.xpdustry.imperium.mindustry.game.RatingListener
import com.xpdustry.imperium.mindustry.game.TipListener
import com.xpdustry.imperium.mindustry.history.HistoryCommand
import com.xpdustry.imperium.mindustry.misc.ImperiumMetadataChunkReader
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
import com.xpdustry.imperium.mindustry.permission.ImperiumRankPermissionSource
import com.xpdustry.imperium.mindustry.permission.ImperiumRankProvider
import com.xpdustry.imperium.mindustry.security.AdminRequestListener
import com.xpdustry.imperium.mindustry.security.AntiEvadeListener
import com.xpdustry.imperium.mindustry.security.GatekeeperListener
import com.xpdustry.imperium.mindustry.security.LogicImageListener
import com.xpdustry.imperium.mindustry.security.PunishmentListener
import com.xpdustry.imperium.mindustry.security.ReportCommand
import com.xpdustry.imperium.mindustry.security.VoteKickCommand
import com.xpdustry.imperium.mindustry.telemetry.DumpCommand
import com.xpdustry.imperium.mindustry.world.CoreBlockListener
import com.xpdustry.imperium.mindustry.world.ExcavateCommand
import com.xpdustry.imperium.mindustry.world.HubListener
import com.xpdustry.imperium.mindustry.world.KillAllCommand
import com.xpdustry.imperium.mindustry.world.MapListener
import com.xpdustry.imperium.mindustry.world.ResourceHudListener
import com.xpdustry.imperium.mindustry.world.RockTheVoteCommand
import com.xpdustry.imperium.mindustry.world.SetTileCommand
import com.xpdustry.imperium.mindustry.world.SpawnCommand
import com.xpdustry.imperium.mindustry.world.SwitchCommand
import com.xpdustry.imperium.mindustry.world.WaveCommand
import com.xpdustry.imperium.mindustry.world.WelcomeListener
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import mindustry.io.SaveVersion

class ImperiumPlugin : AbstractMindustryPlugin() {
    private val application = MindustryImperiumApplication()
    private lateinit var scanner: PluginAnnotationScanner<*>

    override fun onInit() {
        // https://github.com/Anuken/Arc/pull/158
        if (getMindustryVersion().build < 147) {
            Core.app =
                object : Application by Core.app {
                    override fun removeListener(listener: ApplicationListener) {
                        post { synchronized(listeners) { listeners.remove(listener) } }
                    }
                }
        }
    }

    override fun onLoad() {
        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader)

        application.instances.registerCommonModule()
        application.instances.registerMindustryModule(this)
        application.instances.createAll()

        val provider = ImperiumRankProvider(application.instances.get())
        application.register(provider)
        DistributorProvider.get()
            .serviceManager
            .register(this, RankProvider::class.java, Priority.NORMAL, provider)

        val source = ImperiumRankPermissionSource(application.instances.get())
        DistributorProvider.get()
            .serviceManager
            .register(this, RankPermissionSource::class.java, Priority.NORMAL, source)

        DistributorProvider.get()
            .globalLocalizationSource
            .addLocalizationSource(
                LocalizationSourceRegistry.create(
                        application.instances.get<ImperiumConfig>().language)
                    .apply {
                        application.instances.get<ImperiumConfig>().supportedLanguages.forEach {
                            registerAll(
                                it,
                                "com/xpdustry/imperium/bundles/bundle",
                                ImperiumPlugin::class.java.classLoader,
                            )
                        }
                    })

        sequenceOf(
                ConventionListener::class,
                GatekeeperListener::class,
                ChatTranslatorListener::class,
                AccountListener::class,
                AccountCommand::class,
                ChatMessageListener::class,
                HistoryCommand::class,
                BridgeChatMessageListener::class,
                ReportCommand::class,
                LogicImageListener::class,
                AdminRequestListener::class,
                PunishmentListener::class,
                MapListener::class,
                VoteKickCommand::class,
                ExcavateCommand::class,
                RockTheVoteCommand::class,
                CoreBlockListener::class,
                HelpCommand::class,
                WaveCommand::class,
                KillAllCommand::class,
                DumpCommand::class,
                SwitchCommand::class,
                UserSettingsCommand::class,
                WelcomeListener::class,
                ResourceHudListener::class,
                ImperiumLogicListener::class,
                AntiEvadeListener::class,
                GameListener::class,
                TipListener::class,
                RatingListener::class,
                SpawnCommand::class,
                SetTileCommand::class)
            .forEach(application::register)

        if (application.instances.get<MindustryConfig>().gamemode == MindustryGamemode.HUB) {
            application.register(HubListener::class)
        } else {
            Core.settings.remove("totalPlayers")
        }

        application.init()

        scanner =
            PluginAnnotationScanner.list(
                MethodAnnotationScanner.create(this)
                    .register(MethodAnnotationScanner.EVENT_HANDLER_PAIR)
                    .register(MethodAnnotationScanner.TASK_HANDLER_PAIR),
                CommandAnnotationScanner(this, application.instances.get()))

        application.listeners.forEach(scanner::scan)

        runBlocking {
            application.instances
                .get<WebhookMessageSender>()
                .send(WebhookMessage(content = "The server has started."))
        }

        logger.info("Imperium plugin Loaded!")
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }

    private inner class MindustryImperiumApplication : BaseImperiumApplication(logger) {
        private var exited = false

        override fun exit(status: ExitStatus) {
            if (exited) return
            exited = true
            super.exit(status)
            runBlocking {
                instances
                    .get<WebhookMessageSender>()
                    .send(WebhookMessage(content = "The server has exit with $status code."))
            }
            when (status) {
                ExitStatus.EXIT,
                ExitStatus.INIT_FAILURE -> Core.app.exit()
                ExitStatus.RESTART -> Core.app.restart()
            }
        }
    }
}

// Very hacky way to restart the server
private fun Application.restart() {
    exit()
    addListener(
        object : ApplicationListener {
            override fun dispose() {
                Core.settings.autosave()
                exitProcess(2)
            }
        })
}
