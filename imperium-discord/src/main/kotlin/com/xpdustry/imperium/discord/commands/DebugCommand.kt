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
package com.xpdustry.imperium.discord.commands

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.discord.command.InteractionSender
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DebugCommand : ImperiumApplication.Listener {
    @Command(["debug", "log-level"], Rank.OWNER)
    private suspend fun onLogLevelCommand(sender: InteractionSender.Slash, level: String) {
        val value = Level.valueOf(level)
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as LogbackLogger
        root.setLevel(value)
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", value.toString())
        sender.respond("Log level set to $value.")
    }
}
