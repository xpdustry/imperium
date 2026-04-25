// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import arc.files.Fi
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
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
import com.xpdustry.imperium.mindustry.misc.id
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlinx.coroutines.runBlocking
import mindustry.Vars
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

    override fun onImperiumInit() {
        if (cache.notExists()) cache.createDirectory()

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

    private fun reloadMaps() {
        val old = Vars.maps.all().map { it.name().stripMindustryColors() }.toMutableSet()
        Vars.maps.reload()

        val pool =
            runBlocking(ImperiumScope.IO.coroutineContext) {
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
