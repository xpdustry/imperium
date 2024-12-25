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

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.announcement_tip
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars

enum class Tip {
    DISCORD,
    RULES,
    EXCAVATE,
    HELP,
    REPORT,
}

class TipListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ImperiumConfig>()
    private var index = 0
    private val tips = Tip.entries.shuffled()

    override fun onImperiumInit() {
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(config.mindustry.tipsDelay)
                runMindustryThread { showNextTip() }
            }
        }
    }

    private fun showNextTip() {
        if (Vars.state.isPlaying && tips.isNotEmpty()) {
            index = (index + 1) % tips.size
            Distributor.get().audienceProvider.players.sendMessage(announcement_tip(tips[index]))
        }
    }
}
