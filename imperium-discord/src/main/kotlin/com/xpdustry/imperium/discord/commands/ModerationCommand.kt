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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.timestamp
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.misc.identity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.javacord.api.entity.message.embed.EmbedBuilder

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["punishment", "list"], Rank.MODERATOR)
    private suspend fun onPunishmentListCommand(
        actor: InteractionSender,
        player: Snowflake,
        @Min(0) page: Int = 0
    ) {
        val result = punishments.findAllByUser(player).drop(page * 10).take(10).toList()
        if (result.isEmpty()) {
            actor.respond("No punishments found.")
            return
        }
        actor.respond(*result.map { it.toEmbed() }.toTypedArray())
    }

    @Command(["punishment", "info"], Rank.MODERATOR)
    private suspend fun onPunishmentInfoCommand(actor: InteractionSender, punishment: String) {
        val id = punishment.toLongOrNull()
        if (id == null) {
            actor.respond("Invalid id.")
            return
        }
        val result = punishments.findBySnowflake(id)
        if (result == null) {
            actor.respond("No punishment found.")
            return
        }
        actor.respond(result.toEmbed())
    }

    private fun Punishment.toEmbed() =
        EmbedBuilder()
            .setTitle("Punishment $snowflake")
            .addField("Target ID", target.toString(), true)
            .addField("Type", type.toString(), true)
            .addField("Reason", reason, false)
            .addField("Timestamp", renderer.renderInstant(snowflake.timestamp), true)
            .addField("Duration", renderer.renderDuration(duration), true)
            .addField("Pardoned", if (pardon != null) "Yes" else "No", true)
            .apply {
                if (pardon != null) {
                    addField("Pardon Reason", pardon!!.reason, false)
                    addField("Pardon Timestamp", renderer.renderInstant(pardon!!.timestamp), true)
                }
            }

    @Command(["ban"], Rank.MODERATOR)
    private suspend fun onBanCommand(
        actor: InteractionSender,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_HOURS
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, actor, player, reason, duration.value)
    }

    @Command(["mute"], Rank.MODERATOR)
    private suspend fun onMuteCommand(
        actor: InteractionSender,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.ONE_HOUR
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, actor, player, reason, duration.value)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        actor: InteractionSender,
        player: Snowflake,
        reason: String,
        duration: Duration
    ) {
        if (users.findBySnowflake(player) == null) {
            actor.respond("Target is not a valid IP address, UUID or USER ID.")
            return
        }
        punishments.punish(actor.user.identity, player, reason, type, duration)
        actor.respond("$verb user $player.")
    }

    @Command(["pardon"], Rank.MODERATOR)
    private suspend fun onPardonCommand(
        actor: InteractionSender,
        punishment: String,
        reason: String
    ) {
        val snowflake = punishment.toLongOrNull()
        if (snowflake == null) {
            actor.respond("Invalid Punishment ID.")
            return
        }

        val entry = punishments.findBySnowflake(snowflake)
        if (entry == null) {
            actor.respond("Punishment not found.")
            return
        }

        if (entry.pardon != null) {
            actor.respond("Punishment already pardoned.")
            return
        }

        punishments.pardon(actor.user.identity, snowflake, reason)
        actor.respond("Pardoned user.")
    }
}

enum class PunishmentDuration(val value: Duration) {
    ONE_HOUR(1.hours),
    THREE_HOURS(3.hours),
    ONE_DAY(1.days),
    THREE_DAYS(3.days),
    ONE_WEEK(7.days),
    ONE_MONTH(30.days),
    PERMANENT(Duration.INFINITE)
}
