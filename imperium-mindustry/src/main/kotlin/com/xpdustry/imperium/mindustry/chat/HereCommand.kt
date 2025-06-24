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

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import mindustry.gen.Call

class HereCommand : LifecycleListener {

    @ImperiumCommand(["here"])
    @ClientSide
    fun onHereCommand(sender: CommandSender) {
        Call.sendMessage(
            "${sender.player.coloredName()}[white] is at (${sender.player.tileX()}, ${sender.player.tileY()})."
        )
    }
}
