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
package com.xpdustry.imperium.mindustry

import arc.Application
import arc.ApplicationListener
import arc.Core
import com.xpdustry.distributor.DistributorProvider
import com.xpdustry.distributor.annotation.PluginAnnotationScanner
import com.xpdustry.distributor.localization.LocalizationSourceRegistry
import com.xpdustry.distributor.plugin.AbstractMindustryPlugin
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.version.MindustryVersion
import com.xpdustry.imperium.common.webhook.WebhookMessage
import com.xpdustry.imperium.common.webhook.WebhookMessageSender
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatTranslatorListener
import com.xpdustry.imperium.mindustry.command.HelpCommand
import com.xpdustry.imperium.mindustry.config.ConventionListener
import com.xpdustry.imperium.mindustry.game.GameListener
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener
import com.xpdustry.imperium.mindustry.game.TipListener
import com.xpdustry.imperium.mindustry.history.HistoryCommand
import com.xpdustry.imperium.mindustry.misc.ImperiumMetadataChunkReader
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
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
import com.xpdustry.imperium.mindustry.world.SwitchCommand
import com.xpdustry.imperium.mindustry.world.WaveCommand
import com.xpdustry.imperium.mindustry.world.WelcomeListener
import java.util.Locale
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import mindustry.io.SaveVersion

class ImperiumPlugin : AbstractMindustryPlugin() {
    private val application = MindustryImperiumApplication(this)
    internal val scanner =
        PluginAnnotationScanner.create(this)
            .register(PluginAnnotationScanner.createTaskListener())
            .register(PluginAnnotationScanner.createEventListener())

    override fun onInit() {
        // https://github.com/Anuken/Arc/pull/158
        if (getMindustryVersion().build < 147 ||
            getMindustryVersion().type == MindustryVersion.Type.BLEEDING_EDGE) {
            Core.app =
                object : Application by Core.app {
                    override fun removeListener(listener: ApplicationListener) {
                        post { synchronized(listeners) { listeners.remove(listener) } }
                    }
                }
        }
    }

    override fun onLoad() {

        logger.info("Imperium plugin initialized!")
        SaveVersion.addCustomChunk("imperium", ImperiumMetadataChunkReader)

        val source =
            LocalizationSourceRegistry.create(application.instances.get<ImperiumConfig>().language)
        source.registerAll(
            Locale.ENGLISH,
            "com/xpdustry/imperium/bundles/bundle",
            this::class.java.classLoader,
        )
        source.registerAll(
            Locale.FRENCH,
            "com/xpdustry/imperium/bundles/bundle",
            this::class.java.classLoader,
        )
        DistributorProvider.get().globalLocalizationSource.addLocalizationSource(source)

        application.instances.createSingletons()
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
                TipListener::class)
            .forEach(application::register)
        if (application.instances.get<ServerConfig.Mindustry>().gamemode == MindustryGamemode.HUB) {
            application.register(HubListener::class)
        }
        application.init()

        runBlocking {
            application.instances
                .get<WebhookMessageSender>()
                .send(WebhookMessage(content = "The server has started."))
        }

        val registry = application.instances.get<CommandRegistry>()
        application.listeners.forEach { registry.parse(it) }
        logger.info("Parsed Imperium commands!")
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }
}

private class MindustryImperiumApplication(private val plugin: ImperiumPlugin) :
    SimpleImperiumApplication(MindustryModule(plugin)) {
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

    override fun onListenerRegistration(listener: ImperiumApplication.Listener) {
        super.onListenerRegistration(listener)
        plugin.scanner.scan(listener)
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
