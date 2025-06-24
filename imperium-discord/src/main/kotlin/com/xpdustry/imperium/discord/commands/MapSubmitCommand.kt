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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.Lowercase
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.database.tryDecode
import com.xpdustry.imperium.common.image.inputStream
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.MINDUSTRY_ACCENT_COLOR
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.permission.Permission
import com.xpdustry.imperium.discord.command.MenuCommand
import com.xpdustry.imperium.discord.command.annotation.AlsoAllow
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.misc.Embed
import com.xpdustry.imperium.discord.misc.ImperiumEmojis
import com.xpdustry.imperium.discord.misc.MessageCreate
import com.xpdustry.imperium.discord.misc.await
import com.xpdustry.imperium.discord.misc.awaitVoid
import com.xpdustry.imperium.discord.service.DiscordService
import java.awt.Color
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.utils.FileUpload

class MapSubmitCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val maps = instances.get<MindustryMapManager>()
    private val content = instances.get<MindustryContentHandler>()
    private val discord = instances.get<DiscordService>()
    private val codec = instances.get<IdentifierCodec>()

    @ImperiumCommand(["map", "submit"])
    suspend fun onMapSubmitCommand(
        interaction: SlashCommandInteraction,
        map: Message.Attachment,
        @Lowercase updating: String? = null,
        notes: String? = null,
    ) {
        val reply = interaction.deferReply(false).await()
        if (!map.fileName.endsWith(".msav")) {
            reply.sendMessage("Invalid map file!").await()
            return
        }

        if (updating != null) {
            val id = codec.tryDecode(updating)
            if (id == null) {
                reply.sendMessage("Invalid map identifier!").await()
                return
            }
            if (maps.findMapById(id) == null) {
                reply.sendMessage("The map to update does not exist!").await()
                return
            }
        }

        if (map.size > MindustryMap.MAX_MAP_FILE_SIZE) {
            reply.sendMessage("The map file is too big, please submit reasonably sized maps.").await()
            return
        }

        val bytes = map.proxy.download().await().use(InputStream::readBytes)
        val result = content.getMapMetadataWithPreview(bytes.inputStream())
        if (result.isFailure) {
            reply.sendMessage("Invalid map file: " + result.exceptionOrNull()!!.message).await()
            return
        }
        val (meta, preview) = result.getOrThrow()

        if (meta.width > MindustryMap.MAX_MAP_SIDE_SIZE || meta.height > MindustryMap.MAX_MAP_SIDE_SIZE) {
            reply
                .sendMessage(
                    "The map is bigger than ${MindustryMap.MAX_MAP_SIDE_SIZE} blocks, please submit reasonably sized maps."
                )
                .await()
            return
        }

        val channel =
            discord.getMainServer().getTextChannelById(config.discord.channels.maps)
                ?: throw IllegalStateException("Map submission channel not found")

        val message =
            channel
                .sendMessage(
                    MessageCreate {
                        files +=
                            FileUpload.fromStreamSupplier(
                                "${meta.name.stripMindustryColors()}.msav",
                                bytes::inputStream,
                            )
                        files += FileUpload.fromStreamSupplier("preview.png", preview::inputStream)
                        embeds += Embed {
                            color = MINDUSTRY_ACCENT_COLOR.rgb
                            title = "Map Submission"
                            field("Submitter", interaction.member!!.asMention, false)
                            field("Name", meta.name.stripMindustryColors(), false)
                            field("Author", meta.author?.stripMindustryColors() ?: "Unknown", false)
                            field("Description", meta.description?.stripMindustryColors() ?: "Unknown", false)
                            field("Size", "${preview.width} x ${preview.height}", false)
                            if (notes != null) {
                                field("Notes", notes, false)
                            }
                            if (updating != null) {
                                field("Updating", "`$updating`", false)
                            }
                            image = "attachment://preview.png"
                        }
                        components +=
                            ActionRow.of(
                                Button.primary(MAP_ACCEPT_BUTTON, "Accept").withEmoji(ImperiumEmojis.CHECK_MARK),
                                Button.danger(MAP_REJECT_BUTTON, "Reject").withEmoji(ImperiumEmojis.WASTE_BASKET),
                            )
                    }
                )
                .await()

        message
            .createThreadChannel("Comments for ${meta.name.stripMindustryColors()}")
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS)
            .await()

        reply
            .sendMessageEmbeds(
                Embed {
                    color = MINDUSTRY_ACCENT_COLOR.rgb
                    description = "Your map has been submitted for review. Check the status [here](${message.jumpUrl})."
                }
            )
            .await()
    }

    @MenuCommand(MAP_REJECT_BUTTON, Rank.ADMIN)
    @AlsoAllow(Permission.MANAGE_MAP)
    private suspend fun onMapReject(interaction: ButtonInteraction) {
        val reply = interaction.deferReply(true).await()
        updateSubmissionEmbed(interaction, Color.RED, "rejected")
        reply.sendMessage("Map submission rejected!").await()
    }

    @MenuCommand(MAP_ACCEPT_BUTTON, Rank.ADMIN)
    @AlsoAllow(Permission.MANAGE_MAP)
    private suspend fun onMapAccept(interaction: ButtonInteraction) {
        val reply = interaction.deferReply(true).await()
        val attachment = interaction.message.attachments.first()
        val meta = attachment.proxy.download().await().use { content.getMapMetadata(it).getOrThrow() }

        val target =
            interaction.message.embeds.first().getFieldValue("Updating")?.let {
                val result = maps.findMapById(codec.decode(it.replace("`", "")))
                if (result == null) {
                    reply.sendMessage("The map to update no longer exist! Report it to a moderator.").await()
                    return
                }
                result
            }

        val id: Int
        if (target == null) {
            id =
                maps.createMap(
                    name = meta.name.stripMindustryColors(),
                    description = meta.description?.stripMindustryColors(),
                    author = meta.author?.stripMindustryColors(),
                    width = meta.width,
                    height = meta.height,
                    stream = { attachment.proxy.download().join() },
                )
        } else {
            id = target.id
            maps.updateMap(
                id = target.id,
                description = meta.description?.stripMindustryColors(),
                author = meta.author?.stripMindustryColors(),
                width = meta.width,
                height = meta.height,
                stream = { attachment.proxy.download().join() },
            )
        }

        updateSubmissionEmbed(interaction, Color.GREEN, "accepted", id)
        reply.sendMessage("Map submission uploaded! The map id is `${codec.encode(id)}`.").await()
    }

    private suspend fun updateSubmissionEmbed(
        interaction: ButtonInteraction,
        color: Color,
        verb: String,
        id: Int? = null,
    ) {
        interaction.message
            .editMessageEmbeds(
                Embed(interaction.message.embeds.first()) {
                    this@Embed.color = color.rgb
                    field("Reviewer", interaction.member!!.asMention, false)
                    if (id != null && interaction.message.embeds.first().getFieldValue("Updating") == null) {
                        field("Identifier", "`${codec.encode(id)}`", false)
                    }
                    image = "attachment://preview2.png"
                }
            )
            .setComponents()
            .setFiles(
                FileUpload.fromStreamSupplier(interaction.message.attachments.first().fileName) {
                    interaction.message.attachments.first().proxy.download().join()
                },
                // Why do I have to fight discord API to simply update an image in an embed ?
                // I hate it
                FileUpload.fromStreamSupplier("preview2.png") {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    URI(interaction.message.embeds.first().image!!.url!!).toURL().openStream()
                },
            )
            .await()

        val member =
            discord
                .getMainServer()
                .getMemberById(
                    MENTION_TAG_REGEX.find(interaction.message.embeds.first().getFieldValue("Submitter")!!)!!
                        .groups[1]!!
                        .value
                        .toLong()
                ) ?: return

        // TODO Band-aid fix for contributor, cleanup this damn role system
        discord.getMainServer().getRolesByName("Contributor", true).singleOrNull()?.let {
            discord.getMainServer().addRoleToMember(member, it).awaitVoid()
        }

        member.user
            .openPrivateChannel()
            .await()
            .sendMessageEmbeds(
                Embed {
                    this@Embed.color = color.rgb
                    description =
                        "Your [map submission](${interaction.message.jumpUrl}) has been $verb by ${interaction.member!!.asMention}."
                }
            )
    }

    private fun MessageEmbed.getFieldValue(name: String): String? = fields.find { it.name == name }?.value

    companion object {
        private val MENTION_TAG_REGEX = Regex("<@!?(\\d+)>")
        private const val MAP_ACCEPT_BUTTON = "map-submission-accept:2"
        private const val MAP_REJECT_BUTTON = "map-submission-reject:2"
    }
}
