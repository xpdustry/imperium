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

import arc.files.Fi
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.content.MapReloadMessage
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.content.MindustryMapManager
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
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

class MapListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private val maps = instances.get<MindustryMapManager>()
    private val cache = instances.get<Path>("directory").resolve("map-pool")
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        if (cache.notExists()) cache.createDirectory()

        messenger.consumer<MapReloadMessage> {
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
        val file = cache.resolve("${map.id}_${map.lastUpdate.toEpochMilli()}.msav")
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
