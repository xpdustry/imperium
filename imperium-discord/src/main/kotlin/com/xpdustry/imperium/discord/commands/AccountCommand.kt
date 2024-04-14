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
package com.xpdustry.imperium.discord.commands

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.ImperiumPermission
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.discord.command.InteractionSender

class AccountCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val accounts = instances.get<AccountManager>()

    @ImperiumCommand(["account", "rank", "set"])
    @ImperiumPermission(Rank.OWNER)
    private suspend fun onAccountRankSet(
        sender: InteractionSender.Slash,
        target: String,
        rank: Rank
    ) {
        if (rank == Rank.OWNER) {
            sender.respond("Nuh huh")
            return
        }
        if (target.toLongOrNull() == null) {
            sender.respond("Invalid target.")
            return
        }
        val snowflake =
            if (accounts.existsBySnowflake(target.toLong())) {
                target.toLong()
            } else {
                accounts.findByDiscord(target.toLong())?.snowflake
            }

        if (snowflake == null) {
            sender.respond("Account not found.")
            return
        }

        accounts.setRank(snowflake, rank)
        sender.respond("Set rank to $rank.")
    }
}
