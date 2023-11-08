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

import arc.util.Log
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import java.time.Instant
import kotlin.time.Duration
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.NetConnection

val Player.identity: Identity.Mindustry
    get() = Identity.Mindustry(info.plainLastName(), uuid(), usid(), con.address.toInetAddress())

val Player.joinTime: Instant
    get() = Instant.ofEpochMilli(con.connectTime)

fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)

fun NetConnection.kick(reason: String, duration: Duration, silent: Boolean = false) {
    if (kicked) return

    if (!silent) {
        Log.info("Kicking connection @ / @; Reason: @", address, uuid, reason.replace("\n", " "))
    }

    if (duration.isPositive()) {
        Vars.netServer.admins.handleKicked(uuid, address, duration.inWholeMilliseconds)
    }

    Call.kick(this, reason)
    // STEAM: Will break if the connection closes now
    close()

    Vars.netServer.admins.save()
    kicked = true
}
