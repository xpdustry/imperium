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
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.account.UserSettingsCommand
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatTranslatorListener
import com.xpdustry.imperium.mindustry.command.HelpCommand
import com.xpdustry.imperium.mindustry.config.ConventionListener
import com.xpdustry.imperium.mindustry.game.ImperiumLogicListener
import com.xpdustry.imperium.mindustry.history.HistoryCommand
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
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.localization.LocalizationSourceRegistry
import fr.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import java.util.Locale
import kotlin.system.exitProcess

class ImperiumPlugin : AbstractMindustryPlugin() {
    private val application = MindustryImperiumApplication(this)

    override fun onInit() {
        logger.info("Imperium plugin loaded!")
    }

    override fun onLoad() {
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
        for (listener in
            listOf(
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
            )) {
            application.register(listener)
        }
        if (application.instances.get<ServerConfig.Mindustry>().gamemode == MindustryGamemode.HUB) {
            application.register(HubListener::class)
        }
        application.init()

        val registry = application.instances.get<CommandRegistry>()
        application.listeners.forEach { registry.parse(it) }

        // https://github.com/Anuken/Arc/pull/158
        if (getMindustryVersion().build < 147) {
            Core.app =
                object : Application by Core.app {
                    override fun removeListener(listener: ApplicationListener) {
                        synchronized(listeners) {
                            listeners.replace(listener, object : ApplicationListener {})
                        }
                    }
                }
        }
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
        when (status) {
            ExitStatus.EXIT,
            ExitStatus.INIT_FAILURE -> Core.app.exit()
            ExitStatus.RESTART -> Core.app.restart()
        }
    }

    override fun onListenerRegistration(listener: ImperiumApplication.Listener) {
        super.onListenerRegistration(listener)
        DistributorProvider.get().eventBus.parse(plugin, listener)
        DistributorProvider.get().pluginScheduler.parse(plugin, listener)
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
