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
package com.xpdustry.imperium.mindustry.world

import arc.Core
import arc.math.geom.Geometry
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.security.permission.Role
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.getMindustryServerInfo
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.command.sender.CommandSender
import fr.xpdustry.distributor.api.event.EventHandler
import java.awt.Polygon
import java.nio.file.Path
import kotlin.experimental.or
import kotlin.io.path.notExists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.gen.WorldLabel

class HubListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val config = instances.get<ServerConfig.Mindustry>().hub
    private val directory = instances.get<Path>("directory").resolve("hub")
    private val portals = mutableMapOf<String, Portal>()
    private val building = PlayerMap<PortalBuilder>(instances.get())
    private val discovery = instances.get<Discovery>()
    private val debug = PlayerMap<Boolean>(instances.get())
    private val gson =
        GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Polygon::class.java, PolygonTypeAdapter().nullSafe())
            .create()

    override fun onImperiumInit() {
        directory.toFile().mkdirs()

        if (config.preventPlayerActions) {
            Vars.netServer.admins.addActionFilter { false }
        }

        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(1.seconds)
                runMindustryThread {
                    updatePortals()
                    if (!Vars.state.isPlaying) return@runMindustryThread
                    drawPortalBuilders()
                    drawPortalDebug()
                }
            }
        }
    }

    private fun drawPortalBuilders() {
        building.entries.forEach { (player, builder) ->
            for (point in builder.points) {
                Call.label(
                    player.con,
                    Iconc.alphaaaa.toString(),
                    1F,
                    point.x.toFloat() * Vars.tilesize,
                    point.y.toFloat() * Vars.tilesize)
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
                            1F) { x, y ->
                                Call.label(
                                    player.con,
                                    Iconc.alphaaaa.toString(),
                                    1F,
                                    x * Vars.tilesize,
                                    y * Vars.tilesize)
                            }
                    }
                    Call.label(
                        player.con,
                        portal.name,
                        1F,
                        portal.centerX * Vars.tilesize,
                        portal.centerY * Vars.tilesize)
                }
            }
    }

    @EventHandler
    fun onPlayEvent(event: EventType.PlayEvent) {
        portals.clear()
        portals.putAll(loadPortals())
        updatePortals()
    }

    private fun loadPortals(): Map<String, Portal> {
        val file = directory.resolve("${getCurrentMapName()}.json")
        if (file.notExists()) {
            return emptyMap()
        }
        return gson.fromJson(file.toFile().reader(), PORTAL_LIST_TYPE_TOKEN.type)
    }

    @Command(["portal", "delete"], Role.OWNER)
    @ClientSide
    private fun onHubPortalListCommand(sender: CommandSender, name: String) {
        if (!portals.containsKey(name)) {
            sender.sendMessage("A portal with that name does not exist.")
            return
        }
        portals.remove(name)
        savePortals()
        sender.sendMessage("Deleted portal $name.")
    }

    @Command(["portal", "create"], Role.OWNER)
    @ClientSide
    private fun onHubPortalBuildCommand(
        sender: CommandSender,
        name: String,
    ) {
        if (building[sender.player] != null) {
            sender.sendMessage("You are already building a portal.")
            return
        }
        if (portals.containsKey(name)) {
            sender.sendMessage("A portal with that name already exists.")
            return
        }
        building[sender.player] = PortalBuilder(name, emptyList())
        sender.sendMessage("Started building portal $name.")
    }

    @Command(["portal", "undo"], Role.OWNER)
    @ClientSide
    private fun onHubPortalUndoCommand(sender: CommandSender) {
        val builder = building[sender.player]
        if (builder == null) {
            sender.sendMessage("You are not building a portal.")
            return
        }
        if (builder.points.isEmpty()) {
            sender.sendMessage("You have no points to undo.")
            return
        }
        building[sender.player] = builder.copy(points = builder.points.dropLast(1))
        sender.sendMessage("Removed last point.")
    }

    @Command(["portal", "cancel"], Role.OWNER)
    @ClientSide
    private fun onHubPortalCancelCommand(sender: CommandSender) {
        if (building[sender.player] == null) {
            sender.sendMessage("You are not building a portal.")
            return
        }
        building.remove(sender.player)
    }

    @Command(["portal", "list"], Role.OWNER)
    @ClientSide
    private fun onHubPortalListCommand(sender: CommandSender) {
        if (portals.isEmpty()) {
            sender.sendMessage("There are no portals.")
            return
        }
        sender.sendMessage(
            buildString {
                append("-- [accent]Portals: --")
                for (portal in portals.values) {
                    append(
                        "\n[cyan]- [white]${portal.name} [lightgray](${portal.x}, ${portal.y}, ${portal.w}, ${portal.h})")
                }
            })
    }

    @Command(["portal", "debug"], Role.OWNER)
    @ClientSide
    private fun onHubPortalDebugCommand(sender: CommandSender) {
        val debug = debug[sender.player] ?: false
        this.debug[sender.player] = !debug
        sender.sendMessage("Debug mode is now ${if (!debug) "enabled" else "disabled"}.")
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
            portals.values.firstOrNull {
                it.polygon.contains(event.tile.x.toInt(), event.tile.y.toInt())
            }
                ?: return
        val data = discovery.servers[portal.name]?.data ?: return
        if (data !is Discovery.Data.Mindustry) return
        Call.connect(event.player.con, data.host.hostAddress, data.port)
        Call.sendMessage("Player ${event.player.name} joined the server ${data.name}.")
    }

    private fun createPortal(player: Player, builder: PortalBuilder) {
        val polygon =
            Polygon(
                builder.points.map { it.x }.toIntArray(),
                builder.points.map { it.y }.toIntArray(),
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
            logger.error("An error occurred while creating portal ${portal.name}.", error)
        }
        Core.app.post { updatePortals() }
    }

    private fun savePortals() {
        directory.resolve("${getCurrentMapName()}.json").writeText(gson.toJson(portals))
    }

    private fun updatePortals() {
        for (portal in portals.values) {
            var labels = portal.labels
            if (labels == null) {
                labels =
                    Portal.Labels(
                        createErrorLabel(portal),
                        config.overlays.map { createWorldLabel(portal, it) to it })
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

    private fun createWorldLabel(portal: Portal, overlay: ServerConfig.Mindustry.Hub.Overlay) =
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
        Vars.state.map.tags.get("imperium-map-id")
            ?: Vars.state.map.tags.get("name") ?: error("The current map has no name.")

    data class Portal(
        val name: String,
        val polygon: Polygon,
        @Transient var labels: Labels? = null
    ) {
        val x: Int = polygon.xpoints.min()
        val y: Int = polygon.ypoints.min()
        val w: Int = polygon.xpoints.max() - x
        val h: Int = polygon.ypoints.max() - y

        val centerX: Float = x + (w / 2F)
        val centerY: Float = y + (h / 2F)

        data class Labels(
            val error: WorldLabel,
            val overlays: List<Pair<WorldLabel, ServerConfig.Mindustry.Hub.Overlay>>
        )
    }

    data class PortalBuilder(val name: String, val points: List<ImmutablePoint>)

    private class PolygonTypeAdapter : TypeAdapter<Polygon>() {
        override fun write(writer: JsonWriter, value: Polygon) {
            writer.beginArray()
            for (i in 0 until value.npoints) {
                writer.beginArray()
                writer.value(value.xpoints[i])
                writer.value(value.ypoints[i])
                writer.endArray()
            }
            writer.endArray()
        }

        override fun read(reader: JsonReader): Polygon {
            val polygon = Polygon()
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginArray()
                polygon.addPoint(reader.nextInt(), reader.nextInt())
                reader.endArray()
            }
            reader.endArray()
            return polygon
        }
    }

    companion object {
        private val logger by LoggerDelegate()
        private val PORTAL_LIST_TYPE_TOKEN =
            TypeToken.getParameterized(Map::class.java, String::class.java, Portal::class.java)
    }
}
