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
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.application.ImperiumPlatform
import com.xpdustry.imperium.common.message.Message

data class BridgeMessage(val serverName: String, val origin: ImperiumPlatform, val payload: Payload) : Message {
    sealed interface Payload {
        data class PlayerJoin(val playerName: String) : Payload
        data class PlayerQuit(val playerName: String) : Payload
        data class PlayerChat(val playerName: String, val message: String) : Payload
        data class System(val message: String) : Payload
    }
}
