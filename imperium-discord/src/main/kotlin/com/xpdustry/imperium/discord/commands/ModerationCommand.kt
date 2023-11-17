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

import com.xpdustry.imperium.common.account.UserManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.image.LogicImageAnalysis
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.permission.Role
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.misc.identity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.javacord.api.entity.message.embed.EmbedBuilder

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val analysis = instances.get<LogicImageAnalysis>()
    private val tracker = instances.get<PlayerTracker>()

    @Command(["punishment", "list"], Role.MODERATOR)
    private suspend fun onPunishmentListCommand(
        actor: InteractionSender,
        target: String,
        @Min(0) page: Int = 0
    ) {
        val flow =
            try {
                val address = target.toInetAddress()
                punishments.findAllByAddress(address)
            } catch (e: Exception) {
                // TODO This is goofy, punishment should store user IDs instead of UUIDs
                if (ObjectId.isValid(target)) {
                    users.findById(ObjectId(target))?.uuid?.let { punishments.findAllByUuid(it) }
                        ?: emptyFlow()
                } else {
                    punishments.findAllByUuid(target)
                }
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
                        .setTitle("Punishment `${punishment._id}`")
                        .addField("Target IP", punishment.target.address.hostAddress, true)
                        .addField("Target UUID", punishment.target.uuid ?: "N/A", true)
                        .addField("Type", punishment.type.toString(), true)
                        .addField("Reason", punishment.reason, false)
                        .addField("Timestamp", punishment.timestamp.toString(), true)
                        .addField("Duration", punishment.duration?.toString() ?: "Permanent", true)
                        .addField("Pardoned", if (punishment.pardoned) "Yes" else "No", true)
                        .setTimestamp(punishment.timestamp)
                if (punishment.pardoned) {
                    embed.addField("Pardon Reason", punishment.pardon!!.reason, false)
                    embed.addField(
                        "Pardon Timestamp", punishment.pardon!!.timestamp.toString(), true)
                }
                embed
            }

        actor.respond(*embeds)
    }

    @Command(["ban"], Role.MODERATOR)
    private suspend fun onBanCommand(
        actor: InteractionSender,
        target: String,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_HOURS
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, actor, target, reason, duration.value)
    }

    @Command(["mute"], Role.MODERATOR)
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
            val uuid =
                target.toLongOrNull()?.let { tracker.getPlayerEntry(it) }?.player?.uuid ?: target
            var user = users.findByUuid(uuid)
            if (user == null && ObjectId.isValid(target)) {
                user = users.findById(ObjectId(target))
            }
            if (user?.lastAddress == null) {
                actor.respond("Target is not a valid IP address, UUID, TEMP-ID or USER ID.")
                return
            }
            lookup = Punishment.Target(user.lastAddress!!, user.uuid)
        }

        punishments.punish(
            actor.user.identity,
            lookup,
            reason,
            type,
            if (duration.isInfinite()) null else duration.toJavaDuration())
        actor.respond("$verb user $target.")
    }

    @Command(["pardon"], Role.MODERATOR)
    private suspend fun onPardonCommand(actor: InteractionSender, id: String, reason: String) {
        val snowflake = id.toLongOrNull()
        if (snowflake == null) {
            actor.respond("Invalid Punishment ID.")
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

        punishments.pardon(actor.user.identity, snowflake, reason)
        actor.respond("Pardoned user.")
    }

    @Command(["punishment", "image", "show"], Role.MODERATOR)
    private suspend fun onPunishmentNsfwShow(actor: InteractionSender, id: ObjectId) {
        val result = analysis.findHashedImageById(id)
        if (result == null) {
            actor.respond("NSFW Image not found.")
            return
        }
        actor.respond {
            addAttachmentAsSpoiler(result.second.getData(), "image.jpg")
            setContent(
                buildString {
                    appendLine("**ID**: ${result.first._id.toHexString()}")
                    appendLine("**Hashes**: ${result.first.hashes.size}")
                    appendLine("**Unsafe**: ${result.first.unsafe}")
                },
            )
        }
    }

    @Command(["punishment", "image", "set"], Role.MODERATOR)
    private suspend fun onPunishmentNsfwMark(
        actor: InteractionSender,
        id: ObjectId,
        unsafe: Boolean
    ) {
        val result = analysis.findHashedImageById(id)
        if (result == null) {
            actor.respond("NSFW Image not found.")
            return
        }
        analysis.updateSafetyById(result.first._id, unsafe)
        actor.respond("Updated safety of NSFW to ${if (unsafe) "unsafe" else "safe"}.")
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
