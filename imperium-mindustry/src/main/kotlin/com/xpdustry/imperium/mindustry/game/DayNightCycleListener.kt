// SPDX-License-Identifier: GPL-3.0-only
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

class DayNightCycleListener(instances: InstanceManager) : ImperiumApplication.Listener {

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
