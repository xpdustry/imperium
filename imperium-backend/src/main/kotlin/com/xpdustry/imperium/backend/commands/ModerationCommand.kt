// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.commands

import com.xpdustry.imperium.backend.command.annotation.Range
import com.xpdustry.imperium.backend.misc.Embed
import com.xpdustry.imperium.backend.misc.await
import com.xpdustry.imperium.backend.misc.identity
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentDuration
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.PlayerIDLike
import kotlin.time.Duration
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction

@Inject
class ModerationCommand(
    private val punishments: PunishmentManager,
    private val renderer: TimeRenderer,
    private val codec: IdentifierCodec,
) : ImperiumApplication.Listener {

    @ImperiumCommand(["punishment", "list"], Rank.MODERATOR)
    suspend fun onPunishmentListCommand(
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        @Range(min = "0") page: Int = 0,
    ) {
        val reply = interaction.deferReply(true).await()
        val result = punishments.findAllByUser(player.id).drop(page * 10).take(10).toList()
        if (result.isEmpty()) {
            reply.sendMessage("No punishments found.").await()
            return
        }
        reply.sendMessageEmbeds(result.map { it.toEmbed() }).await()
    }

    @ImperiumCommand(["punishment", "info"], Rank.MODERATOR)
    suspend fun onPunishmentInfoCommand(interaction: SlashCommandInteraction, punishment: String) {
        val reply = interaction.deferReply(true).await()
        val id = codec.tryDecode(punishment)
        if (id == null) {
            reply.sendMessage("Invalid id.").await()
            return
        }
        val result = punishments.findById(id)
        if (result == null) {
            reply.sendMessage("No punishment found.").await()
            return
        }
        reply.sendMessageEmbeds(result.toEmbed()).await()
    }

    private fun Punishment.toEmbed() = Embed {
        title = "Punishment `${codec.encode(id)}`"
        field("Target ID", codec.encode(target), true)
        field("Type", type.toString(), true)
        field("Reason", reason, false)
        field("Creation", renderer.renderInstant(creation), true)
        field("Duration", renderer.renderDuration(duration), true)
        if (pardon != null) {
            field("Pardon Reason", pardon!!.reason, false)
            field("Pardon Timestamp", renderer.renderInstant(pardon!!.timestamp), true)
        }
    }

    @ImperiumCommand(["ban"], Rank.MODERATOR)
    suspend fun onBanCommand(
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_DAYS,
    ) {
        onPunishCommand("Banned", Punishment.Type.BAN, interaction, player, reason, duration.value)
    }

    @ImperiumCommand(["freeze"], Rank.MODERATOR)
    suspend fun onFreezeCommand(
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.THREE_HOURS,
    ) {
        onPunishCommand("Frozen", Punishment.Type.FREEZE, interaction, player, reason, duration.value)
    }

    @ImperiumCommand(["mute"], Rank.MODERATOR)
    suspend fun onMuteCommand(
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        reason: String,
        duration: PunishmentDuration = PunishmentDuration.ONE_DAY,
    ) {
        onPunishCommand("Muted", Punishment.Type.MUTE, interaction, player, reason, duration.value)
    }

    @ImperiumCommand(["kick"], Rank.MODERATOR)
    suspend fun onKickCommand(
        interaction: SlashCommandInteraction,
        player: String,
        reason: String,
    ) {
        onPunishCommand("Kicked", Punishment.Type.KICK, interaction, player, reason, PunishmentDuration.NONE.value)
    }

    private suspend fun onPunishCommand(
        verb: String,
        type: Punishment.Type,
        interaction: SlashCommandInteraction,
        player: PlayerIDLike,
        reason: String,
        duration: Duration,
    ) {
        val reply = interaction.deferReply(true).await()
        punishments.punish(interaction.member!!.identity, player.id, reason, type, duration)
        reply.sendMessage("$verb user `${codec.encode(player.id)}`.").await()
    }

    @ImperiumCommand(["pardon"], Rank.MODERATOR)
    suspend fun onPardonCommand(interaction: SlashCommandInteraction, punishment: String, reason: String) {
        val reply = interaction.deferReply(true).await()
        val id = codec.tryDecode(punishment)
        if (id == null) {
            reply.sendMessage("Invalid Punishment ID.").await()
            return
        }

        val entry = punishments.findById(id)
        if (entry == null) {
            reply.sendMessage("Punishment not found.").await()
            return
        }

        if (entry.pardon != null) {
            reply.sendMessage("Punishment already pardoned.").await()
            return
        }

        punishments.pardon(interaction.member!!.identity, id, reason)
        reply.sendMessage("Pardoned user.").await()
    }
}
