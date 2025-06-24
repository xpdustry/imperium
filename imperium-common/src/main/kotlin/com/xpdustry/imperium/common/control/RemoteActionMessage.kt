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
package com.xpdustry.imperium.common.control

import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
data class RemoteActionMessage(val target: String?, val action: Action, val immediate: Boolean) : Message {
    enum class Action {
        RESTART,
        EXIT,
    }
}

fun RemoteActionMessage.Action.toExitStatus() =
    when (this) {
        RemoteActionMessage.Action.EXIT -> ExitStatus.EXIT
        RemoteActionMessage.Action.RESTART -> ExitStatus.RESTART
    }
