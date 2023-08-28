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
package com.xpdustry.imperium.discord.interaction.command.standard

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.MindustryMap
import com.xpdustry.imperium.common.database.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.Permission
import com.xpdustry.imperium.discord.interaction.button.InteractionButton
import com.xpdustry.imperium.discord.interaction.command.Command
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.discord.misc.stripMindustryColors
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.AutoArchiveDuration
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.component.ActionRow
import org.javacord.api.entity.message.component.Button
import org.javacord.api.entity.message.embed.Embed
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.awt.Color
import kotlin.jvm.optionals.getOrNull

class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()

    @Command("map", "submit")
    suspend fun onSubmitCommand(actor: InteractionActor, map: Attachment, notes: String? = null) {
        if (!map.fileName.endsWith(".msav")) {
            actor.respond("Invalid map file!")
            return
        }

        val (meta, preview) = content.getMapMetadataWithPreview(map.asInputStream()).getOrThrow()
        val channel = discord.getMainServer().getTextChannelById(config.channels.maps).getOrNull()
            ?: throw IllegalStateException("Map submission channel not found")

        val message = MessageBuilder()
            .addAttachment(map.url, map.fileName)
            .addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_ACCENT_COLOR)
                    .setTitle("Map Submission")
                    .addField("Submitter", actor.user.mentionTag)
                    .addField("Name", meta.name.stripMindustryColors())
                    .addField("Author", meta.author?.stripMindustryColors() ?: "Unknown")
                    .addField("Description", meta.description?.stripMindustryColors() ?: "Unknown")
                    .addField("Size", "${preview.width} x ${preview.height}")
                    .apply { if (notes != null) addField("Notes", notes) }
                    .setImage(preview),
            )
            .addComponents(
                ActionRow.of(
                    Button.primary(MAP_UPLOAD_BUTTON, "Upload", ImperiumEmojis.INBOX_TRAY),
                    Button.secondary(MAP_UPDATE_BUTTON, "Update", ImperiumEmojis.PENCIL),
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

    @InteractionButton(MAP_REJECT_BUTTON, permission = Permission.ADMINISTRATOR)
    private suspend fun onMapReject(actor: InteractionActor.Button) {
        updateSubmissionEmbed(actor, Color.RED, "rejected")
        actor.respond("Map submission rejected!")
    }

    @InteractionButton(MAP_UPLOAD_BUTTON, permission = Permission.ADMINISTRATOR)
    private suspend fun onMapUpload(actor: InteractionActor.Button) {
        val attachment = actor.message.attachments.first()
        val meta = content.getMapMetadata(attachment.asInputStream()).getOrThrow()

        if (maps.findMapByName(meta.name.stripMindustryColors()) != null) {
            actor.respond("A map with that name already exists!")
            return
        }

        val map = MindustryMap(
            name = meta.name.stripMindustryColors(),
            description = meta.description?.stripMindustryColors(),
            author = meta.author?.stripMindustryColors(),
            width = meta.width,
            height = meta.height,
        )

        maps.saveMap(map, attachment.asInputStream())
        updateSubmissionEmbed(actor, Color.GREEN, "uploaded")
        actor.respond("Map submission uploaded!")
    }

    @InteractionButton(MAP_UPDATE_BUTTON, permission = Permission.ADMINISTRATOR)
    private suspend fun onMapUpdate(actor: InteractionActor.Button) {
        val attachment = actor.message.attachments.first()
        val meta = content.getMapMetadata(attachment.asInputStream()).getOrThrow()

        val map = maps.findMapByName(meta.name.stripMindustryColors())
        if (map == null) {
            actor.respond("A map with that name does not exist!")
            return
        }

        map.description = meta.description?.stripMindustryColors()
        map.author = meta.author?.stripMindustryColors()
        map.width = meta.width
        map.height = meta.height

        maps.saveMap(map, attachment.asInputStream())
        updateSubmissionEmbed(actor, Color.YELLOW, "updated")
        actor.respond("Map submission updated!")
    }

    private suspend fun updateSubmissionEmbed(actor: InteractionActor.Button, color: Color, verb: String) {
        actor.message.createUpdater()
            .removeAllEmbeds()
            .addEmbed(
                actor.message.embeds.first()
                    .toBuilder()
                    .addField("Reviewer", actor.user.mentionTag)
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

    private fun Embed.getFieldValue(name: String): String? = fields.find { it.name == name }?.value

    companion object {
        private val MENTION_TAG_REGEX = Regex("<@!?(\\d+)>")
        private const val MAP_REJECT_BUTTON = "map-submission-reject:1"
        private const val MAP_UPLOAD_BUTTON = "map-submission-upload:1"
        private const val MAP_UPDATE_BUTTON = "map-submission-update:1"
    }
}
