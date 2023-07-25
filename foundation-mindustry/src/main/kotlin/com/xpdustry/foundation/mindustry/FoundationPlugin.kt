/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry

import arc.Application
import arc.ApplicationListener
import arc.Core
import arc.util.CommandHandler
import com.xpdustry.foundation.common.application.FoundationApplication
import com.xpdustry.foundation.common.application.SimpleFoundationApplication
import com.xpdustry.foundation.common.misc.ExitStatus
import com.xpdustry.foundation.mindustry.account.AccountCommand
import com.xpdustry.foundation.mindustry.account.AccountListener
import com.xpdustry.foundation.mindustry.chat.ChatMessageListener
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.history.HistoryCommand
import com.xpdustry.foundation.mindustry.listener.ConventionListener
import com.xpdustry.foundation.mindustry.translator.TranslatorListener
import com.xpdustry.foundation.mindustry.verification.VerificationListener
import fr.xpdustry.distributor.api.DistributorProvider
import fr.xpdustry.distributor.api.plugin.AbstractMindustryPlugin
import reactor.core.publisher.Hooks
import kotlin.system.exitProcess

class FoundationPlugin : AbstractMindustryPlugin() {
    init {
        Hooks.onOperatorDebug()
    }

    internal val serverCommandManager = FoundationPluginCommandManager(this)
    internal val clientCommandManager = FoundationPluginCommandManager(this)
    private val application: MindustryFoundationApplication = MindustryFoundationApplication()

    override fun onInit() {
        logger.info("Foundation plugin loaded!")
    }

    override fun onServerCommandsRegistration(handler: CommandHandler) {
        serverCommandManager.initialize(handler)
    }

    override fun onClientCommandsRegistration(handler: CommandHandler) {
        clientCommandManager.initialize(handler)
    }

    override fun onLoad() {
        // Listeners
        application.register(ConventionListener::class)
        application.register(VerificationListener::class)
        application.register(TranslatorListener::class)
        application.register(AccountListener::class)
        application.register(AccountCommand::class)
        application.register(ChatMessageListener::class)
        application.register(HistoryCommand::class)

        application.instances.createSingletons()
        application.init()
    }

    override fun onExit() {
        application.exit(ExitStatus.EXIT)
    }

    private inner class MindustryFoundationApplication : SimpleFoundationApplication(mindustryModule(this@FoundationPlugin)) {
        override fun exit(status: ExitStatus) {
            super.exit(status)
            when (status) {
                ExitStatus.EXIT -> Core.app.exit()
                ExitStatus.RESTART -> Core.app.restart()
            }
        }

        override fun onListenerRegistration(listener: FoundationApplication.Listener) {
            super.onListenerRegistration(listener)
            DistributorProvider.get().eventBus.parse(this@FoundationPlugin, listener)
            DistributorProvider.get().pluginScheduler.parse(this@FoundationPlugin, listener)
        }
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
