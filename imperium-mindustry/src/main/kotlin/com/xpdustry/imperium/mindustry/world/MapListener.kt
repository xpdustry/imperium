// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import arc.files.Fi
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MapReloadMessage
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.message.MessageService
import com.xpdustry.imperium.common.message.subscribe
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.id
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.game.Gamemode
import mindustry.io.MapIO
import mindustry.maps.Map

@Inject
class MapListener(
    private val config: ImperiumConfig,
    private val maps: MindustryMapManager,
    @Named("directory") directory: Path,
    private val messenger: MessageService,
) : ImperiumApplication.Listener {
    private val cache = directory.resolve("map-pool")

    // Maps of the latest games played on this server, most recent first
    @Volatile private var recent = emptyList<Int>()
    @Volatile private var recentLimit = 0

    override fun onImperiumInit() {
        if (cache.notExists()) cache.createDirectory()

        Vars.maps.setMapProvider(::getNextMap)

        messenger.subscribe<MapReloadMessage> {
            if (config.mindustry.gamemode in it.gamemodes || it.gamemodes.isEmpty()) reloadMaps()
        }

        reloadMaps()
    }

    @ImperiumCommand(["reloadmaps"])
    @ServerSide
    fun onMapReloadCommand() {
        reloadMaps()
    }

    @EventHandler
    internal fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        Vars.state.map.id?.let(::pushRecent)
    }

    private fun getNextMap(mode: Gamemode, previous: Map?): Map? {
        val pool = Vars.maps.all().filter { it.id != null }.associateBy { it.id!! }
        if (pool.isEmpty()) return Vars.maps.shuffleMode.next(mode, previous)
        // In case the previous map somehow missed the play event
        val id = previous?.id
        val history = if (id != null && recent.firstOrNull() != id) listOf(id) + recent else recent
        return pickNextMap(pool.keys.toList(), history)?.let(pool::get)
    }

    private fun pushRecent(map: Int) {
        recent = (listOf(map) + recent).take(recentLimit)
    }

    private fun reloadMaps() {
        val old = Vars.maps.all().map { it.name().stripMindustryColors() }.toMutableSet()
        Vars.maps.reload()

        val pool =
            runBlocking(Dispatchers.IO) {
                maps.findAllMapsByGamemode(config.mindustry.gamemode).mapNotNull {
                    try {
                        downloadMapFromPool(it)
                    } catch (e: Exception) {
                        logger.error("Failed to load map from server pool, falling back to local maps.", e)
                        null
                    }
                }
            }

        if (pool.isEmpty()) {
            logger.warn("No maps found in server pool, falling back to local maps.")
        }

        recentLimit = pool.size * 2
        recent =
            try {
                runBlocking(Dispatchers.IO) { maps.findRecentlyPlayedMaps(config.server.name, recentLimit) }
            } catch (e: Exception) {
                logger.error("Failed to fetch the recently played maps, the map rotation will start fresh.", e)
                emptyList()
            }
        // The ongoing game is only saved in the database once it ends
        if (Vars.state.isGame) Vars.state.map.id?.let(::pushRecent)

        Vars.maps.all().addAll(pool)
        val now = Vars.maps.all().map { it.name().stripMindustryColors() }.toMutableSet()
        logger.info("Reloaded {} maps (added={}, removed={}).", now.size, (now - old).size, (old - now).size)
    }

    private suspend fun downloadMapFromPool(map: MindustryMap): Map {
        val file = cache.resolve("${map.id}_${map.lastUpdate.toEpochMilliseconds()}.msav")
        if (file.notExists()) {
            logger.debug("Downloading map {} (id={}) from serer pool.", map.name, map.id)
            file.outputStream().use { output -> maps.getMapInputStream(map.id)!!.use { input -> input.copyTo(output) } }
        }
        logger.debug("Loaded map {} (id={}) from server pool.", map.name, map.id)
        return MapIO.createMap(Fi(file.toFile()), true).also { it.id = map.id }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
