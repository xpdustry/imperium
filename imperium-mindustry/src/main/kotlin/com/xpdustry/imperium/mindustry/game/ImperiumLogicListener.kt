// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.dependency.Inject
import mindustry.core.GameState
import mindustry.game.EventType.StateChangeEvent

@Inject
class ImperiumLogicListener constructor(private val plugin: MindustryPlugin) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        Distributor.get().eventBus.subscribe(StateChangeEvent::class.java, Priority.HIGH, plugin) { event ->
            if (event.from == GameState.State.menu && event.to == GameState.State.playing) {
                Distributor.get().eventBus.post(MenuToPlayEvent)
            }
        }
    }
}
