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
package com.xpdustry.imperium.mindustry.misc

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.NetConnection
import mindustry.net.Packets.KickReason
import org.slf4j.event.Level

val Player.identity: Identity.Mindustry
    get() =
        Identity.Mindustry(
            info.plainLastName(), uuid(), usid(), con.address.toInetAddress(), info.lastName)

val Player.joinTime: Instant
    get() = Instant.ofEpochMilli(con.connectTime)

val Player.javaLocale: Locale
    get() = Locale.forLanguageTag(locale().replace('_', '-'))

fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)

fun NetConnection.kick(reason: KickReason, silent: Boolean = false) {
    val duration =
        if (reason == KickReason.kick || reason == KickReason.banned || reason == KickReason.vote)
            30.minutes
        else Duration.ZERO
    kick(reason.name, reason, duration, silent)
}

fun NetConnection.kick(reason: String, duration: Duration, silent: Boolean = false) {
    kick(reason, null, duration, silent)
}

private fun NetConnection.kick(
    reason: String,
    kick: KickReason?,
    duration: Duration,
    silent: Boolean
) {
    if (kicked) return

    logger
        .atLevel(if (silent) Level.DEBUG else Level.INFO)
        .log("Kicking connection {} / {}; Reason: {}", address, uuid, reason.replace("\n", " "))

    if (duration.isPositive()) {
        Vars.netServer.admins.handleKicked(uuid, address, duration.inWholeMilliseconds)
    }

    if (kick == null) {
        Call.kick(this, reason)
    } else {
        Call.kick(this, kick)
    }

    // STEAM: Will break if the connection closes now
    close()

    Vars.netServer.admins.save()
    kicked = true
}

suspend fun Player.tryGrantAdmin(manager: AccountManager) {
    admin = ((manager.findByIdentity(identity)?.rank ?: Rank.EVERYONE) >= Rank.OVERSEER) || admin
}

fun Player.reloadWorldData() {
    info.lastSyncTime = System.currentTimeMillis()
    Call.worldDataBegin(con)
    Vars.netServer.sendWorldData(this)
}

private val logger = logger("ROOT")
