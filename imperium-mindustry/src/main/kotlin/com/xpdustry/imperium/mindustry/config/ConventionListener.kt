/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.mindustry.config

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toHexString
import fr.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import fr.xpdustry.distributor.api.scheduler.TaskHandler
import mindustry.net.Administration

class ConventionListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val config = instances.get<ServerConfig.Mindustry>()

    override fun onImperiumInit() {
        Administration.Config.serverName.set(
            "[accent]<[white]CN[]> [${config.color.toHexString()}]${config.displayName}",
        )
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.MINUTES)
    fun onQuoteUpdate() {
        Administration.Config.desc.set("\"${config.quotes.random()}\"")
    }
}
