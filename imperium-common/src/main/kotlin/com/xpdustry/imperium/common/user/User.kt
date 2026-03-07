// SPDX-License-Identifier: GPL-3.0-only
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
