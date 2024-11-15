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
package com.xpdustry.imperium.mindustry.map

import arc.files.Fi
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.function.Consumer
import mindustry.Vars
import mindustry.core.GameState
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.io.SaveIO
import mindustry.maps.Map
import mindustry.net.Administration
import mindustry.net.Packets.KickReason
import mindustry.world.Tiles

class MapLoader : Closeable {

    private val paused = Vars.state.isPaused

    init {
        if (Vars.state.isGame) {
            Groups.player.each { player: Player -> player.kick(KickReason.serverRestarting) }
            Vars.state.set(GameState.State.menu)
            Vars.net.closeServer()
        }
    }

    fun load(map: Map) {
        Vars.world.loadMap(map)
    }

    fun load(file: Path) {
        load(file.toFile())
    }

    fun load(file: File) {
        SaveIO.load(Fi(file))
        Vars.state.rules.sector = null
    }

    fun load(width: Int, height: Int, generator: Consumer<Tiles>) {
        Vars.logic.reset()
        Vars.world.loadGenerator(width, height, generator::accept)
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
}
