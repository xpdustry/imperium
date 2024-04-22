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
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.ImperiumPermission
import com.xpdustry.imperium.discord.command.annotation.Range
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.content.MindustryMapTable
import com.xpdustry.imperium.common.image.inputStream
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
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.utils.FileUpload

internal class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()
    private val renderer = instances.get<TimeRenderer>()

    @Suppress("DuplicatedCode")
    @ImperiumCommand(["map", "preview"])
    @NonEphemeral
    suspend fun onMapPreviewCommand(actor: InteractionSender.Slash, map: Message.Attachment) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        if (map.size > MindustryMapTable.MAX_MAP_FILE_SIZE) {
            actor.respond("The map file is bigger than 1mb, please submit reasonably sized maps.")
            return
        }

        val bytes = map.proxy.download().await().use(InputStream::readBytes)
        val (meta, preview) = content.getMapMetadataWithPreview(bytes.inputStream()).getOrThrow()

        @Suppress("DuplicatedCode")
        if (meta.width > MAX_MAP_SIDE_SIZE || meta.height > MAX_MAP_SIDE_SIZE) {
            actor.respond(
                "The map is bigger than $MAX_MAP_SIDE_SIZE blocks, please submit reasonably sized maps.")
            return
        }

        actor.respond {
            addFiles(
                FileUpload.fromStreamSupplier(map.fileName, bytes::inputStream),
                FileUpload.fromStreamSupplier("preview.png", preview::inputStream))
            addEmbeds(
                Embed {
                    color = MINDUSTRY_ACCENT_COLOR.rgb
                    title = "Map Submission"
                    image = "attachment://preview.png"
                    field("Name", meta.name.stripMindustryColors(), false)
                    field("Author", meta.author?.stripMindustryColors() ?: "Unknown", false)
                    field(
                        "Description", meta.description?.stripMindustryColors() ?: "Unknown", false)
                    field("Size", "${preview.width} x ${preview.height}", false)
                })
        }
    }

    @Suppress("DuplicatedCode")
    @ImperiumCommand(["map", "submit"])
    @NonEphemeral
    suspend fun onMapSubmitCommand(
        actor: InteractionSender.Slash,
        map: Message.Attachment,
        notes: String? = null
    ) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        if (map.size > MindustryMapTable.MAX_MAP_FILE_SIZE) {
            actor.respond("The map file is bigger than 1mb, please submit reasonably sized maps.")
            return
        }

        val bytes = map.proxy.download().await().use(InputStream::readBytes)
        val (meta, preview) = content.getMapMetadataWithPreview(bytes.inputStream()).getOrThrow()

        if (meta.width > MAX_MAP_SIDE_SIZE || meta.height > MAX_MAP_SIDE_SIZE) {
            actor.respond(
                "The map is bigger than $MAX_MAP_SIDE_SIZE blocks, please submit reasonably sized maps.")
            return
        }

        val channel =
            discord.getMainServer().getTextChannelById(config.channels.maps)
                ?: throw IllegalStateException("Map submission channel not found")

        val message =
            channel
                .sendMessage(
                    MessageCreate {
                        files += FileUpload.fromStreamSupplier(map.fileName, bytes::inputStream)
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = "Map Submission"
                            field("Submitter", actor.member.asMention, false)
                            field("Name", meta.name.stripMindustryColors(), false)
                            field("Author", meta.author?.stripMindustryColors() ?: "Unknown", false)
                            field(
                                "Description",
                                meta.description?.stripMindustryColors() ?: "Unknown",
                                false)
                            field("Size", "${preview.width} x ${preview.height}", false)
                            if (notes != null) {
                                field("Notes", notes, false)
                            }
                            val updating =
                                maps.findMapByName(meta.name.stripMindustryColors())?.snowflake
                            if (updating != null) {
                                field("Updating Map", "`$updating`", false)
                            }
                            image = "attachment://preview.png"
                        }
                        components +=
                            ActionRow.of(
                                Button.primary(MAP_UPLOAD_BUTTON, "Upload")
                                    .withEmoji(ImperiumEmojis.INBOX_TRAY),
                                Button.danger(MAP_REJECT_BUTTON, "Reject")
                                    .withEmoji(ImperiumEmojis.WASTE_BASKET),
                            )
                    })
                .await()

        message
            .createThreadChannel("Comments for ${meta.name.stripMindustryColors()}")
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS)
            .await()

        actor.respond(
            Embed {
                color = MINDUSTRY_ACCENT_COLOR.rgb
                description =
                    "Your map has been submitted for review. Check the status [here](${message.jumpUrl})."
            })
    }

    @ButtonCommand(MAP_REJECT_BUTTON, Rank.ADMIN)
    private suspend fun onMapReject(actor: InteractionSender.Button) {
        updateSubmissionEmbed(actor, Color.RED, "rejected")
        actor.respond("Map submission rejected!")
    }

    @ButtonCommand(MAP_UPLOAD_BUTTON, Rank.ADMIN)
    private suspend fun onMapUpload(actor: InteractionSender.Button) {
        val attachment = actor.message.attachments.first()
        val meta =
            attachment.proxy.download().await().use { content.getMapMetadata(it).getOrThrow() }

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
                    stream = { attachment.proxy.download().join() })
        } else {
            snowflake = map.snowflake
            maps.updateMap(
                snowflake = map.snowflake,
                description = meta.description?.stripMindustryColors(),
                author = meta.author?.stripMindustryColors(),
                width = meta.width,
                height = meta.height,
                stream = { attachment.proxy.download().join() })
        }

        updateSubmissionEmbed(actor, Color.GREEN, "uploaded", snowflake)
        actor.respond("Map submission uploaded! The map id is `$snowflake`.")
    }

    private suspend fun updateSubmissionEmbed(
        actor: InteractionSender.Button,
        color: Color,
        verb: String,
        snowflake: Snowflake? = null
    ) {
        actor.message
            .editMessageEmbeds(
                Embed(actor.message.embeds.first()) {
                    this@Embed.color = color.rgb
                    field("Reviewer", actor.member.asMention, false)
                    if (snowflake != null) {
                        field("Identifier", "$snowflake", false)
                    }
                    image = "attachment://preview2.png"
                })
            .setComponents()
            .setFiles(
                FileUpload.fromStreamSupplier(actor.message.attachments.first().fileName) {
                    actor.message.attachments.first().proxy.download().join()
                },
                // Why do I have to fight discord API to simply update an image in an embed ?
                // I hate it
                FileUpload.fromStreamSupplier("preview2.png") {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    URL(actor.message.embeds.first().image!!.url).openStream()
                })
            .await()

        discord
            .getMainServer()
            .getMemberById(
                MENTION_TAG_REGEX.find(actor.message.embeds.first().getFieldValue("Submitter")!!)!!
                    .groups[1]!!
                    .value
                    .toLong())
            ?.user
            ?.openPrivateChannel()
            ?.await()
            ?.sendMessageEmbeds(
                Embed {
                    this@Embed.color = color.rgb
                    description =
                        "Your [map submission](${actor.message.jumpUrl}) has been $verb by ${actor.member.asMention}."
                })
    }

    @ImperiumCommand(["map", "list"])
    @NonEphemeral
    suspend fun onMapList(
        actor: InteractionSender.Slash,
        @Range(min = "1") page: Int = 1,
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

        actor.respond(
            Embed {
                color = MINDUSTRY_ACCENT_COLOR.rgb
                title = "Map list result"
                description =
                    if (result.isEmpty()) {
                        "No maps found"
                    } else {
                        buildString {
                            append(result.joinToString("\n") { "- ${it.name} / ${it.snowflake}" })
                            if (hasMore) {
                                footer("...and more")
                            }
                        }
                    }
            })
    }

    @ImperiumCommand(["map", "info"])
    @NonEphemeral
    suspend fun onMapInfo(actor: InteractionSender.Slash, id: Snowflake) {
        val map = maps.findMapBySnowflake(id)
        if (map == null) {
            actor.respond("Unknown map id")
            return
        }

        val stats = maps.getMapStats(id)!!
        actor.respond {
            addFiles(
                FileUpload.fromStreamSupplier("preview.png") {
                    runBlocking {
                        maps.getMapInputStream(map.snowflake)!!.use {
                            this@MapCommand.content
                                .getMapMetadataWithPreview(it)
                                .getOrThrow()
                                .second
                                .inputStream()
                        }
                    }
                })
            addEmbeds(
                Embed {
                    color = MINDUSTRY_ACCENT_COLOR.rgb
                    title = map.name
                    field("Author", map.author ?: "Unknown", false)
                    field("Identifier", "${map.snowflake}", false)
                    field("Description", map.description ?: "Unknown", false)
                    field("Size", "${map.width} x ${map.height}", false)
                    field("Games", stats.games.toString(), false)
                    field("Score", "%.2f / 5".format(stats.score), false)
                    field("Difficulty", stats.difficulty.toString().lowercase(), false)
                    field("World Record", stats.record.toString(), false)
                    field(
                        "Gamemodes",
                        if (map.gamemodes.isEmpty()) "`none`"
                        else map.gamemodes.joinToString { it.name.lowercase() },
                        false)
                    image = "attachment://preview.png"
                })
            addComponents(
                ActionRow.of(
                    Button.primary(MAP_DOWNLOAD_BUTTON, "Download")
                        .withEmoji(ImperiumEmojis.DOWN_ARROW)))
        }
    }

    // TODO Add a way to navigate the games
    @ImperiumCommand(["map", "game", "info"])
    @NonEphemeral
    suspend fun onMapGameInfo(actor: InteractionSender.Slash, id: Snowflake) {
        val game = maps.findMapGameBySnowflake(id)
        if (game == null) {
            actor.respond("Unknown game id")
            return
        }
        actor.respond(
            Embed {
                color = MINDUSTRY_ACCENT_COLOR.rgb
                title = "Game ${game.snowflake}"
                field("Date", renderer.renderInstant(game.start))
                field("Playtime", renderer.renderDuration(game.playtime))
                field("Units Created", game.unitsCreated.toString())
                field("Ennemies Killed", game.ennemiesKilled.toString())
                field("Waves Lasted", game.wavesLasted.toString())
                field("Buildings Constructed", game.buildingsConstructed.toString())
                field("Buildings Deconstructed", game.buildingsDeconstructed.toString())
                field("Buildings Destroyed", game.buildingsDestroyed.toString())
                field("Winner", getWinnerName(game.winner))
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
    suspend fun onMapDownload(actor: InteractionSender.Button) {
        val snowflake = actor.message.embeds.first().getFieldValue("Identifier")?.toLong()
        val stream = snowflake?.let { maps.getMapInputStream(it) }
        if (stream === null) {
            actor.respond("The map is no longer available")
            return
        }
        actor.respond {
            setContent("Here you go:")
            addFiles(FileUpload.fromStreamSupplier("$snowflake.msav") { stream })
        }
    }

    @ImperiumCommand(["map", "gamemode", "add"])
    @ImperiumPermission(Rank.MODERATOR)
    suspend fun onMapGamemodeAdd(
        actor: InteractionSender.Slash,
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

    @ImperiumCommand(["map", "gamemode", "remove"])
    @ImperiumPermission(Rank.MODERATOR)
    suspend fun onMapGamemodeRemove(
        actor: InteractionSender.Slash,
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

    @ImperiumCommand(["map", "delete"])
    @ImperiumPermission(Rank.ADMIN)
    suspend fun onMapDelete(actor: InteractionSender.Slash, id: Snowflake) {
        if (maps.deleteMapBySnowflake(id)) {
            actor.respond("Map deleted!")
        } else {
            actor.respond("Unknown map id")
        }
    }

    private fun MessageEmbed.getFieldValue(name: String): String? =
        fields.find { it.name == name }?.value

    companion object {
        private val MENTION_TAG_REGEX = Regex("<@!?(\\d+)>")
        private const val MAP_REJECT_BUTTON = "map-submission-reject:1"
        private const val MAP_UPLOAD_BUTTON = "map-submission-upload:1"
        private const val MAP_DOWNLOAD_BUTTON = "map-download:1"
        private const val MAX_MAP_SIDE_SIZE = 1536
    }
}
