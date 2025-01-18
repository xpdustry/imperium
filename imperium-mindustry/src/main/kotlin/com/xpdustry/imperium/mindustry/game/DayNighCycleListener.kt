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

import arc.math.Interp
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.misc.dayNightCycle
import mindustry.Vars
import mindustry.gen.Call

class DayNighCycleListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val cycle = instances.get<ImperiumConfig>().mindustry.world.dayNightCycleDuration.inWholeSeconds

    @EventHandler
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        if (!Vars.state.rules.lighting) {
            Vars.state.map.dayNightCycle = true
            Vars.state.rules.lighting = true
        }
    }

    @TaskHandler(interval = 1, unit = MindustryTimeUnit.SECONDS)
    fun onSolarCycleUpdate() {
        if (!Vars.state.isGame || !Vars.state.rules.lighting || !Vars.state.map.dayNightCycle) return
        val time = ((System.currentTimeMillis() / 1000L) % cycle) / (cycle * 0.5F)
        Vars.state.rules.ambientLight.a = Interp.sine.apply(0F, 0.8F, time)
        Call.setRules(Vars.state.rules)
    }
}
