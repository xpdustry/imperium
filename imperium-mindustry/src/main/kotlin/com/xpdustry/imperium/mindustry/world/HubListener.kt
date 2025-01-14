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
package com.xpdustry.imperium.mindustry.world

import arc.Core
import arc.math.geom.Geometry
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.serialization.SerializablePolygon
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.getMindustryServerInfo
import com.xpdustry.imperium.mindustry.misc.id
import java.awt.Polygon
import java.nio.file.Path
import kotlin.experimental.or
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.gen.WorldLabel

class HubListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val config = instances.get<ImperiumConfig>().mindustry.hub
    private val directory = instances.get<Path>("directory").resolve("hub")
    private val portals = mutableMapOf<String, Portal>()
    private val building = PlayerMap<PortalBuilder>(instances.get())
    private val discovery = instances.get<Discovery>()
    private val debug = PlayerMap<Boolean>(instances.get())

    override fun onImperiumInit() {
        directory.toFile().mkdirs()
        if (config.preventPlayerActions) {
            Vars.netServer.admins.addActionFilter { false }
        }
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.SECONDS)
    fun onHubUpdate() {
        updatePortals()
        if (!Vars.state.isPlaying) return
        drawPortalBuilders()
        drawPortalDebug()
        Core.settings.put("totalPlayers", getTotalPlayerCount())
    }

    private fun getTotalPlayerCount() =
        Groups.player.size() +
            discovery.servers.values
                .map(Discovery.Server::data)
                .filterIsInstance<Discovery.Data.Mindustry>()
                .sumOf(Discovery.Data.Mindustry::playerCount)

    private fun drawPortalBuilders() {
        building.entries.forEach { (player, builder) ->
            for (point in builder.points) {
                Call.label(
                    player.con,
                    Iconc.alphaaaa.toString(),
                    1F,
                    point.x.toFloat() * Vars.tilesize,
                    point.y.toFloat() * Vars.tilesize,
                )
            }
        }
    }

    private fun drawPortalDebug() {
        debug.entries
            .filter { (_, debug) -> debug }
            .forEach { (player, _) ->
                portals.values.forEach { portal ->
                    for (i in 0 until portal.polygon.npoints) {
                        val ni = if (i == portal.polygon.npoints - 1) 0 else i + 1
                        Geometry.iterateLine(
                            0F, // Useless argument, why is it even there ?
                            portal.polygon.xpoints[i].toFloat(),
                            portal.polygon.ypoints[i].toFloat(),
                            portal.polygon.xpoints[ni].toFloat(),
                            portal.polygon.ypoints[ni].toFloat(),
                            1F,
                        ) { x, y ->
                            Call.label(player.con, Iconc.alphaaaa.toString(), 1F, x * Vars.tilesize, y * Vars.tilesize)
                        }
                    }
                    Call.label(
                        player.con,
                        portal.name,
                        1F,
                        portal.centerX * Vars.tilesize,
                        portal.centerY * Vars.tilesize,
                    )
                }
            }
    }

    @EventHandler
    fun onPlayEvent(event: EventType.PlayEvent) {
        portals.clear()
        portals.putAll(loadPortals())
        updatePortals()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadPortals(): Map<String, Portal> {
        val file = directory.resolve("${getCurrentMapName()}.json")
        if (file.notExists()) {
            return emptyMap()
        }
        return file.toFile().inputStream().use { Json.decodeFromStream<Map<String, Portal>>(it) }
    }

    @ImperiumCommand(["portal", "delete"], Rank.OWNER)
    @ClientSide
    fun onHubPortalListCommand(sender: CommandSender, name: String) {
        val portal = portals.remove(name)
        if (portal == null) {
            sender.error("A portal with that name does not exist.")
            return
        }
        val labels = portal.labels
        if (labels != null) {
            labels.error.hide()
            labels.overlays.forEach { (label, _) -> label.hide() }
        }
        savePortals()
        sender.reply("Deleted portal $name.")
    }

    @ImperiumCommand(["portal", "create"], Rank.OWNER)
    @ClientSide
    fun onHubPortalBuildCommand(sender: CommandSender, name: String) {
        if (building[sender.player] != null) {
            sender.error("You are already building a portal.")
            return
        }
        if (portals.containsKey(name)) {
            sender.error("A portal with that name already exists.")
            return
        }
        building[sender.player] = PortalBuilder(name, emptyList())
        sender.reply("Started building portal $name.")
    }

    @ImperiumCommand(["portal", "undo"], Rank.OWNER)
    @ClientSide
    fun onHubPortalUndoCommand(sender: CommandSender) {
        val builder = building[sender.player]
        if (builder == null) {
            sender.error("You are not building a portal.")
            return
        }
        if (builder.points.isEmpty()) {
            sender.error("You have no points to undo.")
            return
        }
        building[sender.player] = builder.copy(points = builder.points.dropLast(1))
        sender.reply("Removed last point.")
    }

    @ImperiumCommand(["portal", "cancel"], Rank.OWNER)
    @ClientSide
    fun onHubPortalCancelCommand(sender: CommandSender) {
        val builder = building.remove(sender.player)
        if (builder == null) {
            sender.error("You are not building a portal.")
            return
        }
        sender.reply("Cancelled portal ${builder.name}")
    }

    @ImperiumCommand(["portal", "list"], Rank.OWNER)
    @ClientSide
    fun onHubPortalListCommand(sender: CommandSender) {
        if (portals.isEmpty()) {
            sender.error("There are no portals.")
            return
        }
        sender.reply(
            buildString {
                append("-- [accent]Portals: --")
                for (portal in portals.values) {
                    append(
                        "\n[cyan]- [white]${portal.name} [lightgray](${portal.x}, ${portal.y}, ${portal.w}, ${portal.h})"
                    )
                }
            }
        )
    }

    @ImperiumCommand(["portal", "debug"], Rank.OWNER)
    @ClientSide
    fun onHubPortalDebugCommand(sender: CommandSender) {
        val debug = debug[sender.player] ?: false
        this.debug[sender.player] = !debug
        sender.reply("Debug mode is now ${if (!debug) "enabled" else "disabled"}.")
    }

    @EventHandler
    fun onBuilderTapEvent(event: EventType.TapEvent) {
        val builder = building[event.player] ?: return
        val point = ImmutablePoint(event.tile.x.toInt(), event.tile.y.toInt())
        if (portals.values.any { it.polygon.contains(point.x, point.y) }) {
            event.player.sendMessage("You cannot build a portal on another portal.")
            return
        }
        if (builder.points.contains(point)) {
            if (builder.points.size < 3) {
                event.player.sendMessage("You need at least 3 points to create a portal.")
                return
            }
            createPortal(event.player, builder)
            return
        }
        building[event.player] = builder.copy(points = builder.points + point)
        event.player.sendMessage("Added point $point to portal.")
    }

    @EventHandler
    fun onTeleportTapEvent(event: EventType.TapEvent) {
        if (building[event.player] != null) return
        val portal =
            portals.values.firstOrNull { it.polygon.contains(event.tile.x.toInt(), event.tile.y.toInt()) } ?: return
        val data = discovery.servers[portal.name]?.data ?: return
        if (data !is Discovery.Data.Mindustry) return
        Call.connect(event.player.con, data.host.hostAddress, data.port)
        Call.sendMessage("Player ${event.player.name} joined the server ${data.name}.")
    }

    private fun createPortal(player: Player, builder: PortalBuilder) {
        val polygon =
            Polygon(
                builder.points.map(ImmutablePoint::x).toIntArray(),
                builder.points.map(ImmutablePoint::y).toIntArray(),
                builder.points.size,
            )
        val portal = Portal(builder.name, polygon)
        portals[builder.name] = portal
        building.remove(player)
        try {
            savePortals()
            player.sendMessage("Created portal ${portal.name}.")
        } catch (error: Exception) {
            portals.remove(builder.name)
            player.sendMessage("An error occurred while creating portal ${portal.name}.")
            LOGGER.error("An error occurred while creating portal ${portal.name}.", error)
        }
        Core.app.post { updatePortals() }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun savePortals() {
        directory.resolve("${getCurrentMapName()}.json").outputStream().use { Json.encodeToStream(portals, it) }
    }

    private fun updatePortals() {
        for (portal in portals.values) {
            var labels = portal.labels
            if (labels == null) {
                labels =
                    Portal.Labels(createErrorLabel(portal), config.overlays.map { createWorldLabel(portal, it) to it })
                portal.labels = labels
            }
            val info = discovery.servers[portal.name]
            if (info == null) {
                labels.overlays.forEach { it.first.hide() }
                labels.error.text("[orange]Server not found.")
                labels.error.add()
                continue
            }
            val data = info.data
            if (data !is Discovery.Data.Mindustry) {
                labels.overlays.forEach { it.first.hide() }
                labels.error.text("[scarlet]Server is not a Mindustry server.")
                labels.error.add()
                continue
            }
            if (data.state == Discovery.Data.Mindustry.State.STOPPED) {
                labels.overlays.forEach { it.first.hide() }
                labels.error.text("[orange]Server is not open.")
                labels.error.add()
                continue
            }
            if (data.gameVersion != getMindustryServerInfo().gameVersion) {
                labels.overlays.forEach { it.first.hide() }
                labels.error.text("[scarlet]Server version mismatch.")
                labels.error.add()
                continue
            }
            labels.error.hide()
            labels.overlays.forEach { (label, overlay) ->
                label.text(formatText(overlay.text.trim(), data))
                label.add()
            }
        }
    }

    private fun formatText(text: String, info: Discovery.Data.Mindustry): String {
        return text
            .replace("%server_name%", info.name)
            .replace("%server_host%", info.host.toString())
            .replace("%server_port%", info.port.toString())
            .replace("%server_map_name%", info.mapName)
            .replace("%server_description%", info.description)
            .replace("%server_wave%", info.wave.toString())
            .replace("%server_player_count%", info.playerCount.toString())
            .replace("%server_player_limit%", info.playerLimit.toString())
            .replace("%server_game_version%", info.gameVersion.toString())
            .replace("%server_game_mode%", info.gamemode.name.lowercase())
            .replace("%server_game_mode_name%", info.gamemodeName ?: "unknown")
    }

    private fun createErrorLabel(portal: Portal) =
        WorldLabel.create().apply {
            x(portal.centerX * Vars.tilesize)
            y(portal.centerY * Vars.tilesize)
            fontSize(config.errorFontSize)
            flags(WorldLabel.flagOutline or WorldLabel.flagBackground)
        }

    private fun createWorldLabel(portal: Portal, overlay: MindustryConfig.Hub.Overlay) =
        WorldLabel.create().apply {
            x((portal.centerX + (overlay.offsetX * portal.w)) * Vars.tilesize)
            y((portal.centerY + (overlay.offsetY * portal.h)) * Vars.tilesize)
            fontSize(overlay.fontSize)
            var flags: Byte = 0
            if (overlay.outline) flags = flags or WorldLabel.flagOutline
            if (overlay.background) flags = flags or WorldLabel.flagBackground
            flags(flags)
        }

    private fun getCurrentMapName() =
        Vars.state.map.id ?: Vars.state.map.name() ?: error("The current map has no name.")

    @Serializable
    data class Portal(val name: String, val polygon: SerializablePolygon, @Transient var labels: Labels? = null) {
        val x: Int = polygon.xpoints.min()
        val y: Int = polygon.ypoints.min()
        val w: Int = polygon.xpoints.max() - x
        val h: Int = polygon.ypoints.max() - y

        val centerX: Float = x + (w / 2F)
        val centerY: Float = y + (h / 2F)

        data class Labels(val error: WorldLabel, val overlays: List<Pair<WorldLabel, MindustryConfig.Hub.Overlay>>)
    }

    data class PortalBuilder(val name: String, val points: List<ImmutablePoint>)

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
