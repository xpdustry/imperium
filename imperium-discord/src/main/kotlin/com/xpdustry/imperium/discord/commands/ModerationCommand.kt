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
import com.xpdustry.imperium.common.misc.isCRC32Muuid
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.snowflake.timestamp
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.misc.identity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.javacord.api.entity.message.embed.EmbedBuilder

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val renderer = instances.get<TimeRenderer>()

    @Command(["punishment", "list"], Rank.MODERATOR)
    private suspend fun onPunishmentListCommand(
        actor: InteractionSender,
        target: String,
        @Min(0) page: Int = 0
    ) {
        var flow = emptyFlow<Punishment>()
        val address = target.toInetAddressOrNull()
        if (address != null) {
            flow = merge(flow, punishments.findAllByAddress(address))
        }
        val long = target.toLongOrNull()
        if (long != null) {
            val user = users.findBySnowflake(long)
            if (user != null) {
                flow = merge(flow, punishments.findAllByUuid(user.uuid))
            }
        }
        if (target.isCRC32Muuid()) {
            flow = merge(flow, punishments.findAllByUuid(target))
        }

        val result = flow.drop(page * 10).take(10).toList()
        if (result.isEmpty()) {
            actor.respond("No punishments found.")
            return
        }

        val embeds =
            Array<EmbedBuilder>(result.size) {
                val punishment = result[it]
                val embed =
                    EmbedBuilder()
                        .setTitle("Punishment `${punishment.snowflake}`")
                        .addField("Target IP", punishment.target.address.hostAddress, true)
                        .addField("Target UUID", punishment.target.uuid ?: "N/A", true)
                        .addField("Type", punishment.type.toString(), true)
                        .addField("Reason", punishment.reason, false)
                        .addField(
                            "Timestamp",
                            renderer.renderInstant(punishment.snowflake.timestamp),
                            true)
                        .addField("Duration", renderer.renderDuration(punishment.duration), true)
                        .addField("Pardoned", if (punishment.pardon != null) "Yes" else "No", true)
                if (punishment.pardon != null) {
                    embed.addField("Pardon Reason", punishment.pardon!!.reason, false)
                    embed.addField(
                        "Pardon Timestamp",
                        renderer.renderInstant(punishment.pardon!!.timestamp),
                        true)
                }
                embed
            }

        actor.respond(*embeds)
    }

    @Command(["ban"], Rank.MODERATOR)
    private suspend fun onBanCommand(
        actor: InteractionSender,
        target: String,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_HOURS
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, actor, target, reason, duration.value)
    }

    @Command(["mute"], Rank.MODERATOR)
    private suspend fun onMuteCommand(
        actor: InteractionSender,
        target: String,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.ONE_HOUR
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, actor, target, reason, duration.value)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        actor: InteractionSender,
        target: String,
        reason: String,
        duration: Duration
    ) {
        var lookup = target.toInetAddressOrNull()?.let(Punishment::Target)
        if (lookup == null) {
            var user = users.findByUuid(target)
            if (user == null && target.toLongOrNull() != null) {
                user = users.findBySnowflake(target.toLong())
            }
            if (user == null) {
                actor.respond("Target is not a valid IP address, UUID or USER ID.")
                return
            }
            lookup = Punishment.Target(user.lastAddress, user.uuid)
        }

        punishments.punish(actor.user.identity, lookup, reason, type, duration)
        actor.respond("$verb user $target.")
    }

    @Command(["pardon"], Rank.MODERATOR)
    private suspend fun onPardonCommand(actor: InteractionSender, id: String, reason: String) {
        val snowflake = id.toLongOrNull()
        if (snowflake == null) {
            actor.respond("Invalid Punishment ID.")
            return
        }

        val punishment = punishments.findBySnowflake(snowflake)
        if (punishment == null) {
            actor.respond("Punishment not found.")
            return
        }

        if (punishment.pardon != null) {
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
