// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.account

import mindustry.gen.Player

data class PlayerLoginEvent(val player: Player)

data class PlayerLogoutEvent(val player: Player)
