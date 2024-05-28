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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.mindustry.translation.announcement_power_void_destroyed
import mindustry.Vars
import mindustry.game.EventType
import mindustry.world.blocks.ConstructBlock.ConstructBuild
import mindustry.world.blocks.sandbox.PowerVoid

class AlertListener : ImperiumApplication.Listener {

    @EventHandler
    fun onBlockDestroy(event: EventType.BlockDestroyEvent) {
        if (event.tile.block() is PowerVoid && !Vars.state.rules.infiniteResources) {
            notifyPowerVoidDestroyed(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }

    @EventHandler
    fun onBlockDelete(event: EventType.BlockBuildBeginEvent) {
        val building = event.tile.build
        if (event.breaking &&
            building is ConstructBuild &&
            building.current is PowerVoid &&
            !Vars.state.rules.infiniteResources) {
            notifyPowerVoidDestroyed(event.tile.x.toInt(), event.tile.y.toInt())
        }
    }

    private fun notifyPowerVoidDestroyed(x: Int, y: Int) {
        DistributorProvider.get()
            .audienceProvider
            .players
            .sendMessage(announcement_power_void_destroyed(x, y))
    }
}
