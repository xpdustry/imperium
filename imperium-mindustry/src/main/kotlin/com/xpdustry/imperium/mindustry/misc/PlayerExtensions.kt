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

import com.google.common.primitives.Longs
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.account.SessionKey
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import java.time.Instant
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Player

val Player.identity: Identity.Mindustry
    get() = Identity.Mindustry(info.plainLastName(), uuid(), usid(), con.address.toInetAddress(), info.lastName)

@OptIn(ExperimentalEncodingApi::class)
val Player.sessionKey: SessionKey
    get() =
        SessionKey(
            Longs.fromByteArray(Base64.decode(uuid())),
            Longs.fromByteArray(Base64.decode(usid())),
            con.address.toInetAddress(),
        )

val Player.joinTime: Instant
    get() = Instant.ofEpochMilli(con.connectTime)

val Player.javaLocale: Locale
    get() = Locale.forLanguageTag(locale().replace('_', '-'))

val Player.asAudience: Audience
    get() = Distributor.get().audienceProvider.getPlayer(this)

fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)

suspend fun Player.tryGrantAdmin(manager: AccountManager) {
    admin = ((manager.selectBySession(sessionKey)?.rank ?: Rank.EVERYONE) >= Rank.OVERSEER) || admin
}

fun Player.reloadWorldData() {
    info.lastSyncTime = System.currentTimeMillis()
    Call.worldDataBegin(con)
    Vars.netServer.sendWorldData(this)
}
