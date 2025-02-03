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
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.flex.placeholder.PlaceholderContext
import com.xpdustry.flex.processor.Processor
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
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

class ImperiumPlaceholderProcessor(
    plugin: MindustryPlugin,
    private val accounts: AccountManager,
    private val users: UserManager,
) : Processor<PlaceholderContext, String?> {
    private val ranks = PlayerMap<Rank>(plugin)
    private val hours = PlayerMap<Int>(plugin)
    private val hidden = PlayerMap<Boolean>(plugin)
    private val rainbow = PlayerMap<Boolean>(plugin)

    init {
        ImperiumScope.MAIN.launch {
            while (isActive) {
                // TODO That block of code makes me cry, polling is cringe, reactive is king
                delay(2.seconds)
                Entities.getPlayersAsync().forEach { player ->
                    val account = accounts.selectBySession(player.sessionKey)
                    val undercover = users.getSetting(player.uuid(), User.Setting.UNDERCOVER)
                    val rainbow =
                        users.getSetting(player.uuid(), User.Setting.RAINBOW_NAME) &&
                            account != null &&
                            accounts.selectAchievement(account.id, Achievement.SUPPORTER)
                    runMindustryThread {
                        hidden[player] = undercover
                        this@ImperiumPlaceholderProcessor.rainbow[player] = rainbow
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
                    is PlayerAudience -> {
                        if (hidden[audience.player] == true) ""
                        else hours[audience.player]?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
                    }
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
                    is PlayerAudience -> {
                        if (hidden[audience.player] == true) Rank.EVERYONE.toColor().toHexString()
                        else (ranks[audience.player] ?: Rank.EVERYONE).toColor().toHexString()
                    }
                    else -> Rank.EVERYONE.toColor().toHexString()
                }
            "name_colored" -> {
                val audience = context.subject
                if (audience is PlayerAudience && rainbow[audience.player] == true && hidden[audience.player] != true) {
                    context.subject.metadata[StandardKeys.DECORATED_NAME]?.let {
                        buildString {
                            val plain = ComponentStringBuilder.plain(context.subject.metadata).append(it).toString()
                            val initial = (((System.currentTimeMillis() / 1000L) % 60) / 60F) * 360F
                            val color = Color().a(1F)
                            for ((index, char) in plain.withIndex()) {
                                color.fromHsv(initial + (index * 8F), 0.55F, 0.9F)
                                append("[#")
                                append(color)
                                append(']')
                                append(char)
                            }
                        }
                    }
                } else {
                    context.subject.metadata[StandardKeys.NAME]?.let {
                        ComponentStringBuilder.mindustry(context.subject.metadata).append(it).toString()
                    }
                }
            }
            else -> null
        }

    private fun Rank.toColor() =
        when (this) {
            Rank.EVERYONE -> Color.lightGray
            Rank.VERIFIED -> Pal.accent
            Rank.OVERSEER -> Color.green
            Rank.MODERATOR -> Color.royal
            Rank.ADMIN,
            Rank.OWNER -> Color.scarlet
        }

    companion object {
        val CHAOTIC_HOUR_FORMAT = DecimalFormat("000")
    }
}
