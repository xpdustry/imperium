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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.command.Permission
import com.xpdustry.imperium.common.command.annotation.Min
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.command.ButtonCommand
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.future.await
import org.bson.types.ObjectId
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.AutoArchiveDuration
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.component.ActionRow
import org.javacord.api.entity.message.component.Button
import org.javacord.api.entity.message.embed.Embed
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.awt.Color
import java.time.Instant
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.hours

class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()

    @Command(["map", "submit"])
    suspend fun onSubmitCommand(actor: InteractionSender, map: Attachment, notes: String? = null) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        val (meta, preview) = content.getMapMetadataWithPreview(map.asInputStream()).getOrThrow()
        val channel = discord.getMainServer().getTextChannelById(config.channels.maps).getOrNull()
            ?: throw IllegalStateException("Map submission channel not found")

        val message = MessageBuilder()
            .addAttachment(map.asInputStream(), map.fileName)
            .addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_ACCENT_COLOR)
                    .setTitle("Map Submission")
                    .addField("Submitter", actor.user.mentionTag)
                    .addField("Name", meta.name.stripMindustryColors())
                    .addField("Author", meta.author?.stripMindustryColors() ?: "Unknown")
                    .addField("Description", meta.description?.stripMindustryColors() ?: "Unknown")
                    .addField("Size", "${preview.width} x ${preview.height}")
                    .apply {
                        if (notes != null) {
                            addField("Notes", notes)
                        }
                        val updating = maps.findMapByName(meta.name.stripMindustryColors())?._id
                        if (updating != null) {
                            addField("Updating Map", "`$updating`")
                        }
                    }
                    .setImage(preview),
            )
            .addComponents(
                ActionRow.of(
                    Button.primary(MAP_UPLOAD_BUTTON, "Upload", ImperiumEmojis.INBOX_TRAY),
                    Button.danger(MAP_REJECT_BUTTON, "Reject", ImperiumEmojis.WASTE_BASKET),
                ),
            )
            .send(channel)
            .await()

        message.createThread("Comments for ${meta.name.stripMindustryColors()}", AutoArchiveDuration.THREE_DAYS).await()

        actor.respond(
            EmbedBuilder()
                .setColor(MINDUSTRY_ACCENT_COLOR)
                .setDescription("Your map has been submitted for review. Check the status [here](${message.link})."),
        )
    }

    @ButtonCommand(MAP_REJECT_BUTTON, Permission.ADMINISTRATOR)
    private suspend fun onMapReject(actor: InteractionSender.Button) {
        updateSubmissionEmbed(actor, Color.RED, "rejected")
        actor.respond("Map submission rejected!")
    }

    @ButtonCommand(MAP_UPLOAD_BUTTON, Permission.ADMINISTRATOR)
    private suspend fun onMapUpload(actor: InteractionSender.Button) {
        val attachment = actor.message.attachments.first()
        val meta = content.getMapMetadata(attachment.asInputStream()).getOrThrow()

        var map = maps.findMapByName(meta.name.stripMindustryColors())
        if (map == null) {
            map = MindustryMap(
                name = meta.name.stripMindustryColors(),
                description = meta.description?.stripMindustryColors(),
                author = meta.author?.stripMindustryColors(),
                width = meta.width,
                height = meta.height,
            )
        } else {
            map.description = meta.description?.stripMindustryColors()
            map.author = meta.author?.stripMindustryColors()
            map.width = meta.width
            map.height = meta.height
            map.lastUpdate = Instant.now()
        }

        maps.saveMap(map, attachment.asInputStream())
        updateSubmissionEmbed(actor, Color.GREEN, "uploaded")
        actor.respond("Map submission uploaded! The map id is `${map._id}`.")
    }

    private suspend fun updateSubmissionEmbed(actor: InteractionSender.Button, color: Color, verb: String) {
        actor.message.createUpdater()
            .removeAllEmbeds()
            .addEmbed(
                actor.message.embeds.first()
                    .toBuilder()
                    .addField("Reviewer", actor.user.mentionTag)
                    // Avoids the image expiring, I think...
                    .setImage(actor.message.embeds.first().image.get().url.toString())
                    .setColor(color),
            )
            .removeAllComponents()
            .applyChanges()
            .await()

        actor.message.embeds.first().getFieldValue("Submitter")
            ?.let { discord.api.getUserById(MENTION_TAG_REGEX.find(it)!!.groupValues[1].toLong()) }
            ?.await()
            ?.sendMessage(
                EmbedBuilder()
                    .setColor(color)
                    .setDescription("Your [map submission](${actor.message.link}) has been $verb by ${actor.user.mentionTag}."),
            )
            ?.await()
    }

    @Command(["map", "list"])
    @NonEphemeral
    private suspend fun onMapList(actor: InteractionSender, @Min(1) page: Int = 1, server: String? = null) {
        val result = maps.findMaps(server).drop((page - 1) * 10).take(11).toCollection(mutableListOf())
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
                    append(result.joinToString("\n") { "- ${it.name} / `${it._id}`" })
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
    private suspend fun onMapInfo(actor: InteractionSender, id: ObjectId) {
        val map = maps.findMapById(id)
        if (map == null) {
            actor.respond("Unknown map id")
            return
        }

        actor.respond {
            addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_ACCENT_COLOR)
                    .setTitle(map.name)
                    .addField("Author", map.author ?: "Unknown")
                    .addField("Identifier", "${map._id}")
                    .addField("Description", map.description ?: "Unknown")
                    .addField("Size", "${map.width} x ${map.height}")
                    .addField("Score", "%.2f / 5".format(maps.computeAverageScoreByMap(map._id)))
                    .addField("Difficulty", maps.computeAverageDifficultyByMap(map._id).name.lowercase())
                    .addField("Servers", if (map.servers.isEmpty()) "`none`" else map.servers.joinToString(", "))
                    .setImage(
                        content.getMapMetadataWithPreview(maps.getMapObject(map._id).getData()).getOrThrow().second,
                    ),
            )
            addComponents(
                ActionRow.of(Button.primary(MAP_DOWNLOAD_BUTTON, "Download", ImperiumEmojis.DOWN_ARROW)),
            )
        }
    }

    @ButtonCommand(MAP_DOWNLOAD_BUTTON)
    private suspend fun onMapDownload(actor: InteractionSender.Button) {
        val url = actor.message.embeds.first().getFieldValue("Identifier")
            ?.let { maps.getMapObject(ObjectId(it)) }
            ?.takeIf { it.exists }
            ?.getDownloadUrl(1.hours)

        if (url === null) {
            actor.respond("Failed to get download url")
            return
        }

        actor.respond("Here go, click on this [link]($url) to download the map. It will expire in 1 hour.")
    }

    @Command(["map", "edit", "servers"], Permission.ADMINISTRATOR)
    private suspend fun onMapServersChange(actor: InteractionSender, id: ObjectId, servers: String? = null) {
        if (maps.findMapById(id) == null) {
            actor.respond("Unknown map id")
            return
        }

        val list = servers?.split(",")?.map { it.trim() } ?: emptyList()
        for (server in list) {
            if (!server.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                actor.respond("**$server** is an invalid server name")
                return
            }
        }

        maps.updateMapById(id) {
            this.servers.clear()
            this.servers.addAll(list)
        }

        actor.respond("Map servers updated to $list!")
    }

    @Command(["map", "delete"], Permission.ADMINISTRATOR)
    private suspend fun onMapDelete(actor: InteractionSender, id: ObjectId) {
        if (maps.deleteMapById(id)) {
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
    }
}
