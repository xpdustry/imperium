/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.map

import arc.files.Fi
import mindustry.Vars
import mindustry.core.GameState
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.io.SaveIO
import mindustry.maps.Map
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.world.Tiles
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.function.Consumer

class MapLoader private constructor() : Closeable {
    private val paused: Boolean = Vars.state.isPaused

    init {
        if (Vars.state.isGame) {
            Groups.player.each { player: Player -> player.kick(KickReason.serverRestarting) }
            Vars.state.set(GameState.State.menu)
            Vars.net.closeServer()
        }
    }

    fun load(map: Map?) {
        Vars.world.loadMap(map)
    }

    fun load(file: File?) {
        SaveIO.load(Fi(file))
        Vars.state.rules.sector = null
    }

    fun load(width: Int, height: Int, generator: Consumer<Tiles>) {
        Vars.logic.reset()
        Vars.world.loadGenerator(width, height) { t: Tiles -> generator.accept(t) }
    }

    fun <C : MapContext> load(generator: MapGenerator<C>): C {
        Vars.logic.reset()
        Vars.world.beginMapLoad()

        // Clear tile entities
        for (tile in Vars.world.tiles) {
            tile.build?.remove()
        }

        // I hate it
        val context = generator.createContext()
        generator.generate(context)
        Vars.world.tiles = Tiles(1, 1)
        for (action in context.actions) {
            Vars.world.tiles = action.apply(Vars.world.tiles)
        }
        Vars.world.endMapLoad()
        return context
    }

    @Throws(IOException::class)
    override fun close() {
        Vars.state.set(if (paused) GameState.State.paused else GameState.State.playing)
        try {
            Vars.net.host(Administration.Config.port.num())
        } catch (exception: IOException) {
            Vars.state.set(GameState.State.menu)
            throw exception
        }
    }

    companion object {
        fun create(): MapLoader {
            return MapLoader()
        }
    }
}
