// SPDX-License-Identifier: GPL-3.0-only
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
