/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.discord.command.InteractionSender
import com.xpdustry.imperium.discord.command.annotation.NonEphemeral
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.Embed
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.utils.FileUpload

class SchematicCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val content = instances.get<MindustryContentHandler>()

    @ImperiumCommand(["schematic", "text"])
    @NonEphemeral
    suspend fun onSchematicCommand(actor: InteractionSender.Slash, schematic: String) {
        val parsed = content.getSchematic(schematic)
        if (parsed.isFailure) {
            actor.respond("Failed to parse the schematic: ${parsed.exceptionOrNull()!!.message}")
            return
        }

        val preview = content.getSchematicPreview(parsed.getOrThrow())
        if (preview.isFailure) {
            actor.respond(
                "Failed to generate schematic preview: ${preview.exceptionOrNull()!!.message}")
            return
        }

        val stream = ByteArrayOutputStream()
        content.writeSchematic(parsed.getOrThrow(), stream).getOrThrow()

        actor.respond {
            addFiles(
                FileUpload.fromData(
                    stream.toByteArray(),
                    "${parsed.getOrThrow().name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"),
                FileUpload.fromStreamSupplier("preview.png", preview.getOrThrow()::inputStream))
            addEmbeds(
                Embed {
                    author(actor.member)
                    title = parsed.getOrThrow().name()
                    image = "attachment://preview.png"
                })
        }
    }

    @ImperiumCommand(["schematic", "file"])
    @NonEphemeral
    suspend fun onSchematicCommand(actor: InteractionSender.Slash, file: Message.Attachment) {
        if (!file.fileName.endsWith(".msch")) {
            actor.respond("Invalid schematic file!")
            return
        }

        if (file.size > SCHEMATIC_MAX_FILE_SIZE) {
            actor.respond("Schematic file is too large!")
            return
        }

        val bytes = file.proxy.download().await().use(InputStream::readBytes)
        val result = content.getSchematic(bytes.inputStream())
        if (result.isFailure) {
            actor.respond("Failed to parse the schematic: ${result.exceptionOrNull()!!.message}")
            return
        }

        val parsed = result.getOrThrow()
        val preview = content.getSchematicPreview(parsed).getOrThrow()
        val name = "${parsed.name().stripMindustryColors()}_${Random.nextInt(1000..9999)}.msch"

        actor.respond {
            addFiles(
                FileUpload.fromStreamSupplier(name, bytes::inputStream),
                FileUpload.fromStreamSupplier("preview.png", preview::inputStream))
            addEmbeds(
                Embed {
                    author(actor.member)
                    title = parsed.name()
                    image = "attachment://preview.png"
                })
        }
    }

    companion object {
        // 2MB
        private const val SCHEMATIC_MAX_FILE_SIZE = 2 * 1024 * 1024
    }
}
