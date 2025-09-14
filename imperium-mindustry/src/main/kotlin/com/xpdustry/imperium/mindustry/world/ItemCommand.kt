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
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import kotlin.math.absoluteValue
import mindustry.Vars
import mindustry.game.Team
import mindustry.type.Item
import mindustry.type.ItemSeq
import org.incendo.cloud.annotation.specifier.Quoted

class ItemCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["fillitems"], Rank.ADMIN)
    @ClientSide
    fun onItemCommand(sender: CommandSender, team: Team = sender.player.team(), @Quoted items: String) {
        // Trim trailing commas and whitespace
        val trimmedItems = items.trim().removeSuffix(",")
        if (trimmedItems.isEmpty()) {
            sender.reply("No items provided.")
            return
        }

        val parts = trimmedItems.split(",").map { it.trim() }

        // Each item must have a corresponding amount
        if (parts.size % 2 != 0) {
            sender.reply("Each item must have an amount. EG: \"copper, 1, lead, 2\"")
            return
        }

        val itemArray =
            parts
                .chunked(2)
                .flatMap { (itemName, amountString) ->
                    // Find the item by name, case-insensitively
                    val item =
                        Vars.content.items().find { it.name.equals(itemName, ignoreCase = true) }
                            ?: return sender.reply("Invalid item name: $itemName")

                    // Parse the amount, ensuring it's a valid integer
                    val amount =
                        amountString.toIntOrNull() ?: return sender.reply("Invalid amount for $itemName: $amountString")

                    listOf(item, amount)
                }
                .toTypedArray()

        val core = team.core()
        if (core == null) {
            sender.reply("Could not find a core.")
            return
        }

        val itemSeq =
            ItemSeq().apply {
                for (i in itemArray.indices step 2) {
                    add(itemArray[i] as Item, itemArray[i + 1] as Int)
                }
            }

        core.items.add(itemSeq)
        val formattedItems =
            itemSeq.joinToString(", ") { itemStack -> "${itemStack.item.localizedName}: ${itemStack.amount}" }
        sender.reply("Added items to core:\n$formattedItems")
    }

    @ImperiumCommand(["item"], Rank.ADMIN)
    @ClientSide
    fun onItemCommand(
        sender: CommandSender,
        @Flag("i") item: Item,
        @Flag("a") amount: Int = 1,
        @Flag("t") team: Team = sender.player.team(),
    ) {
        val core = team.core()
        if (core == null) {
            sender.reply("Could not find a core.")
            return
        }
        if (amount >= 0) core.items.add(item, amount) else core.items.remove(item, amount)
        sender.reply(
            "${if(amount >= 0) "Added" else "Removed"}} ${amount.absoluteValue} ${item.localizedName} to core."
        )
    }
}
