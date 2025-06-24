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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.misc.getMindustryVersion
import mindustry.gen.Call
import mindustry.gen.Player

class SwitchCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val discovery = instances.get<Discovery>()
    private val plugin = instances.get<MindustryPlugin>()
    private val menu = SwitchWindowManager(plugin, discovery)

    @ImperiumCommand(["switch"])
    @ClientSide
    fun onSwitchCommand(sender: CommandSender, server: String? = null) {
        if (server == null) {
            menu.create(sender.player).show()
            return
        }
        val data = discovery.servers[server]?.data
        if (data == null) {
            sender.error("[accent]Server not found.")
            return
        }
        if (data !is Discovery.Data.Mindustry) {
            sender.error("[accent]Server is not a Mindustry server.")
            return
        }
        if (data.state == Discovery.Data.Mindustry.State.STOPPED) {
            sender.error("[accent]Server is not available.")
            return
        }
        if (data.gameVersion != getMindustryVersion()) {
            sender.error("[accent]Server is not running the same version of Mindustry.")
            return
        }
        switch(sender.player, data)
    }

    @ImperiumCommand(["hub"])
    @ClientSide
    fun onHubCommand(sender: CommandSender) {
        onSwitchCommand(sender, "hub")
    }
}

@Suppress("FunctionName")
private fun SwitchWindowManager(plugin: MindustryPlugin, discovery: Discovery): WindowManager {
    val manager = MenuManager.create(plugin)
    manager.addTransformer(
        ListTransformer<Discovery.Data.Mindustry>()
            .setProvider {
                discovery.servers.values
                    .asSequence()
                    .map(Discovery.Server::data)
                    .filterIsInstance(Discovery.Data.Mindustry::class.java)
                    .filter {
                        it.state != Discovery.Data.Mindustry.State.STOPPED && it.gameVersion == getMindustryVersion()
                    }
                    .sortedBy(Discovery.Data.Mindustry::name)
                    .toList()
            }
            .setRenderer { element -> Distributor.get().mindustryComponentDecoder.decode(element.name) }
            .setHeight(20)
            .setChoiceAction(
                BiAction.compose(
                    BiAction.from(Window::hide),
                    BiAction { window, server -> switch(window.viewer, server) },
                )
            )
    )
    return manager
}

private fun switch(player: Player, server: Discovery.Data.Mindustry) {
    Call.connect(player.con, server.host.hostAddress, server.port)
    Call.sendMessage("[accent]${player.plainName()}[] switched to the [cyan]${server.name}[accent].")
}
