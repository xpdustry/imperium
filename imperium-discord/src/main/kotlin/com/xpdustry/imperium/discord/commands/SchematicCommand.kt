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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.utils.FileUpload

class SchematicCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val content = instances.get<MindustryContentHandler>()

    @ImperiumCommand(["schematic", "text"])
    suspend fun onSchematicCommand(interaction: SlashCommandInteraction, schematic: String) {
        val reply = interaction.deferReply(false).await()
        val parsed = content.getSchematic(schematic)
        if (parsed.isFailure) {
            reply
                .sendMessage("Failed to parse the schematic: ${parsed.exceptionOrNull()!!.message}")
                .await()
            return
        }

        val preview = content.getSchematicPreview(parsed.getOrThrow())
        if (preview.isFailure) {
            reply
                .sendMessage(
                    "Failed to generate schematic preview: ${preview.exceptionOrNull()!!.message}")
                .await()
            return
        }

        val stream = ByteArrayOutputStream()
        content.writeSchematic(parsed.getOrThrow(), stream).getOrThrow()

        reply
            .sendMessage(
                MessageCreate {
                    files +=
                        listOf(
                            FileUpload.fromData(
                                stream.toByteArray(),
                                "${parsed.getOrThrow().name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"),
                            FileUpload.fromStreamSupplier(
                                "preview.png", preview.getOrThrow()::inputStream))
                    embeds += Embed {
                        author(interaction.member!!)
                        title = parsed.getOrThrow().name()
                        image = "attachment://preview.png"
                    }
                })
            .await()
    }

    @ImperiumCommand(["schematic", "file"])
    suspend fun onSchematicCommand(interaction: SlashCommandInteraction, file: Message.Attachment) {
        val reply = interaction.deferReply(false).await()
        if (!file.fileName.endsWith(".msch")) {
            reply.sendMessage("Invalid schematic file!").await()
            return
        }

        if (file.size > SCHEMATIC_MAX_FILE_SIZE) {
            reply.sendMessage("Schematic file is too large!").await()
            return
        }

        val bytes = file.proxy.download().await().use(InputStream::readBytes)
        val result = content.getSchematic(bytes.inputStream())
        if (result.isFailure) {
            reply
                .sendMessage("Failed to parse the schematic: ${result.exceptionOrNull()!!.message}")
                .await()
            return
        }

        val parsed = result.getOrThrow()
        val preview = content.getSchematicPreview(parsed).getOrThrow()
        val name = "${parsed.name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"

        reply
            .sendMessage(
                MessageCreate {
                    files +=
                        listOf(
                            FileUpload.fromData(bytes, name),
                            FileUpload.fromStreamSupplier("preview.png", preview::inputStream))
                    embeds += Embed {
                        author(interaction.member!!)
                        title = parsed.name()
                        image = "attachment://preview.png"
                    }
                })
            .await()
    }

    companion object {
        // 2MB
        private const val SCHEMATIC_MAX_FILE_SIZE = 2 * 1024 * 1024
    }
}
