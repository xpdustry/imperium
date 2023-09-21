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
package com.xpdustry.imperium.discord.interaction.command.standard

import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toLongFromBase62
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.Permission
import com.xpdustry.imperium.discord.interaction.command.Command
import java.time.Duration

// TODO Add punishment list command
class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()

    // TODO Using enums is awful, use multiple commands instead
    // TODO Fix weird variable names
    @Command("punish", permission = Permission.MODERATOR)
    private suspend fun onPunishCommand(actor: InteractionActor, type: Punishment.Type, target: String, reason: String, duration: Duration? = null) {
        val subject = try {
            Punishment.Target(target.toInetAddress())
        } catch (e: Exception) {
            val user = users.findByUuid(target)
            if (user?.lastAddress == null) {
                actor.respond("Target is not a valid IP address or a valid UUID.")
                return
            }
            Punishment.Target(user.lastAddress!!, user._id)
        }

        punishments.punish(Identity.Discord(actor.user.name, actor.user.id), subject, reason, type, duration)
        actor.respond("Punished user.")
    }

    // TODO Add punishment type filter so its possible to only unmute someone for example
    @Command("pardon", permission = Permission.MODERATOR)
    private suspend fun onPardonCommand(actor: InteractionActor, id: String, reason: String) {
        val snowflake = try {
            id.toLongFromBase62()
        } catch (e: Exception) {
            actor.respond("Invalid ID.")
            return
        }

        val punishment = punishments.findById(snowflake)
        if (punishment == null) {
            actor.respond("Punishment not found.")
            return
        }

        if (punishment.pardoned) {
            actor.respond("Punishment already pardoned.")
            return
        }

        punishments.pardon(Identity.Discord(actor.user.name, actor.user.id), snowflake, reason)
        actor.respond("Pardoned user.")
    }
}
