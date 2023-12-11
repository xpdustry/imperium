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
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.MindustryMapTable
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.discord.command.ButtonCommand
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.future.await
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.AutoArchiveDuration
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.component.ActionRow
import org.javacord.api.entity.message.component.Button
import org.javacord.api.entity.message.embed.Embed
import org.javacord.api.entity.message.embed.EmbedBuilder

class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()
    private val renderer = instances.get<TimeRenderer>()

    @Suppress("DuplicatedCode")
    @Command(["map", "preview"])
    @NonEphemeral
    suspend fun onMapPreviewCommand(actor: InteractionSender, map: Attachment) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        if (map.size > MindustryMapTable.MAX_MAP_FILE_SIZE) {
            actor.respond(
                "The map file is bigger than ${MindustryMapTable.MAX_MAP_FILE_SIZE / 1024}kb, please submit reasonably sized maps.")
            return
        }

        val (meta, preview) =
            map.asInputStream().use { content.getMapMetadataWithPreview(it).getOrThrow() }

        @Suppress("DuplicatedCode")
        if (meta.width > MAX_MAP_SIDE_SIZE || meta.height > MAX_MAP_SIDE_SIZE) {
            actor.respond(
                "The map is bigger than $MAX_MAP_SIDE_SIZE blocs, please submit reasonably sized maps.")
            return
        }

        actor.respond {
            addAttachment(map.asInputStream(), map.fileName)
            addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_ACCENT_COLOR)
                    .setTitle("Map Submission")
                    .addField("Name", meta.name.stripMindustryColors())
                    .addField("Author", meta.author?.stripMindustryColors() ?: "Unknown")
                    .addField("Description", meta.description?.stripMindustryColors() ?: "Unknown")
                    .addField("Size", "${preview.width} x ${preview.height}")
                    .setImage(preview))
        }
    }

    @Suppress("DuplicatedCode")
    @Command(["map", "submit"])
    @NonEphemeral
    suspend fun onMapSubmitCommand(
        actor: InteractionSender,
        map: Attachment,
        notes: String? = null
    ) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        if (map.size > MindustryMapTable.MAX_MAP_FILE_SIZE) {
            actor.respond(
                "The map file is bigger than ${MindustryMapTable.MAX_MAP_FILE_SIZE / 1024}kb, please submit reasonably sized maps.")
            return
        }

        val (meta, preview) =
            map.asInputStream().use { content.getMapMetadataWithPreview(it).getOrThrow() }

        if (meta.width > MAX_MAP_SIDE_SIZE || meta.height > MAX_MAP_SIDE_SIZE) {
            actor.respond(
                "The map is bigger than $MAX_MAP_SIDE_SIZE blocs, please submit reasonably sized maps.")
            return
        }

        val channel =
            discord.getMainServer().getTextChannelById(config.channels.maps).getOrNull()
                ?: throw IllegalStateException("Map submission channel not found")

        val message =
            MessageBuilder()
                .addAttachment(map.asInputStream(), map.fileName)
                .addEmbed(
                    EmbedBuilder()
                        .setColor(MINDUSTRY_ACCENT_COLOR)
                        .setTitle("Map Submission")
                        .addField("Submitter", actor.user.mentionTag)
                        .addField("Name", meta.name.stripMindustryColors())
                        .addField("Author", meta.author?.stripMindustryColors() ?: "Unknown")
                        .addField(
                            "Description", meta.description?.stripMindustryColors() ?: "Unknown")
                        .addField("Size", "${preview.width} x ${preview.height}")
                        .apply {
                            if (notes != null) {
                                addField("Notes", notes)
                            }
                            val updating =
                                maps.findMapByName(meta.name.stripMindustryColors())?.snowflake
                            if (updating != null) {
                                addField("Updating Map", "`$updating`")
                            }
                        }
                        .setImage(preview))
                .addComponents(
                    ActionRow.of(
                        Button.primary(MAP_UPLOAD_BUTTON, "Upload", ImperiumEmojis.INBOX_TRAY),
                        Button.danger(MAP_REJECT_BUTTON, "Reject", ImperiumEmojis.WASTE_BASKET),
                    ),
                )
                .send(channel)
                .await()

        message
            .createThread(
                "Comments for ${meta.name.stripMindustryColors()}", AutoArchiveDuration.THREE_DAYS)
            .await()

        actor.respond(
            EmbedBuilder()
                .setColor(MINDUSTRY_ACCENT_COLOR)
                .setDescription(
                    "Your map has been submitted for review. Check the status [here](${message.link})."),
        )
    }

    @ButtonCommand(MAP_REJECT_BUTTON, Rank.ADMIN)
    private suspend fun onMapReject(actor: InteractionSender.Button) {
        updateSubmissionEmbed(actor, Color.RED, "rejected")
        actor.respond("Map submission rejected!")
    }

    @ButtonCommand(MAP_UPLOAD_BUTTON, Rank.ADMIN)
    private suspend fun onMapUpload(actor: InteractionSender.Button) {
        val attachment = actor.message.attachments.first()
        val meta = attachment.asInputStream().use { content.getMapMetadata(it).getOrThrow() }

        val map = maps.findMapByName(meta.name.stripMindustryColors())
        val snowflake: Snowflake
        if (map == null) {
            snowflake =
                maps.createMap(
                    name = meta.name.stripMindustryColors(),
                    description = meta.description?.stripMindustryColors(),
                    author = meta.author?.stripMindustryColors(),
                    width = meta.width,
                    height = meta.height,
                    stream = attachment::asInputStream)
        } else {
            snowflake = map.snowflake
            maps.updateMap(
                snowflake = map.snowflake,
                description = meta.description?.stripMindustryColors(),
                author = meta.author?.stripMindustryColors(),
                width = meta.width,
                height = meta.height,
                stream = attachment::asInputStream)
        }

        updateSubmissionEmbed(actor, Color.GREEN, "uploaded")
        actor.respond("Map submission uploaded! The map id is `$snowflake`.")
    }

    private suspend fun updateSubmissionEmbed(
        actor: InteractionSender.Button,
        color: Color,
        verb: String
    ) {
        actor.message
            .createUpdater()
            .removeAllEmbeds()
            .addEmbed(
                actor.message.embeds
                    .first()
                    .toBuilder()
                    .addField("Reviewer", actor.user.mentionTag)
                    .setImage(actor.message.embeds.first().image.get().asInputStream(discord.api))
                    .setColor(color),
            )
            .removeAllComponents()
            .applyChanges()
            .await()

        discord
            .getMainServer()
            .getMemberById(
                MENTION_TAG_REGEX.find(actor.message.embeds.first().getFieldValue("Submitter")!!)!!
                    .groups[1]!!
                    .value
                    .toLong())
            .getOrNull()
            ?.sendMessage(
                EmbedBuilder()
                    .setColor(color)
                    .setDescription(
                        "Your [map submission](${actor.message.link}) has been $verb by ${actor.user.mentionTag}."),
            )
            ?.await()
    }

    @Command(["map", "list"])
    @NonEphemeral
    private suspend fun onMapList(
        actor: InteractionSender,
        @Min(1) page: Int = 1,
        query: String? = null,
        gamemode: MindustryGamemode? = null
    ) {
        val result =
            (if (query == null) maps.findAllMaps() else maps.searchMapByName(query))
                .filter { gamemode == null || gamemode in it.gamemodes }
                .drop((page - 1) * 10)
                .take(11)
                .toCollection(mutableListOf())
        val hasMore = result.size > 11
        if (hasMore) {
            result.removeLast()
        }
        val embed = EmbedBuilder().setColor(MINDUSTRY_ACCENT_COLOR).setTitle("Map list result")

        if (result.isEmpty()) {
            embed.setDescription("No maps found")
        } else {
            embed.setDescription(
                buildString {
                    append(result.joinToString("\n") { "- ${it.name} / `${it.snowflake}`" })
                    if (hasMore) {
                        append("\n\n...and more")
                    }
                },
            )
        }

        actor.respond(embed)
    }

    @Command(["map", "info"])
    @NonEphemeral
    private suspend fun onMapInfo(actor: InteractionSender, id: Snowflake) {
        val map = maps.findMapBySnowflake(id)
        if (map == null) {
            actor.respond("Unknown map id")
            return
        }

        val stats = maps.getMapStats(id)!!
        actor.respond {
            addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_ACCENT_COLOR)
                    .setTitle(map.name)
                    .addField("Author", map.author ?: "Unknown")
                    .addField("Identifier", "${map.snowflake}")
                    .addField("Description", map.description ?: "Unknown")
                    .addField("Size", "${map.width} x ${map.height}")
                    .addField("Games", stats.games.toString())
                    .addField("Score", "%.2f / 5".format(stats.score))
                    .addField("Difficulty", stats.difficulty.toString().lowercase())
                    .addField("World Record", stats.record.toString())
                    .addField(
                        "Gamemodes",
                        if (map.gamemodes.isEmpty()) "`none`"
                        else map.gamemodes.joinToString { it.name.lowercase() })
                    .setImage(
                        maps.getMapInputStream(map.snowflake)!!.use {
                            content.getMapMetadataWithPreview(it).getOrThrow().second
                        }))
            addComponents(
                ActionRow.of(
                    Button.primary(MAP_DOWNLOAD_BUTTON, "Download", ImperiumEmojis.DOWN_ARROW)),
            )
        }
    }

    // TODO Add a way to navigate the games
    @Command(["map", "game", "info"])
    @NonEphemeral
    private suspend fun onMapGameInfo(actor: InteractionSender, id: Snowflake) {
        val game = maps.findMapGameBySnowflake(id)
        if (game == null) {
            actor.respond("Unknown game id")
            return
        }
        actor.respond(
            EmbedBuilder().apply {
                setColor(MINDUSTRY_ACCENT_COLOR)
                setTitle("Game ${game.snowflake}")
                addField("Date", renderer.renderInstant(game.start))
                addField("Playtime", renderer.renderDuration(game.playtime))
                addField("Units Created", game.unitsCreated.toString())
                addField("Ennemies Killed", game.ennemiesKilled.toString())
                addField("Waves Lasted", game.wavesLasted.toString())
                addField("Buildings Constructed", game.buildingsConstructed.toString())
                addField("Buildings Deconstructed", game.buildingsDeconstructed.toString())
                addField("Buildings Destroyed", game.buildingsDestroyed.toString())
                addField("Winner", getWinnerName(game.winner))
            })
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

    @ButtonCommand(MAP_DOWNLOAD_BUTTON)
    private suspend fun onMapDownload(actor: InteractionSender.Button) {
        val snowflake = actor.message.embeds.first().getFieldValue("Identifier")?.toLong()
        val stream = snowflake?.let { maps.getMapInputStream(it) }
        if (stream === null) {
            actor.respond("The map is no longer available")
            return
        }
        stream.use {
            actor.respond {
                setContent("Here you go:")
                addAttachment(it, "$snowflake.msav")
            }
        }
    }

    @Command(["map", "gamemode", "add"], Rank.ADMIN)
    private suspend fun onMapGamemodeAdd(
        actor: InteractionSender,
        id: Snowflake,
        gamemode: MindustryGamemode
    ) {
        val map = maps.findMapBySnowflake(id)
        if (map == null) {
            actor.respond("Unknown map id")
            return
        }
        if (gamemode in map.gamemodes) {
            actor.respond(
                "This map is already in the **${gamemode.name.lowercase()}** server pool.")
            return
        }
        maps.setMapGamemodes(id, map.gamemodes + gamemode)
        actor.respond("This map is now in the **${gamemode.name.lowercase()}** server pool.")
    }

    @Command(["map", "gamemode", "remove"], Rank.ADMIN)
    private suspend fun onMapGamemodeRemove(
        actor: InteractionSender,
        id: Snowflake,
        gamemode: MindustryGamemode
    ) {
        val map = maps.findMapBySnowflake(id)
        if (map == null) {
            actor.respond("Unknown map id")
            return
        }
        if (gamemode !in map.gamemodes) {
            actor.respond("This map is not in the **${gamemode.name.lowercase()}** server pool.")
            return
        }
        maps.setMapGamemodes(id, map.gamemodes - gamemode)
        actor.respond("This map is no longer in the **${gamemode.name.lowercase()}** server pool.")
    }

    @Command(["map", "delete"], Rank.ADMIN)
    private suspend fun onMapDelete(actor: InteractionSender, id: Snowflake) {
        if (maps.deleteMapBySnowflake(id)) {
            actor.respond("Map deleted!")
        } else {
            actor.respond("Unknown map id")
        }
    }

    private fun Embed.getFieldValue(name: String): String? = fields.find { it.name == name }?.value

    companion object {
        private val MENTION_TAG_REGEX = Regex("<@!?(\\d+)>")
        private const val MAP_REJECT_BUTTON = "map-submission-reject:1"
        private const val MAP_UPLOAD_BUTTON = "map-submission-upload:1"
        private const val MAP_DOWNLOAD_BUTTON = "map-download:1"
        private const val MAX_MAP_SIDE_SIZE = 1440//p
    }
}
