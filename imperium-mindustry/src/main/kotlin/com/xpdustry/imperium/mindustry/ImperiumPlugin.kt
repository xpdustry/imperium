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
import arc.util.CommandHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.misc.ExitStatus
import com.xpdustry.imperium.mindustry.account.AccountCommand
import com.xpdustry.imperium.mindustry.account.AccountListener
import com.xpdustry.imperium.mindustry.chat.BridgeChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatMessageListener
import com.xpdustry.imperium.mindustry.chat.ChatTranslatorListener
import com.xpdustry.imperium.mindustry.command.ImperiumPluginCommandManager
import com.xpdustry.imperium.mindustry.history.HistoryCommand
import com.xpdustry.imperium.mindustry.horny.HornyDetectionListener
import com.xpdustry.imperium.mindustry.listener.ConventionListener
import com.xpdustry.imperium.mindustry.moderation.ReportCommand
import com.xpdustry.imperium.mindustry.verification.VerificationListener
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import kotlin.system.exitProcess

class ImperiumPlugin : AbstractMindustryPlugin() {
    internal val serverCommandManager = ImperiumPluginCommandManager(this)
    internal val clientCommandManager = ImperiumPluginCommandManager(this)
    private val application = MindustryImperiumApplication(this)

    override fun onInit() {
        logger.info("Imperium plugin loaded!")
    }

    override fun onServerCommandsRegistration(handler: CommandHandler) {
        serverCommandManager.initialize(handler)
    }

    override fun onClientCommandsRegistration(handler: CommandHandler) {
        clientCommandManager.initialize(handler)
    }

    override fun onLoad() {
        application.instances.createSingletons()
        application.register(ConventionListener::class)
        application.register(VerificationListener::class)
        application.register(ChatTranslatorListener::class)
        application.register(AccountListener::class)
        application.register(AccountCommand::class)
        application.register(ChatMessageListener::class)
        application.register(HistoryCommand::class)
        application.register(BridgeChatMessageListener::class)
        application.register(ReportCommand::class)
        application.register(HornyDetectionListener::class)

        application.init()
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }
}

private class MindustryImperiumApplication(private val plugin: ImperiumPlugin) : SimpleImperiumApplication(mindustryModule(plugin)) {
    private var exited = false
    override fun exit(status: ExitStatus) {
        if (exited) return
        exited = true
        super.exit(status)
        when (status) {
            ExitStatus.EXIT, ExitStatus.INIT_FAILURE -> Core.app.exit()
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
    addListener(object : ApplicationListener {
        override fun dispose() {
            Core.settings.autosave()
            exitProcess(2)
        }
    })
}
