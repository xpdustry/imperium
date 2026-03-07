// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.content

enum class MindustryGamemode(val pvp: Boolean = false) {
    SURVIVAL,
    ATTACK,
    PVP(pvp = true),
    SANDBOX,
    ROUTER,
    SURVIVAL_EXPERT,
    HEXED(pvp = true),
    TOWER_DEFENSE,
    HUB,
    TESTING,
}
