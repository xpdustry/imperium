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
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.imperium.common.bridge.PlayerInfo
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.service.PlayerIdentity
import mindustry.gen.Call
import mindustry.gen.Player

val Player.playerInfo get() = PlayerInfo(name, uuid(), ip().toInetAddress())

val Player.identity: PlayerIdentity get() = PlayerIdentity(uuid(), usid(), con.address.toInetAddress())

fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)
