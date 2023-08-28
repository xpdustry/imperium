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
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.interaction.InteractionActor
import com.xpdustry.imperium.discord.interaction.command.Command
import org.javacord.api.entity.Attachment
import org.javacord.api.entity.message.embed.EmbedBuilder

class SchematicCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val content = instances.get<MindustryContentHandler>()

    @Command("schematic", "text", ephemeral = false)
    suspend fun onSchematicCommand(actor: InteractionActor, schematic: String) {
        val result = content.getSchematic(schematic)
        if (result.isFailure) {
            actor.respond("Failed to parse the schematic.")
            return
        }

        actor.respond(
            EmbedBuilder()
                .setAuthor(actor.user)
                .setTitle(result.getOrThrow().name())
                .setImage(content.getSchematicPreview(result.getOrThrow()).getOrThrow()),
        )
    }

    @Command("schematic", "file", ephemeral = false)
    suspend fun onSchematicCommand(actor: InteractionActor, file: Attachment) {
        if (!file.fileName.endsWith(".msch")) {
            actor.respond("Invalid schematic file!")
            return
        }

        val result = content.getSchematic(file.asInputStream())
        if (result.isFailure) {
            actor.respond("Failed to parse the schematic.")
            return
        }

        actor.respond(
            EmbedBuilder()
                .setAuthor(actor.user)
                .setTitle(result.getOrThrow().name())
                .setImage(content.getSchematicPreview(result.getOrThrow()).getOrThrow()),
        )
    }
}
