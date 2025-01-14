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
package com.xpdustry.imperium.mindustry.history

import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor.ACCENT
import com.xpdustry.distributor.api.translation.TranslationArguments.array
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.history.config.BlockConfig
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.translation.GRAY
import com.xpdustry.imperium.mindustry.translation.LIGHT_GRAY

interface HistoryRenderer {
    suspend fun render(entries: List<HistoryEntry>, x: Int, y: Int): Component

    suspend fun render(entries: List<HistoryEntry>, actor: HistoryActor): Component
}

class SimpleHistoryRenderer(
    private val users: UserManager,
    private val codec: IdentifierCodec,
    private val timeRenderer: TimeRenderer,
) : HistoryRenderer {

    override suspend fun render(entries: List<HistoryEntry>, x: Int, y: Int) =
        render0(
            components(translatable("imperium.history.header", array(ImmutablePoint(x, y).toComponent())), newline()),
            entries,
            name = true,
        )

    override suspend fun render(entries: List<HistoryEntry>, actor: HistoryActor) =
        render0(
            components(translatable("imperium.history.header", array(getDisplayName(actor))), newline()),
            entries,
            location = true,
        )

    private suspend fun render0(
        header: Component,
        entries: List<HistoryEntry>,
        name: Boolean = false,
        location: Boolean = false,
    ): Component =
        components()
            .append(header)
            .apply {
                val pairs = entries.flatMap { entry -> render0(entry).map { entry to it } }.iterator()
                if (!pairs.hasNext()) {
                    append(text(" > ", ACCENT))
                    append(translatable("imperium.history.none"))
                } else {
                    do {
                        val (entry, component) = pairs.next()
                        append(text(" > ", ACCENT))
                        if (name) {
                            append(getDisplayName(entry.actor), text(":"), space())
                        }
                        append(component)
                        if (location) {
                            append(
                                space(),
                                translatable(
                                    "imperium.history.location",
                                    array(ImmutablePoint(entry.x, entry.y).toComponent()),
                                ),
                            )
                        }
                        append(space())
                        append(text(timeRenderer.renderRelativeInstant(entry.timestamp), GRAY))
                        if (pairs.hasNext()) {
                            append(newline())
                        }
                    } while (pairs.hasNext())
                }
            }
            .build()

    private fun render0(entry: HistoryEntry): List<Component> {
        var components =
            when (entry.type) {
                HistoryEntry.Type.PLACING ->
                    listOf(translatable("imperium.history.type.placing", array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.PLACE ->
                    listOf(translatable("imperium.history.type.place", array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.BREAKING ->
                    listOf(translatable("imperium.history.type.breaking", array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.BREAK ->
                    listOf(translatable("imperium.history.type.break", array(translatable(entry.block, ACCENT))))
                HistoryEntry.Type.ROTATE ->
                    listOf(
                        translatable(
                            "imperium.history.type.rotate",
                            array(translatable(entry.block, ACCENT), getDisplayOrientation(entry.rotation)),
                        )
                    )
                HistoryEntry.Type.CONFIGURE -> render0(entry, entry.config)
            }
        if (entry.type != HistoryEntry.Type.CONFIGURE && entry.config != null) {
            components = render0(entry, entry.config) + components
        }
        return components
    }

    private fun render0(entry: HistoryEntry, config: BlockConfig?): List<Component> =
        when (config) {
            is BlockConfig.Composite -> config.configs.flatMap { render0(entry, it) }
            is BlockConfig.Text ->
                listOf(translatable("imperium.history.type.configure.text", array(translatable(entry.block, ACCENT))))
            is BlockConfig.Link ->
                listOf(
                    translatable()
                        .modify {
                            if (config.connection) {
                                it.setKey("imperium.history.type.configure.link")
                            } else {
                                it.setKey("imperium.history.type.configure.unlink")
                            }
                        }
                        .setParameters(
                            array(
                                translatable(entry.block, ACCENT),
                                components()
                                    .apply {
                                        for ((i, point) in config.positions.withIndex()) {
                                            append(
                                                point.copy(x = point.x + entry.x, y = point.y + entry.y).toComponent()
                                            )
                                            if (i < config.positions.size - 1) append(text(", "))
                                        }
                                    }
                                    .build(),
                            )
                        )
                        .build()
                )
            is BlockConfig.Canvas ->
                listOf(translatable("imperium.history.type.configure.canvas", array(translatable(entry.block, ACCENT))))
            is BlockConfig.Content ->
                listOf(
                    translatable(
                        "imperium.history.type.configure.content",
                        array(translatable(entry.block, ACCENT), translatable(config.value, ACCENT)),
                    )
                )
            is BlockConfig.Enable ->
                listOf(
                    translatable(
                        if (config.value) {
                            "imperium.history.type.configure.enabled"
                        } else {
                            "imperium.history.type.configure.disabled"
                        },
                        array(translatable(entry.block, ACCENT)),
                    )
                )
            is BlockConfig.Light ->
                listOf(
                    translatable(
                        "imperium.history.type.configure.light",
                        array(translatable(entry.block, ACCENT), text("%06X".format(0xFFFFFF and config.color), ACCENT)),
                    )
                )
            is BlockConfig.Reset ->
                listOf(translatable("imperium.history.type.configure.reset", array(translatable(entry.block, ACCENT))))
            else -> emptyList()
        }

    private suspend fun getDisplayName(author: HistoryActor) =
        if (author.player != null) {
            users.findByUuid(author.player)?.let {
                components(text(it.lastName, ACCENT), space(), text("#${codec.encode(it.id)}", LIGHT_GRAY))
            } ?: text("Unknown", ACCENT)
        } else {
            components(ACCENT, translatable(author.team), space(), translatable(author.unit))
        }

    private fun getDisplayOrientation(rotation: Int): Component =
        when (rotation % 4) {
            0 -> translatable("imperium.history.orientation.right", ACCENT)
            1 -> translatable("imperium.history.orientation.top", ACCENT)
            2 -> translatable("imperium.history.orientation.left", ACCENT)
            3 -> translatable("imperium.history.orientation.bottom", ACCENT)
            else -> error("This should never happen")
        }

    private fun ImmutablePoint.toComponent() =
        components(LIGHT_GRAY, text('('), number(x, ACCENT), text(", "), number(y, ACCENT), text(')'))
}
