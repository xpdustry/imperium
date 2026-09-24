// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.exists
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toLongMuuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

interface PlayerWhitelist {
    suspend fun addPlayer(uuid: MindustryUUID, reason: String)

    suspend fun containsPlayer(uuid: MindustryUUID): Boolean

    suspend fun removePlayer(uuid: MindustryUUID)

    suspend fun listWhitelist(): List<WhitelistWithReason>
}

typealias WhitelistWithReason = Pair<MindustryUUID, String>

@Inject
class SimplePlayerWhitelist(private val provider: SQLProvider) : PlayerWhitelist, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(WhitelistTable) }
    }

    override suspend fun addPlayer(uuid: MindustryUUID, reason: String): Unit = provider.newSuspendTransaction {
        WhitelistTable.upsert {
            it[WhitelistTable.uuid] = uuid.toLongMuuid()
            it[WhitelistTable.reason] = reason
        }
    }

    override suspend fun containsPlayer(uuid: MindustryUUID) = provider.newSuspendTransaction {
        WhitelistTable.exists { WhitelistTable.uuid eq uuid.toLongMuuid() }
    }

    override suspend fun removePlayer(uuid: MindustryUUID): Unit = provider.newSuspendTransaction {
        WhitelistTable.deleteWhere { WhitelistTable.uuid eq uuid.toLongMuuid() }
    }

    override suspend fun listWhitelist() = provider.newSuspendTransaction {
        WhitelistTable.select(WhitelistTable.uuid, WhitelistTable.reason).map {
            it[WhitelistTable.uuid].toCRC32Muuid() to it[WhitelistTable.reason]
        }
    }
}
