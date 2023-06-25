/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.listener

import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.misc.capitalize
import com.xpdustry.foundation.common.misc.toHexString
import fr.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import fr.xpdustry.distributor.api.scheduler.TaskHandler
import jakarta.inject.Inject
import mindustry.net.Administration

// TODO: Turn into ConventionService
class ConventionListener @Inject constructor(private val config: FoundationConfig) : FoundationListener {

    override fun onFoundationInit() {
        Administration.Config.serverName.set(
            "[accent]<[white]CN[]> [${config.mindustry.color.toHexString()}]${config.mindustry.serverName.capitalize(all = true)}",
        )
    }

    @TaskHandler(interval = 1L, unit = MindustryTimeUnit.MINUTES)
    fun onQuoteUpdate() {
        Administration.Config.desc.set("\"${config.mindustry.quotes.random()}\"")
    }
}
