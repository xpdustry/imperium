// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
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

@Inject
class TipListener constructor(private val config: ImperiumConfig) : ImperiumApplication.Listener {
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
