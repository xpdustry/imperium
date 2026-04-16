// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.content

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.addSuspendingEventListener
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.utils.FileUpload

@Inject
class MindustryContentListener(private val discord: DiscordService, private val content: MindustryContentHandler) :
    ImperiumApplication.Listener {

    override fun onImperiumInit() {
        discord.jda.addSuspendingEventListener<MessageReceivedEvent> { event ->
            if (event.author.isBot || event.author.isSystem || event.isWebhookMessage) {
                return@addSuspendingEventListener
            }
            onMindustryContent(event.channel, event.message, event.member!!)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun onMindustryContent(channel: MessageChannel, message: Message, member: Member) {
        val maps = mutableListOf<Triple<ParsedMindustryMapMetadata, BufferedImage, Message.Attachment>>()
        val schematics = mutableListOf<Pair<ParsedMindustrySchematic, Path>>()
        var delete = false

        if (message.contentRaw.startsWith(SCHEMATIC_HEADER_PREFIX)) {
            delete = true
            schematics +=
                content
                    .parseSchematic(message.contentRaw)
                    .onFailure {
                        logger.error("Failed to parse text schematic", it)
                        channel.sendMessage("Failed to parse text schematic").await()
                        return
                    }
                    .getOrThrow() to createTempFile().apply { writeBytes(Base64.decode(message.contentRaw)) }
        }

        val attachments = message.attachments
        if (attachments.isNotEmpty() && message.contentRaw.isEmpty()) {
            delete = true
        }

        if (message.startedThread != null) {
            delete = false
        }

        message.attachments.forEach { attachment ->
            try {
                if (attachment.fileExtension == "txt") {
                    if (attachment.size > SCHEMATIC_MAX_FILE_SIZE) {
                        channel.sendMessage("Schematic file is too large!").await()
                        return
                    }
                    val text = attachment.proxy.download().await().bufferedReader().use { it.readText() }
                    if (text.startsWith(SCHEMATIC_HEADER_PREFIX)) {
                        schematics +=
                            content
                                .parseSchematic(text)
                                .onFailure {
                                    logger.error("Failed to parse text schematic", it)
                                    channel.sendMessage("Failed to parse text schematic").await()
                                    return
                                }
                                .getOrThrow() to createTempFile().apply { writeBytes(Base64.decode(text)) }
                    }
                } else if (attachment.fileExtension == "msch") {
                    if (attachment.size > SCHEMATIC_MAX_FILE_SIZE) {
                        channel.sendMessage("Schematic file is too large!").await()
                        return
                    }
                    val tempFile = createTempFile()
                    attachment.proxy.downloadToFile(tempFile.toFile()).await()
                    schematics +=
                        content
                            .parseSchematic(tempFile.toFile())
                            .onFailure {
                                logger.error("Failed to parse binary schematic", it)
                                channel.sendMessage("Failed to parse binary schematic").await()
                                return
                            }
                            .getOrThrow() to tempFile
                } else if (attachment.fileExtension == "msav") {
                    if (attachment.size > MindustryMap.MAX_MAP_FILE_SIZE) {
                        channel.sendMessage("The map file is too big, please submit reasonably sized maps.").await()
                        return
                    }
                    val tempFile = createTempFile()
                    attachment.proxy.downloadToPath(tempFile).await()
                    val metadata =
                        content
                            .parseMap(tempFile.toFile())
                            .onFailure {
                                logger.error("Failed to parse map", it)
                                channel.sendMessage("Failed to parse map").await()
                                return
                            }
                            .getOrThrow()
                    if (
                        metadata.width > MindustryMap.MAX_MAP_SIDE_SIZE ||
                            metadata.height > MindustryMap.MAX_MAP_SIDE_SIZE
                    ) {
                        channel
                            .sendMessage(
                                "The map is bigger than ${MindustryMap.MAX_MAP_SIDE_SIZE} blocks, please submit reasonably sized maps."
                            )
                            .await()
                        return
                    }
                    val preview =
                        content
                            .renderMap(tempFile.toFile())
                            .onFailure {
                                logger.error("Failed to render map", it)
                                channel.sendMessage("Failed to render map").await()
                                return
                            }
                            .getOrThrow()
                    maps += Triple(metadata, preview, attachment)
                } else {
                    delete = false
                }
            } catch (e: Exception) {
                logger.error("Failed to parse mindustry content", e)
                channel.sendMessage("Failed to parse mindustry content").await()
                return
            }
        }

        schematics.forEach { (schematic, file) ->
            val preview =
                content
                    .renderSchematic(file.toFile())
                    .onFailure {
                        logger.error("Failed to generate schematic preview", it)
                        channel.sendMessage("Failed to generate schematic preview").await()
                        return
                    }
                    .getOrThrow()
            val requirements = buildString {
                val iterator = schematic.requirements.iterator()
                while (iterator.hasNext()) {
                    val (item, amount) = iterator.next()
                    append(itemNameToEmoji(item))
                    append(' ')
                    append(amount)
                    if (iterator.hasNext()) append(' ')
                }
            }
            channel
                .sendMessage(
                    MessageCreate {
                        files += FileUpload.fromData(file, "${schematic.name}.msch")
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            author(member)
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = schematic.name
                            field("Requirements", requirements, false)
                            description = schematic.description
                            image = "attachment://preview.png"
                        }
                    }
                )
                .await()
            file.deleteExisting()
        }

        maps.forEach { (meta, preview, attachement) ->
            channel
                .sendMessage(
                    MessageCreate {
                        files +=
                            FileUpload.fromStreamSupplier(meta.name + ".msav") { attachement.proxy.download().join() }
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = meta.name
                            image = "attachment://preview.png"
                            field("Author", meta.author, false)
                            field("Description", meta.description, false)
                            field("Size", "${preview.width} x ${preview.height}", false)
                        }
                    }
                )
                .await()
        }

        if (delete) {
            channel.deleteMessageById(message.idLong).await()
        }
    }

    private fun itemNameToEmoji(item: String) =
        discord.getMainServer().getEmojisByName(item.replace("-", ""), true).firstOrNull()?.asMention ?: ":question:"

    companion object {
        private val logger by LoggerDelegate()
        private const val SCHEMATIC_MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
        private const val SCHEMATIC_HEADER_PREFIX = "bXNjaA"
    }
}
