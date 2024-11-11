package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import mindustry.gen.Player

interface MarkedPlayerManager {
    fun isMarked(player: Player): Boolean
    fun mark(player: Player)
}

class SimpleMarkedPlayerManager(plugin: MindustryPlugin) : MarkedPlayerManager, ImperiumApplication.Listener {
    private val marked = PlayerMap<Boolean>(plugin)

    override fun isMarked(player: Player): Boolean = marked[player] ?: false

    override fun mark(player: Player) {
        marked[player] = true
    }
}