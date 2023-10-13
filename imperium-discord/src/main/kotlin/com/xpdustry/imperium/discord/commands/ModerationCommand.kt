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
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.Permission
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.image.LogicImageAnalysis
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toBase62
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toLongFromBase62
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.discord.command.InteractionSender
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.time.Duration

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val analysis = instances.get<LogicImageAnalysis>()

    @Command(["punishment", "list"], Permission.MODERATOR)
    private suspend fun onPunishmentListCommand(actor: InteractionSender, target: String, @Min(0) page: Int = 0) {
        val flow = try {
            val address = target.toInetAddress()
            punishments.findAllByAddress(address)
        } catch (e: Exception) {
            punishments.findAllByUuid(target)
        }

        val result = flow.drop(page * 10).take(10).toList()
        if (result.isEmpty()) {
            actor.respond("No punishments found.")
            return
        }

        val embeds = Array<EmbedBuilder>(result.size) {
            val punishment = result[it]
            val embed = EmbedBuilder()
                .setTitle("Punishment ${punishment._id.toBase62()}")
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
                embed.addField("Pardon Timestamp", punishment.pardon!!.timestamp.toString(), true)
            }
            embed
        }

        actor.respond(*embeds)
    }

    @Command(["kick"], Permission.MODERATOR)
    private suspend fun onKickCommand(actor: InteractionSender, target: String, reason: String, duration: Duration? = null) {
        onPunishCommand("Kicked", Punishment.Type.KICK, actor, target, reason, duration)
    }

    @Command(["ban"], Permission.MODERATOR)
    private suspend fun onBanCommand(actor: InteractionSender, target: String, reason: String) {
        onPunishCommand("Banned", Punishment.Type.BAN, actor, target, reason, null)
    }

    @Command(["mute"], Permission.MODERATOR)
    private suspend fun onMuteCommand(actor: InteractionSender, target: String, reason: String, duration: Duration? = null) {
        onPunishCommand("Muted", Punishment.Type.MUTE, actor, target, reason, duration)
    }

    private suspend fun onPunishCommand(verb: String, type: Punishment.Type, actor: InteractionSender, target: String, reason: String, duration: Duration?) {
        val lookup = try {
            Punishment.Target(target.toInetAddress())
        } catch (e: Exception) {
            val user = users.findByUuid(target)
            if (user?.lastAddress == null) {
                actor.respond("Target is not a valid IP address or a valid UUID.")
                return
            }
            Punishment.Target(user.lastAddress!!, user._id)
        }

        punishments.punish(Identity.Discord(actor.user.name, actor.user.id), lookup, reason, type, duration)
        actor.respond("$verb user $target.")
    }

    @Command(["pardon"], Permission.MODERATOR)
    private suspend fun onPardonCommand(actor: InteractionSender, id: String, reason: String) {
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

    @Command(["punishment", "nsfw", "show"], Permission.MODERATOR)
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

    @Command(["punishment", "nsfw", "safety"], Permission.MODERATOR)
    private suspend fun onPunishmentNsfwMark(actor: InteractionSender, id: ObjectId, unsafe: Boolean) {
        val result = analysis.findHashedImageById(id)
        if (result == null) {
            actor.respond("NSFW Image not found.")
            return
        }
        analysis.updateSafetyById(result.first._id, unsafe)
        actor.respond("Updated safety of NSFW to ${if (unsafe) "unsafe" else "safe"}.")
    }
}
