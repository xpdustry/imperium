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

import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.Command
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.security.AddressWhitelist
import com.xpdustry.imperium.discord.command.InteractionSender

class WhitelistCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val whitelist = instances.get<AddressWhitelist>()

    @Command(["whitelist", "add"], Rank.ADMIN)
    private suspend fun onWhitelistAddCommand(sender: InteractionSender, address: String) {
        // TODO Add InetAddress parser, with option to prevent the use of loopback addresses
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            sender.respond("The ip address is not valid.")
            return
        }
        if (whitelist.containsAddress(ip)) {
            sender.respond("The whitelist already contains this address.")
        } else {
            whitelist.addAddress(ip)
            sender.respond("Added address to whitelist.")
        }
    }

    @Command(["whitelist", "remove"], Rank.ADMIN)
    private suspend fun onWhitelistRemoveCommand(sender: InteractionSender, address: String) {
        val ip = address.toInetAddressOrNull()
        if (ip == null) {
            sender.respond("The ip address is not valid.")
            return
        }
        if (whitelist.containsAddress(ip)) {
            whitelist.removeAddress(ip)
            sender.respond("Removed address from whitelist.")
        } else {
            sender.respond("The whitelist does not contain this address.")
        }
    }
}
