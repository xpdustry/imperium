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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.permission.Permission
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.discord.command.MenuCommand
import com.xpdustry.imperium.discord.command.annotation.AlsoAllow
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.utils.FileUpload

internal class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val renderer = instances.get<TimeRenderer>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["map", "info"])
    suspend fun onMapInfo(interaction: SlashCommandInteraction, id: String) {
        val reply = interaction.deferReply(false).await()
        val map = codec.tryDecode(id)?.let { maps.findMapById(it) }
        if (map == null) {
            reply.sendMessage("Unknown map id").await()
            return
        }

        val stats = maps.getMapStats(map.id)!!
        reply
            .sendMessage(
                MessageCreate {
                    files +=
                        FileUpload.fromStreamSupplier("preview.png") {
                            runBlocking {
                                maps.getMapInputStream(map.id)!!.use {
                                    this@MapCommand.content
                                        .getMapMetadataWithPreview(it)
                                        .getOrThrow()
                                        .second
                                        .inputStream()
                                }
                            }
                        }

                    embeds += Embed {
                        color = MINDUSTRY_ACCENT_COLOR.rgb
                        title = map.name
                        field("Author", map.author ?: "Unknown", false)
                        field("Identifier", codec.encode(map.id), false)
                        field("Description", map.description ?: "Unknown", false)
                        field("Size", "${map.width} x ${map.height}", false)
                        field("Games", stats.games.toString(), false)
                        field("Score", "%.2f / 5".format(stats.score), false)
                        field("Difficulty", stats.difficulty.toString().lowercase(), false)
                        field("World Record", stats.record?.let(codec::encode) ?: "none", false)
                        field(
                            "Gamemodes",
                            if (map.gamemodes.isEmpty()) "`none`"
                            else map.gamemodes.joinToString { it.name.lowercase() },
                            false,
                        )
                        image = "attachment://preview.png"
                    }

                    components +=
                        ActionRow.of(
                            Button.primary(MAP_DOWNLOAD_BUTTON, "Download").withEmoji(ImperiumEmojis.DOWN_ARROW)
                        )
                }
            )
            .await()
    }

    // TODO Add a way to navigate the games
    @ImperiumCommand(["map", "game", "info"])
    suspend fun onMapGameInfo(interaction: SlashCommandInteraction, id: String) {
        val reply = interaction.deferReply(false).await()
        val game = codec.tryDecode(id)?.let { maps.findMapGameBySnowflake(it) }
        if (game == null) {
            reply.sendMessage("Unknown game id").await()
            return
        }
        reply
            .sendMessageEmbeds(
                Embed {
                    color = MINDUSTRY_ACCENT_COLOR.rgb
                    title = "Game ${codec.encode(game.id)}"
                    field("Date", renderer.renderInstant(game.data.start))
                    field("Playtime", renderer.renderDuration(game.data.playtime))
                    field("Units Created", game.data.unitsCreated.toString())
                    field("Ennemies Killed", game.data.ennemiesKilled.toString())
                    field("Waves Lasted", game.data.wavesLasted.toString())
                    field("Buildings Constructed", game.data.buildingsConstructed.toString())
                    field("Buildings Deconstructed", game.data.buildingsDeconstructed.toString())
                    field("Buildings Destroyed", game.data.buildingsDestroyed.toString())
                    field("Winner", getWinnerName(game.data.winner))
                }
            )
            .await()
    }

    private fun getWinnerName(id: UByte) =
        when (id.toInt()) {
            0 -> "derelict"
            1 -> "sharded"
            2 -> "crux"
            3 -> "malis"
            4 -> "green"
            5 -> "blue"
            6 -> "neoplastic"
            else -> "team#$id"
        }

    @MenuCommand(MAP_DOWNLOAD_BUTTON)
    suspend fun onMapDownload(interaction: ButtonInteraction) {
        val reply = interaction.deferReply(true).await()
        val id = interaction.message.embeds.first().getFieldValue("Identifier")?.let { codec.tryDecode(it) }
        val stream = id?.let { maps.getMapInputStream(it) }
        if (stream === null) {
            reply.sendMessage("The map is no longer available").await()
            return
        }
        reply
            .sendMessage(
                MessageCreate {
                    content = "Here you go:"
                    files += FileUpload.fromStreamSupplier("${codec.encode(id)}.msav") { stream }
                }
            )
            .await()
    }

    @ImperiumCommand(["map", "gamemode", "add"], Rank.MODERATOR)
    @AlsoAllow(Permission.MANAGE_MAP)
    suspend fun onMapGamemodeAdd(interaction: SlashCommandInteraction, id: String, gamemode: MindustryGamemode) {
        val reply = interaction.deferReply(true).await()
        val map = codec.tryDecode(id)?.let { maps.findMapById(it) }
        if (map == null) {
            reply.sendMessage("Unknown map id").await()
        } else if (gamemode in map.gamemodes) {
            reply.sendMessage("This map is already in the **${gamemode.name.lowercase()}** server pool.").await()
        } else {
            maps.setMapGamemodes(map.id, map.gamemodes + gamemode)
            reply.sendMessage("This map is now in the **${gamemode.name.lowercase()}** server pool.").await()
        }
    }

    @ImperiumCommand(["map", "gamemode", "remove"], Rank.MODERATOR)
    @AlsoAllow(Permission.MANAGE_MAP)
    suspend fun onMapGamemodeRemove(interaction: SlashCommandInteraction, id: String, gamemode: MindustryGamemode) {
        val reply = interaction.deferReply(true).await()
        val map = codec.tryDecode(id)?.let { maps.findMapById(it) }
        if (map == null) {
            reply.sendMessage("Unknown map id").await()
        } else if (gamemode !in map.gamemodes) {
            reply.sendMessage("This map is not in the **${gamemode.name.lowercase()}** server pool.").await()
        } else {
            maps.setMapGamemodes(map.id, map.gamemodes - gamemode)
            reply.sendMessage("This map is no longer in the **${gamemode.name.lowercase()}** server pool.").await()
        }
    }

    @ImperiumCommand(["map", "delete"], Rank.ADMIN)
    @AlsoAllow(Permission.MANAGE_MAP)
    suspend fun onMapDelete(interaction: SlashCommandInteraction, id: String) {
        val reply = interaction.deferReply(true).await()
        val parsed = codec.tryDecode(id)
        if (parsed == null) {
            reply.sendMessage("Invalid map id").await()
            return
        }
        if (maps.deleteMapById(parsed)) {
            reply.sendMessage("Map deleted!").await()
        } else {
            reply.sendMessage("Unknown map id").await()
        }
    }

    private fun MessageEmbed.getFieldValue(name: String): String? = fields.find { it.name == name }?.value

    companion object {
        private const val MAP_DOWNLOAD_BUTTON = "map-download:2"
    }
}
