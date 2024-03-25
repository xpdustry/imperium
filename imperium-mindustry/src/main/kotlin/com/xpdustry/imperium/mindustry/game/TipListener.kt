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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.javaLocale
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import fr.xpdustry.distributor.api.DistributorProvider
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars

private typealias TipWithDetails = Pair<String, String>

class TipListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val config = instances.get<ServerConfig.Mindustry>()
    private val language = instances.get<ImperiumConfig>().language
    private var index = 0
    private lateinit var tips: List<String>

    override fun onImperiumInit() {
        LOGGER.debug("Loading tips: {}", config.tips)
        tips = config.tips.filter { getLocalizedTip(it, language) != null }.shuffled()
        val failures = config.tips - tips.toSet()

        if (failures.isNotEmpty()) {
            LOGGER.warn("Failed to load tips: {}", failures)
        }

        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(config.tipsDelay)
                runMindustryThread { showNextTip() }
            }
        }
    }

    private fun showNextTip() {
        if (!Vars.state.isPlaying || tips.isEmpty()) {
            return
        }

        index = (index + 1) % tips.size
        Entities.getPlayers().forEach {
            it.sendMessage(
                buildString {
                    val (content, details) = getLocalizedTip(tips[index], it.javaLocale)!!
                    append("[cyan]>>> [accent]Tip: ")
                    append(content)
                    append("\n[lightgray]")
                    append(details)
                })
        }
    }

    private fun getLocalizedTip(key: String, locale: Locale): TipWithDetails? {
        val translator = DistributorProvider.get().globalLocalizationSource
        val content = translator.localize("imperium.tip.$key.content", locale) ?: return null
        return content.format(NO_ARGS) to translator.format("imperium.tip.$key.details", locale)
    }

    companion object {
        private val LOGGER = logger<TipListener>()
        private val NO_ARGS = emptyArray<Any>()
    }
}
