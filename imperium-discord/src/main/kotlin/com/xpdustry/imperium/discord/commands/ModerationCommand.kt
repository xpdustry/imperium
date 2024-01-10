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
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.identity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["punishment", "list"], Rank.MODERATOR)
    private suspend fun onPunishmentListCommand(
        actor: InteractionSender.Slash,
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
    private suspend fun onPunishmentInfoCommand(
        actor: InteractionSender.Slash,
        punishment: String
    ) {
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

    private fun Punishment.toEmbed() = Embed {
        title = "Punishment $snowflake"
        field("Target ID", target.toString(), true)
        field("Type", type.toString(), true)
        field("Reason", reason, false)
        field("Timestamp", renderer.renderInstant(snowflake.timestamp), true)
        field("Duration", renderer.renderDuration(duration), true)
        if (pardon != null) {
            field("Pardon Reason", pardon!!.reason, false)
            field("Pardon Timestamp", renderer.renderInstant(pardon!!.timestamp), true)
        }
    }

    @Command(["ban"], Rank.MODERATOR)
    private suspend fun onBanCommand(
        actor: InteractionSender.Slash,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_DAYS
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, actor, player, reason, duration.value)
    }

    @Command(["kick"], Rank.OVERSEER)
    private suspend fun onBanCommand(
        actor: InteractionSender.Slash,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.FIFTEEN_MINUTES
    ) {
        onPunishCommand("Kicked", Punishment.Type.BAN, actor, player, reason, duration.value)
    }

    @Command(["mute"], Rank.MODERATOR)
    private suspend fun onMuteCommand(
        actor: InteractionSender.Slash,
        player: Snowflake,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.ONE_DAY
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, actor, player, reason, duration.value)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        actor: InteractionSender.Slash,
        player: Snowflake,
        reason: String,
        duration: Duration
    ) {
        if (users.findBySnowflake(player) == null) {
            actor.respond("Target is not a valid IP address, UUID or USER ID.")
            return
        }
        punishments.punish(actor.member.identity, player, reason, type, duration)
        actor.respond("$verb user $player.")
    }

    @Command(["pardon"], Rank.MODERATOR)
    private suspend fun onPardonCommand(
        actor: InteractionSender.Slash,
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

        punishments.pardon(actor.member.identity, snowflake, reason)
        actor.respond("Pardoned user.")
    }
}

enum class PunishmentDuration(val value: Duration) {
    FIFTEEN_MINUTES(15.minutes),
    ONE_HOUR(1.hours),
    THREE_HOURS(3.hours),
    ONE_DAY(1.days),
    THREE_DAYS(3.days),
    ONE_WEEK(7.days),
    ONE_MONTH(30.days),
    PERMANENT(Duration.INFINITE)
}
