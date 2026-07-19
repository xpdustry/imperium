// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.dependency.Inject
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

@Inject
class ModerationCommand(
    private val punishments: PunishmentManager,
    private val users: UserManager,
    private val config: ImperiumConfig,
    private val codec: IdentifierCodec,
) : ImperiumApplication.Listener {

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
