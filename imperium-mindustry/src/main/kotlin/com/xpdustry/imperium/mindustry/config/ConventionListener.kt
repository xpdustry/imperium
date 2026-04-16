// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.config

import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.toHexString
import mindustry.net.Administration

@Inject
class ConventionListener constructor(private val config: ImperiumConfig) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        Administration.Config.serverName.set(
            "[accent]<[white]CN[]> [${config.mindustry.color.toHexString()}]${config.server.displayName}"
        )
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.MINUTES)
    fun onQuoteUpdate() {
        Administration.Config.desc.set("\"${config.mindustry.quotes.random()}\"")
    }
}
