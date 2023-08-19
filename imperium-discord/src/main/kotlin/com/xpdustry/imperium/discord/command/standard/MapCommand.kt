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
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.database.Database
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.CoroutineHttpClient
import com.xpdustry.imperium.common.storage.Storage
import com.xpdustry.imperium.discord.command.Command
import com.xpdustry.imperium.discord.command.CommandActor
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import kotlinx.coroutines.future.await
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.message.MessageBuilder
import org.javacord.api.entity.message.component.ActionRow
import org.javacord.api.entity.message.component.Button
import org.javacord.api.entity.message.embed.EmbedBuilder
import java.net.http.HttpResponse.BodyHandlers
import kotlin.jvm.optionals.getOrNull

@Command("map")
class MapCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Discord>()
    private val database = instances.get<Database>()
    private val storage = instances.get<Storage>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()
    private val http = instances.get<CoroutineHttpClient>()

    override fun onImperiumInit() {
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

        val channel = discord.getMainServer().getTextChannelById(config.channels.maps).getOrNull()
            ?: throw IllegalStateException("Map submission channel not found")

        val message = MessageBuilder()
            .addAttachment(map.url, "${map.fileName}.msav")
            .addEmbed(
                EmbedBuilder()
                    .setTitle("Map Submission")
                    .addField("Name", preview.name, true)
                    .addField("Author", preview.author ?: "Unknown", true)
                    .addField("Description", preview.description ?: "Unknown", true)
                    .addField("Size", "${preview.width} x ${preview.height}", true)
                    .addField("Submitter", actor.interaction.user.mentionTag, true)
                    .addField("Status", "Pending review", true)
                    .setImage(preview.image),
            )
            .addComponents(
                ActionRow.of(
                    Button.primary("map-submission:approve:1", "Approve"),
                    Button.danger("map-submission:reject:1", "Reject"),
                ),
            )
            .send(channel)
            .await()

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
}
