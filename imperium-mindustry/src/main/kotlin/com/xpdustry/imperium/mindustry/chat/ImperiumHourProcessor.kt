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
package com.xpdustry.imperium.mindustry.chat

import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.flex.placeholder.PlaceholderContext
import com.xpdustry.flex.processor.Processor
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.onEvent
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import mindustry.game.EventType

class ImperiumHourProcessor(plugin: MindustryPlugin, private val accounts: AccountManager) :
    Processor<PlaceholderContext, String?> {

    private val hours = PlayerMap<Int>(plugin)

    init {
        onEvent<EventType.PlayerJoin> { event ->
            ImperiumScope.MAIN.launch {
                val playtime = accounts.selectBySession(event.player.sessionKey)?.playtime
                if (playtime != null) {
                    runMindustryThread { hours[event.player] = playtime.inWholeHours.toInt() }
                }
            }
        }
    }

    override fun process(context: PlaceholderContext): String? =
        when (val audience = context.subject) {
            is DiscordAudience -> CHAOTIC_HOUR_FORMAT.format(audience.hours)
            is PlayerAudience -> hours[audience.player]?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
            else -> null
        }

    companion object {
        val CHAOTIC_HOUR_FORMAT = DecimalFormat("000")
    }
}
