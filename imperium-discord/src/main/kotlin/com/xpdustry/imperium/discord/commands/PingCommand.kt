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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import kotlinx.coroutines.future.await

class PingCommand : ImperiumApplication.Listener {
    @Command(["ping"])
    @NonEphemeral
    suspend fun onPingCommand(actor: InteractionSender) =
        actor.respond(
            "pong with **${actor.discord.measureRestLatency().await().toMillis()}** milliseconds of latency!")
}
