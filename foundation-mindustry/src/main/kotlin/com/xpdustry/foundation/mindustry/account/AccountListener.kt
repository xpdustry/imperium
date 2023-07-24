/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.mindustry.account

import com.google.inject.Inject
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.database.model.AccountService
import com.xpdustry.foundation.common.database.model.PlayerIdentity
import com.xpdustry.foundation.common.misc.toInetAddress
import com.xpdustry.foundation.mindustry.verification.VerificationPipeline
import com.xpdustry.foundation.mindustry.verification.VerificationResult
import fr.xpdustry.distributor.api.util.Priority
import mindustry.gen.Player
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AccountListener::class.java)

class AccountListener @Inject constructor(
    private val service: AccountService,
    private val pipeline: VerificationPipeline,
) : FoundationListener {
    override fun onFoundationInit() {
        // Small hack to make sure a player session is refreshed when it joins the server,
        // instead of blocking the process in a PlayerConnectionConfirmed event listener
        pipeline.register("account", Priority.LOWEST) {
            service.refresh(PlayerIdentity(it.uuid, it.usid, it.address)).thenReturn(VerificationResult.Success)
        }
    }
}

val Player.identity: PlayerIdentity get() = PlayerIdentity(uuid(), usid(), con.address.toInetAddress())
