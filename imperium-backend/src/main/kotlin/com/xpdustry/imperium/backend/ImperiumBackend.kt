// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend

import com.xpdustry.imperium.backend.account.RoleSyncListener
import com.xpdustry.imperium.backend.bridge.MindustryBridgeListener
import com.xpdustry.imperium.backend.commands.AccountCommand
import com.xpdustry.imperium.backend.commands.HistoryCommand
import com.xpdustry.imperium.backend.commands.MapCommand
import com.xpdustry.imperium.backend.commands.MapSearchCommand
import com.xpdustry.imperium.backend.commands.MapSubmitCommand
import com.xpdustry.imperium.backend.commands.ModerationCommand
import com.xpdustry.imperium.backend.commands.PingCommand
import com.xpdustry.imperium.backend.commands.PlayerCommand
import com.xpdustry.imperium.backend.commands.ServerCommand
import com.xpdustry.imperium.backend.commands.VerifyCommand
import com.xpdustry.imperium.backend.commands.WhitelistCommand
import com.xpdustry.imperium.backend.content.MindustryContentListener
import com.xpdustry.imperium.backend.metrics.MetricsListener
import com.xpdustry.imperium.backend.rest.RestListener
import com.xpdustry.imperium.backend.security.PunishmentListener
import com.xpdustry.imperium.backend.security.ReportListener
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.registerCommonModule
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger(ImperiumBackend::class.java)

class ImperiumBackend :
    BaseImperiumApplication(
        LOGGER,
        modules = {
            registerCommonModule()
            registerDiscordModule()
        },
    ) {
    override fun exit(status: ExitStatus) {
        super.exit(status)
        exitProcess(status.ordinal)
    }
}

fun main() {
    val application = ImperiumBackend()

    application.createAll()

    sequenceOf(
            MindustryBridgeListener::class,
            PingCommand::class,
            ServerCommand::class,
            ReportListener::class,
            MapCommand::class,
            VerifyCommand::class,
            ModerationCommand::class,
            PunishmentListener::class,
            RoleSyncListener::class,
            PlayerCommand::class,
            AccountCommand::class,
            WhitelistCommand::class,
            RestListener::class,
            MapSearchCommand::class,
            MapSubmitCommand::class,
            MindustryContentListener::class,
            HistoryCommand::class,
            MetricsListener::class,
        )
        .forEach(application::register)
    application.init()

    val scanner = application.instances.get<AnnotationScanner>()

    for (listener in application.listeners) {
        scanner.scan(listener)
    }

    scanner.process()

    LOGGER.info("Imperium loaded.")
}
