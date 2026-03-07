// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import mindustry.Vars
import mindustry.gen.Player

interface ClientDetector {
    fun isFooClient(player: Player): Boolean
}

class SimpleClientDetector(plugin: MindustryPlugin) : ClientDetector, ImperiumApplication.Listener {

    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }
    }

    override fun isFooClient(player: Player) = fooClients[player] == true
}
