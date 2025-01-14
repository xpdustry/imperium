/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.identity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Greedy
import org.incendo.cloud.annotation.specifier.Quoted

class ModerationCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val config = instances.get<ImperiumConfig>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["admin"], Rank.OVERSEER)
    @ClientSide
    fun onAdminToggleCommand(sender: CommandSender) {
        sender.player.admin = !sender.player.admin
        sender.player.sendMessage("[accent]Your admin status has been set to ${sender.player.admin}")
    }

    @ImperiumCommand(["ban"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    suspend fun onBanCommand(
        sender: CommandSender,
        player: Player,
        @Quoted reason: String = UNDEFINED_REASON,
        duration: Duration = 3.days,
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, sender, player, reason, duration)
    }

    @ImperiumCommand(["freeze|f"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    suspend fun onFreezeCommand(
        sender: CommandSender,
        player: Player,
        @Quoted reason: String = UNDEFINED_REASON,
        duration: Duration = 3.hours,
    ) {
        onPunishCommand("Frozen", Punishment.Type.FREEZE, sender, player, reason, duration)
    }

    @ImperiumCommand(["mute"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    suspend fun onMuteCommand(
        sender: CommandSender,
        player: Player,
        @Quoted reason: String = UNDEFINED_REASON,
        duration: Duration = 1.days,
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, sender, player, reason, duration)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        sender: CommandSender,
        player: Player,
        reason: String,
        duration: Duration,
    ) {
        val id = punishments.punish(sender.identity, users.getByIdentity(player.identity).id, reason, type, duration)
        sender.reply("$verb user $player (${codec.encode(id)}).")
    }

    @ImperiumCommand(["pardon"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    suspend fun onPardonCommand(sender: CommandSender, punishment: String, @Greedy reason: String) {
        val id = codec.tryDecode(punishment)
        if (id == null) {
            sender.error("Invalid Punishment ID.")
            return
        }

        val entry = punishments.findById(id)
        if (entry == null) {
            sender.error("Punishment not found.")
            return
        }

        if (entry.pardon != null) {
            sender.error("Punishment already pardoned.")
            return
        }

        punishments.pardon(sender.identity, id, reason)
        sender.reply("Pardoned user.")
    }

    private val CommandSender.identity
        get() = if (isPlayer) player.identity else config.server.identity

    companion object {
        private const val UNDEFINED_REASON = "No reason provided."
    }
}
