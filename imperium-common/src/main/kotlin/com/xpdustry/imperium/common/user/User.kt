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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.misc.MindustryUUID
import java.net.InetAddress
import java.time.Instant

data class User(
    val id: Int,
    val uuid: MindustryUUID,
    val lastName: String,
    val lastAddress: InetAddress,
    val timesJoined: Int = 0,
    val lastJoin: Instant,
    val firstJoin: Instant,
) {
    data class NamesAndAddresses(val names: Set<String>, val addresses: Set<InetAddress>)

    enum class Setting(val default: Boolean, val deprecated: Boolean = false, val achievement: Achievement? = null) {
        SHOW_WELCOME_MESSAGE(true),
        RESOURCE_HUD(true),
        REMEMBER_LOGIN(true),
        DOUBLE_TAP_TILE_LOG(true),
        ANTI_BAN_EVADE(false),
        CHAT_TRANSLATOR(true, deprecated = true),
        AUTOMATIC_LANGUAGE_DETECTION(true),
        UNDERCOVER(false),
        RAINBOW_NAME(false, achievement = Achievement.SUPPORTER),
    }
}
