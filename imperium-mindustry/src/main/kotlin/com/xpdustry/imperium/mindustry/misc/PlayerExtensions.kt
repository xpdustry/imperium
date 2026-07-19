// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.misc

import com.google.common.primitives.Longs
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.imperium.common.account.SessionKey
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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

val Player.asAudience: Audience
    get() = Distributor.get().audienceProvider.getPlayer(this)

fun Player.showInfoMessage(message: String) = Call.infoMessage(con, message)
