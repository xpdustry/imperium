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
package com.xpdustry.imperium.discord.command.standard

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.MindustryMap
import com.xpdustry.imperium.common.database.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.CoroutineHttpClient
import com.xpdustry.imperium.discord.command.Command
import com.xpdustry.imperium.discord.command.CommandActor
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.channel.AutoArchiveDuration
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.component.ActionRow
import org.javacord.api.entity.message.component.Button
import org.javacord.api.entity.message.embed.Embed
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.interaction.ButtonInteraction
import java.awt.Color
import java.net.http.HttpResponse.BodyHandlers
import kotlin.jvm.optionals.getOrNull

@Command("map")
class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()
    private val http = instances.get<CoroutineHttpClient>()

    override fun onImperiumInit() {
        discord.getMainServer().addButtonClickListener { event ->
            if (event.buttonInteraction.customId == "map-submission:rejected:1") {
                ImperiumScope.MAIN.launch { onMapRejected(event.buttonInteraction) }
            } else if (event.buttonInteraction.customId == "map-submission:approved:1") {
                ImperiumScope.MAIN.launch { onMapApproved(event.buttonInteraction) }
            }
        }
    }

    @Command("submit")
    suspend fun onSubmitCommand(actor: CommandActor, map: Attachment) {
        if (!map.fileName.endsWith(".msav")) {
            actor.updater.setContent("Invalid map file!").update().await()
            return
        }

        val response = http.get(map.url.toURI(), BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            actor.updater.setContent("Unable to fetch map file!").update().await()
            return
        }

        val preview = content.getMapPreview(response.body()).getOrThrow()
        if (preview.name == null) {
            actor.updater.setContent("The map does not have a name!").update().await()
            return
        }

        if (maps.findMapByName(preview.name) != null) {
            actor.updater.setContent("A map with that name already exists!").update().await()
            return
        }

        val channel = discord.getMainServer().getTextChannelById(config.channels.maps).getOrNull()
            ?: throw IllegalStateException("Map submission channel not found")

        val message = MessageBuilder()
            .addAttachment(map.url, map.fileName)
            .addEmbed(
                EmbedBuilder()
                    .setColor(MINDUSTRY_COLOR)
                    .setTitle("Map Submission")
                    .addField("Name", preview.name)
                    .addField("Author", preview.author ?: "Unknown")
                    .addField("Description", preview.description ?: "Unknown")
                    .addField("Size", "${preview.width} x ${preview.height}")
                    .addField("Submitter", actor.interaction.user.mentionTag)
                    .addField("Status", "Pending review", true)
                    .setImage(preview.image),
            )
            .addComponents(
                ActionRow.of(
                    Button.primary("map-submission:approved:1", "Approve"),
                    Button.danger("map-submission:rejected:1", "Reject"),
                ),
            )
            .send(channel)
            .await()

        message.createThread("Comments for ${preview.name}", AutoArchiveDuration.THREE_DAYS).await()

        actor.updater.addEmbed(
            EmbedBuilder()
                .setDescription("Your map has been submitted for review. Check the status [here](${message.link})."),
        ).update().await()
    }

    @Command("list")
    suspend fun onListCommand(actor: CommandActor) = Unit

    @Command("update")
    suspend fun onUpdateCommand(actor: CommandActor) = Unit

    @Command("info")
    suspend fun onInfoCommand(actor: CommandActor) = Unit

    private suspend fun onMapRejected(interaction: ButtonInteraction) {
        val updater = interaction.respondLater(true).await()
        interaction.message.createUpdater()
            .removeAllEmbeds()
            .addEmbed(
                interaction.message.embeds.first().replaceFields("Status" to "Rejected").setColor(Color.RED),
            )
            .removeAllComponents()
            .applyChanges()
            .await()

        interaction.message.embeds.first().fields.find { it.name == "Submitter" }?.value
            ?.let { discord.api.getUserById(MENTION_TAG_REGEX.find(it)!!.groupValues[1].toLong()) }
            ?.await()
            ?.sendMessage(
                EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Oh no!")
                    .setDescription("Your [map submission](${interaction.message.link}) has been rejected by ${interaction.user.mentionTag}."),
            )
            ?.await()

        updater.setContent("Map submission rejected!").update().await()
    }

    private suspend fun onMapApproved(interaction: ButtonInteraction) {
        val embed = interaction.message.embeds.first()
        val name = embed.getFieldValue("Name")!!
        val attachment = interaction.message.attachments.first()
        val updater = interaction.respondLater(true).await()

        if (maps.findMapByName(name) != null) {
            updater
                .setContent("A map with that name already exists!")
                .update()
            return
        }

        if (!attachment.fileName.endsWith(".msav")) {
            updater
                .setContent("Invalid map file!")
                .update()
            return
        }

        val response = http.get(attachment.url.toURI(), BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            updater
                .setContent("Unable to fetch map file!")
                .update()
            return
        }

        val size = embed.getFieldValue("Size")!!
        val entry = MindustryMap(
            name = name,
            description = embed.getFieldValue("Description")!!,
            author = embed.getFieldValue("Author")!!,
            width = size.substringBefore("x").trim().toInt(),
            height = size.substringAfter("x").trim().toInt(),
        )

        maps.uploadMap(entry, response.body())

        interaction.message.createUpdater()
            .removeAllEmbeds()
            .addEmbed(
                interaction.message.embeds.first().replaceFields("Status" to "Approved").setColor(Color.GREEN),
            )
            .removeAllComponents()
            .applyChanges()
            .await()

        interaction.message.embeds.first().getFieldValue("Submitter")
            ?.let { discord.api.getUserById(MENTION_TAG_REGEX.find(it)!!.groupValues[1].toLong()) }
            ?.await()
            ?.sendMessage(
                EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Congratulations!")
                    .setDescription("Your [map submission](${interaction.message.link}) has been approved by ${interaction.user.mentionTag}."),
            )
            ?.await()

        updater.setContent("Map submission approved!").update().await()
    }

    private fun Embed.replaceFields(vararg replacements: Pair<String, String>): EmbedBuilder {
        val map = replacements.toMap()
        val builder = toBuilder().removeAllFields()
        fields.forEach {
            builder.addField(it.name, map.getOrDefault(it.name, it.value), it.isInline)
        }
        return builder
    }

    private fun Embed.getFieldValue(name: String): String? = fields.find { it.name == name }?.value

    companion object {
        private val MENTION_TAG_REGEX = Regex("<@!?(\\d+)>")
        private val MINDUSTRY_COLOR = Color(0xffd37f)
    }
}
