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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toShortMuuid
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import java.net.InetAddress
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

interface PunishmentManager {

    suspend fun findBySnowflake(snowflake: Snowflake): Punishment?

    suspend fun findAllByAddress(target: InetAddress): Flow<Punishment>

    suspend fun findAllByUuid(uuid: MindustryUUID): Flow<Punishment>

    suspend fun punish(
        author: Identity,
        target: Punishment.Target,
        reason: String,
        type: Punishment.Type,
        duration: Duration
    )

    suspend fun pardon(author: Identity, snowflake: Snowflake, reason: String): PardonResult
}

enum class PardonResult {
    SUCCESS,
    NOT_FOUND,
    ALREADY_PARDONED
}

@Serializable
data class PunishmentMessage(
    val author: Identity,
    val type: Type,
    val snowflake: Snowflake,
) : Message {
    enum class Type {
        CREATE,
        PARDON
    }
}

class SimplePunishmentManager(
    private val provider: SQLProvider,
    private val generator: SnowflakeGenerator,
    private val messenger: Messenger,
    private val account: AccountManager,
) : PunishmentManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(PunishmentTable) }
    }

    override suspend fun findBySnowflake(snowflake: Snowflake): Punishment? =
        provider.newSuspendTransaction {
            PunishmentTable.select { PunishmentTable.id eq snowflake }.firstOrNull()?.toPunishment()
        }

    override suspend fun findAllByAddress(target: InetAddress): Flow<Punishment> =
        provider.newSuspendTransaction {
            PunishmentTable.select { PunishmentTable.targetAddress eq target.address }
                .map { it.toPunishment() }
                .asFlow()
        }

    override suspend fun findAllByUuid(uuid: MindustryUUID): Flow<Punishment> =
        provider.newSuspendTransaction {
            PunishmentTable.select { PunishmentTable.targetUuid eq uuid.toShortMuuid() }
                .map { it.toPunishment() }
                .asFlow()
        }

    override suspend fun punish(
        author: Identity,
        target: Punishment.Target,
        reason: String,
        type: Punishment.Type,
        duration: Duration
    ): Unit =
        provider.newSuspendTransaction {
            val snowflake = generator.generate()
            val punishmentAuthor = author.toAuthor()
            PunishmentTable.insert {
                it[PunishmentTable.id] = snowflake
                it[authorId] = punishmentAuthor.identifier
                it[authorType] = punishmentAuthor.type
                it[targetAddress] = target.address.address
                it[targetAddressMask] = target.mask
                it[targetUuid] = target.uuid?.toShortMuuid()
                it[PunishmentTable.reason] = reason
                it[PunishmentTable.type] = type
                it[PunishmentTable.duration] =
                    if (duration.isInfinite()) null else duration.toJavaDuration()
            }
            messenger.publish(
                PunishmentMessage(author, PunishmentMessage.Type.CREATE, snowflake), local = true)
        }

    override suspend fun pardon(
        author: Identity,
        snowflake: Snowflake,
        reason: String
    ): PardonResult =
        provider.newSuspendTransaction {
            val punishment =
                PunishmentTable.slice(PunishmentTable.pardonTimestamp)
                    .select { PunishmentTable.id eq snowflake }
                    .firstOrNull()
                    ?: return@newSuspendTransaction PardonResult.NOT_FOUND

            if (punishment[PunishmentTable.pardonTimestamp] != null) {
                return@newSuspendTransaction PardonResult.ALREADY_PARDONED
            }

            val punishmentAuthor = author.toAuthor()
            PunishmentTable.update({ PunishmentTable.id eq snowflake }) {
                it[pardonTimestamp] = Instant.now()
                it[pardonReason] = reason
                it[pardonAuthorId] = punishmentAuthor.identifier
                it[pardonAuthorType] = punishmentAuthor.type
            }

            messenger.publish(
                PunishmentMessage(author, PunishmentMessage.Type.PARDON, snowflake), local = true)

            return@newSuspendTransaction PardonResult.SUCCESS
        }

    private suspend fun Identity.toAuthor(): Punishment.Author =
        when (this) {
            is Identity.Server -> Punishment.Author(0L, Punishment.Author.Type.CONSOLE)
            is Identity.Discord -> {
                val account = account.findByDiscord(id)
                if (account == null) {
                    Punishment.Author(0L, Punishment.Author.Type.DISCORD)
                } else {
                    Punishment.Author(account.snowflake, Punishment.Author.Type.ACCOUNT)
                }
            }
            is Identity.Mindustry -> {
                val account = account.findByIdentity(this)
                if (account == null) {
                    Punishment.Author(0L, Punishment.Author.Type.USER)
                } else {
                    Punishment.Author(account.snowflake, Punishment.Author.Type.ACCOUNT)
                }
            }
        }

    private fun ResultRow.toPunishment(): Punishment {
        val target =
            Punishment.Target(
                InetAddress.getByAddress(this[PunishmentTable.targetAddress]),
                this[PunishmentTable.targetUuid]?.toCRC32Muuid(),
                this[PunishmentTable.targetAddressMask])

        val author =
            Punishment.Author(
                this[PunishmentTable.authorId].value, this[PunishmentTable.authorType])

        val pardon =
            if (this[PunishmentTable.pardonTimestamp] == null) {
                null
            } else {
                Punishment.Pardon(
                    this[PunishmentTable.pardonTimestamp]!!,
                    this[PunishmentTable.pardonReason]!!,
                    Punishment.Author(
                        this[PunishmentTable.pardonAuthorId]!!,
                        this[PunishmentTable.pardonAuthorType]!!))
            }

        return Punishment(
            snowflake = this[PunishmentTable.id].value,
            author = author,
            target = target,
            reason = this[PunishmentTable.reason],
            type = this[PunishmentTable.type],
            duration = this[PunishmentTable.duration]?.toKotlinDuration() ?: Duration.INFINITE,
            pardon = pardon)
    }
}
