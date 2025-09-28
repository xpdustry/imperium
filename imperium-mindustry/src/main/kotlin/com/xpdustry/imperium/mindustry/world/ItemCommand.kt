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
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.specifier.AllTeams
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import kotlin.math.absoluteValue
import mindustry.game.Team
import mindustry.type.Item

class ItemCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["item", "add|a"], Rank.ADMIN)
    fun onItemAddCommand(sender: CommandSender, item: Item, amount: Int, @AllTeams team: Team = sender.player.team()) {
        val core = team.core()
        if (core == null) {
            sender.reply("Could not find a core for team ${team.localized()}.")
            return
        }

        if (amount == 0) {
            sender.reply("Amount must not be zero!")
        } else if (amount > 0) {
            core.items.add(item, amount)
        } else {
            core.items.remove(item, amount.absoluteValue)
        }

        sender.reply(
            "${if(amount >= 0) "Added" else "Removed"} ${amount.absoluteValue} ${item.localizedName} to ${team.localized()} core."
        )
    }
}
