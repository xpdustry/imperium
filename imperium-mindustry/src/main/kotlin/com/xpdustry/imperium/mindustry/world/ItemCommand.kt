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
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import kotlin.math.absoluteValue
import kotlin.math.min
import mindustry.Vars
import mindustry.game.Team
import mindustry.type.Item

// TODO Translate this shi
@Suppress("DuplicatedCode")
class ItemCommand : LifecycleListener {

    @ImperiumCommand(["item", "add|a"], Rank.ADMIN)
    @ClientSide
    @ServerSide
    fun onItemAddCommand(sender: CommandSender, item: Item, amount: Int, @AllTeams team: Team = sender.player.team()) {
        if (!Vars.state.isGame) {
            sender.error("Not playing. Host first.")
            return
        }

        val core = team.core()
        if (core == null) {
            sender.reply("Could not find a core for team ${team.localized()}.")
            return
        }

        val verb: String
        val value: Int
        if (amount == 0) {
            sender.reply("Amount must not be zero!")
            return
        } else if (amount > 0) {
            value = min(amount, core.storageCapacity - core.items.get(item))
            verb = "Added"
            core.items.add(item, value)
        } else {
            value = min(amount.absoluteValue, core.items.get(item))
            verb = "Removed"
            core.items.remove(item, value)
        }

        sender.reply("$verb ${value.absoluteValue} ${item.localizedName} to ${team.localized()} core.")
    }

    @ImperiumCommand(["item", "fill|f"], Rank.ADMIN)
    @ClientSide
    @ServerSide
    fun onItemFillCommand(sender: CommandSender, @AllTeams team: Team = sender.player.team()) {
        if (!Vars.state.isGame) {
            sender.error("Not playing. Host first.")
            return
        }

        val core = team.core()
        if (core == null) {
            sender.reply("Could not find a core for team ${team.localized()}.")
            return
        }

        for (item in Vars.content.items()) {
            core.items.set(item, core.storageCapacity)
        }

        sender.reply("Core filled.")
    }
}
