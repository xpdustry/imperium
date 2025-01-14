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

import arc.graphics.Color
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.flex.placeholder.PlaceholderContext
import com.xpdustry.flex.processor.Processor
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.toHexString
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.graphics.Pal

class ImperiumPlaceholderProcessor(plugin: MindustryPlugin, private val accounts: AccountManager) :
    Processor<PlaceholderContext, String?> {

    private val ranks = PlayerMap<Rank>(plugin)
    private val hours = PlayerMap<Int>(plugin)

    init {
        ImperiumScope.MAIN.launch {
            while (isActive) {
                delay(10.seconds)
                Entities.getPlayersAsync().forEach { player ->
                    val account = accounts.selectBySession(player.sessionKey)
                    runMindustryThread {
                        if (account == null) {
                            hours.remove(player)
                            ranks.remove(player)
                        } else {
                            hours[player] = account.playtime.inWholeHours.toInt()
                            ranks[player] = account.rank
                        }
                    }
                }
            }
        }
    }

    override fun process(context: PlaceholderContext): String? =
        when (context.query.lowercase()) {
            "hours" ->
                when (val audience = context.subject) {
                    is DiscordAudience -> audience.hours?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
                    is PlayerAudience -> hours[audience.player]?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
                    else -> ""
                }
            "is_discord" ->
                when (context.subject) {
                    is DiscordAudience -> "discord"
                    else -> ""
                }
            "rank_color" ->
                when (val audience = context.subject) {
                    is DiscordAudience -> audience.rank.toColor().toHexString()
                    is PlayerAudience -> (ranks[audience.player] ?: Rank.EVERYONE).toColor().toHexString()
                    else -> Rank.EVERYONE.toColor().toHexString()
                }
            else -> null
        }

    private fun Rank.toColor() =
        when (this) {
            Rank.EVERYONE -> Color.gray
            Rank.VERIFIED -> Pal.accent
            Rank.OVERSEER -> Color.green
            Rank.MODERATOR -> Color.blue
            Rank.ADMIN,
            Rank.OWNER -> Color.scarlet
        }

    companion object {
        val CHAOTIC_HOUR_FORMAT = DecimalFormat("000")
    }
}
