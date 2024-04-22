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

import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.net.Administration.PlayerInfo
import org.incendo.cloud.annotation.specifier.Range

class HistoryCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val history = instances.get<BlockHistory>()
    private val taps = PlayerMap<Long>(instances.get())
    private val users = instances.get<UserManager>()
    private val config = instances.get<ServerConfig.Mindustry>()
    private val renderer = instances.get<TimeRenderer>()

    @ImperiumCommand(["history", "player"])
    @ClientSide
    @ServerSide
    suspend fun onPlayerHistoryCommand(
        sender: CommandSender,
        player: PlayerInfo,
        @Range(min = "1", max = "50") limit: Int = 10
    ) {
        val entries = runMindustryThread { normalize(history.getHistory(player.id), limit) }
        if (entries.none()) {
            sender.sendWarning("No history found.")
            return
        }
        val builder =
            StringBuilder("[accent]History of player [white]").append(player.plainLastName())
        builder
            .append(" [lightgray](#")
            .append(users.findByUuid(player.id)?.snowflake ?: "unknown")
            .append(")")
        builder.append(":")
        for (entry in entries) {
            builder
                .append("\n[accent] > ")
                .append(
                    renderEntry(entry, name = false, id = false, position = true, indent = 3),
                )
        }

        sender.sendMessage(
            if (sender.isServer) builder.toString().stripMindustryColors() else builder.toString())
    }

    @EventHandler
    internal fun onPlayerTapEvent(event: EventType.TapEvent) =
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.DOUBLE_TAP_TILE_LOG)) {
                val last = taps[event.player]
                if (last != null &&
                    (System.currentTimeMillis() - last).milliseconds <
                        config.history.doubleClickDelay) {
                    taps.remove(event.player)
                    onTileHistoryCommand(
                        CommandSender.player(event.player), event.tile.x, event.tile.y)
                } else {
                    taps[event.player] = System.currentTimeMillis()
                }
            }
        }

    @ImperiumCommand(["history", "tile"])
    @ClientSide
    @ServerSide
    suspend fun onTileHistoryCommand(
        sender: CommandSender,
        @Range(min = "1") x: Short,
        @Range(min = "1") y: Short,
        @Range(min = "1", max = "50") limit: Int = 10,
    ) {
        val entries = runMindustryThread {
            normalize(history.getHistory(x.toInt(), y.toInt()), limit)
        }
        if (entries.none()) {
            sender.sendWarning("No history found.")
            return
        }
        val builder =
            StringBuilder("[accent]History of tile [white]")
                .append("(")
                .append(x)
                .append(", ")
                .append(y)
                .append(")[]:")
        for (entry in entries) {
            builder
                .append("\n[accent] > ")
                .append(renderEntry(entry, name = true, position = false, 3))
        }

        sender.sendMessage(
            if (sender.isServer) builder.toString().stripMindustryColors() else builder.toString())
    }

    private suspend fun renderEntry(
        entry: HistoryEntry,
        name: Boolean,
        position: Boolean,
        indent: Int,
        id: Boolean = true,
    ): String {
        val builder = StringBuilder("[white]")
        if (name) {
            builder.append(getName(entry.author))
            if (id && entry.author.uuid != null) {
                val snowflake = users.findByUuid(entry.author.uuid)?.snowflake ?: "unknown"
                builder.append(" [lightgray](#").append(snowflake).append(")")
            }
            builder.append("[white]: ")
        }
        when (entry.type) {
            HistoryEntry.Type.PLACING ->
                builder.append("Constructing [accent]").append(entry.block.name)
            HistoryEntry.Type.PLACE ->
                builder.append("Constructed [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAKING ->
                builder.append("Deconstructing [accent]").append(entry.block.name)
            HistoryEntry.Type.BREAK ->
                builder.append("Deconstructed [accent]").append(entry.block.name)
            HistoryEntry.Type.ROTATE ->
                builder
                    .append("Rotated [accent]")
                    .append(entry.block.name)
                    .append(" [white]to [accent]")
                    .append(getOrientation(entry.rotation))
            HistoryEntry.Type.CONFIGURE ->
                renderConfiguration(
                    builder,
                    entry,
                    entry.configuration,
                    indent,
                )
        }
        if (entry.type !== HistoryEntry.Type.CONFIGURE && entry.configuration != null) {
            renderConfiguration(
                builder.append(" ".repeat(indent)).append("\n[accent] > [white]"),
                entry,
                entry.configuration,
                indent + 3,
            )
        }
        builder.append("[white]")
        if (position) {
            builder.append(" at [accent](").append(entry.x).append(", ").append(entry.y).append(")")
        }
        builder.append(" [white]").append(renderer.renderRelativeInstant(entry.timestamp))
        return builder.toString()
    }

    private fun renderConfiguration(
        builder: StringBuilder,
        entry: HistoryEntry,
        config: HistoryConfig?,
        ident: Int,
    ) {
        when (config) {
            is HistoryConfig.Composite -> {
                builder.append("Configured [accent]").append(entry.block.name).append("[white]:")
                for (component in config.configurations) {
                    renderConfiguration(
                        builder.append("\n").append(" ".repeat(ident)).append("[accent] - [white]"),
                        entry,
                        component,
                        ident + 3,
                    )
                }
            }
            is HistoryConfig.Text -> {
                builder
                    .append("Edited [accent]")
                    .append(config.type.name.lowercase())
                    .append("[white] of [accent]")
                    .append(entry.block.name)
                if (config.type === HistoryConfig.Text.Type.MESSAGE) {
                    builder.append("[white] to [gray]").append(config.text)
                }
            }
            is HistoryConfig.Link -> {
                if (config.type === HistoryConfig.Link.Type.RESET) {
                    builder.append("Reset [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append(
                        if (config.type === HistoryConfig.Link.Type.CONNECT) "Connected"
                        else "Disconnected")
                    .append(" [accent]")
                    .append(entry.block.name)
                    .append("[white] ")
                    .append(if (config.type === HistoryConfig.Link.Type.CONNECT) "to" else "from")
                    .append(" [accent]")
                    .append(
                        config.positions.joinToString(", ") { point ->
                            "(${(point.x + entry.buildX)}, ${(point.y + entry.buildY)})"
                        },
                    )
            }
            is HistoryConfig.Canvas -> {
                builder.append("Edited [accent]").append(entry.block.name)
            }
            is HistoryConfig.Content -> {
                if (config.value == null) {
                    builder.append("Reset [accent]").append(entry.block.name)
                    return
                }
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value.name)
            }
            is HistoryConfig.Enable -> {
                builder
                    .append(if (config.value) "Enabled" else "Disabled")
                    .append(" [accent]")
                    .append(entry.block.name)
            }
            is HistoryConfig.Light -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.color.toHexString())
            }
            is HistoryConfig.Simple -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]")
                    .append(config.value?.toString() ?: "null")
            }
            null -> {
                builder
                    .append("Configured [accent]")
                    .append(entry.block.name)
                    .append("[white] to [accent]null")
            }
        }
    }

    private fun getName(author: HistoryAuthor): String {
        return if (author.uuid != null) Vars.netServer.admins.getInfo(author.uuid).lastName
        else author.team.name.lowercase() + " " + author.unit.name
    }

    private fun normalize(entries: List<HistoryEntry>, limit: Int) =
        entries
            .asReversed()
            .asSequence()
            .withIndex()
            .filter {
                it.index == 0 ||
                    (it.value.type != HistoryEntry.Type.BREAKING &&
                        it.value.type != HistoryEntry.Type.PLACING)
            }
            .map { it.value }
            .take(limit)
            .toList()

    private fun getOrientation(rotation: Int): String =
        when (rotation % 4) {
            0 -> "right"
            1 -> "top"
            2 -> "left"
            3 -> "bottom"
            else -> error("This should never happen")
        }
}
