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
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.command.CommandRegistry
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.discord.account.RoleSyncListener
import com.xpdustry.imperium.discord.bridge.BridgeListener
import com.xpdustry.imperium.discord.commands.DebugCommand
import com.xpdustry.imperium.discord.commands.MapCommand
import com.xpdustry.imperium.discord.commands.ModerationCommand
import com.xpdustry.imperium.discord.commands.PingCommand
import com.xpdustry.imperium.discord.commands.PlayerCommand
import com.xpdustry.imperium.discord.commands.SchematicCommand
import com.xpdustry.imperium.discord.commands.ServerCommand
import com.xpdustry.imperium.discord.commands.VerifyCommand
import com.xpdustry.imperium.discord.security.PunishmentListener
import com.xpdustry.imperium.discord.security.ReportListener
import java.util.Scanner
import kotlin.system.exitProcess

class ImperiumDiscord : SimpleImperiumApplication(DiscordModule()) {
    override fun exit(status: ExitStatus) {
        super.exit(status)
        exitProcess(status.ordinal)
    }
}

fun main() {
    val application = ImperiumDiscord()

    application.instances.createSingletons()
    for (listener in
        listOf(
            BridgeListener::class,
            PingCommand::class,
            ServerCommand::class,
            ReportListener::class,
            MapCommand::class,
            SchematicCommand::class,
            VerifyCommand::class,
            ModerationCommand::class,
            PunishmentListener::class,
            RoleSyncListener::class,
            DebugCommand::class,
            PlayerCommand::class)) {
        application.register(listener)
    }

    val commands = application.instances.get<CommandRegistry>("slash")
    val buttons = application.instances.get<CommandRegistry>("button")
    for (listener in application.listeners) {
        commands.parse(listener)
        buttons.parse(listener)
    }

    application.init()

    val scanner = Scanner(System.`in`)
    while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        if (line == "exit") {
            break
        }
        logger<ImperiumDiscord>().info("Type 'exit' to exit.")
    }

    application.exit(ExitStatus.EXIT)
}
