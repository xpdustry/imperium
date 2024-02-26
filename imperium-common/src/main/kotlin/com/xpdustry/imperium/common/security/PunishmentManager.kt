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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import com.xpdustry.imperium.common.user.UserAddressTable
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.common.user.UserTable
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

interface PunishmentManager {

    suspend fun findBySnowflake(snowflake: Snowflake): Punishment?

    suspend fun findAllByIdentity(identity: Identity.Mindustry): List<Punishment>

    suspend fun findAllByUser(snowflake: Snowflake): List<Punishment>

    suspend fun punish(
        author: Identity,
        user: Snowflake,
        reason: String,
        type: Punishment.Type,
        duration: Duration,
        metadata: Punishment.Metadata = Punishment.Metadata.None
    ): Snowflake

    suspend fun pardon(author: Identity, punishment: Snowflake, reason: String): PardonResult
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
    val server: String,
    val metadata: Punishment.Metadata
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
    private val users: UserManager,
    private val config: ImperiumConfig
) : PunishmentManager, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(PunishmentTable) }
    }

    override suspend fun findBySnowflake(snowflake: Snowflake): Punishment? =
        provider.newSuspendTransaction {
            PunishmentTable.selectAll()
                .where { PunishmentTable.id eq snowflake }
                .firstOrNull()
                ?.toPunishment()
        }

    override suspend fun findAllByIdentity(identity: Identity.Mindustry): List<Punishment> =
        provider.newSuspendTransaction {
            var query = Op.build { UserAddressTable.address eq identity.address.address }
            users.findByUuid(identity.uuid)?.snowflake?.let { snowflake ->
                query = query.or(Op.build { (PunishmentTable.target eq snowflake) })
            }
            PunishmentTable.join(
                    UserAddressTable,
                    JoinType.LEFT,
                    onColumn = PunishmentTable.target,
                    otherColumn = UserAddressTable.user)
                .selectAll()
                .where(query)
                .map { it.toPunishment() }
        }

    override suspend fun findAllByUser(snowflake: Snowflake): List<Punishment> =
        provider.newSuspendTransaction {
            (PunishmentTable leftJoin UserTable)
                .selectAll()
                .where { UserTable.id eq snowflake }
                .map { it.toPunishment() }
        }

    override suspend fun punish(
        author: Identity,
        user: Snowflake,
        reason: String,
        type: Punishment.Type,
        duration: Duration,
        metadata: Punishment.Metadata
    ): Snowflake {
        val snowflake = generator.generate()
        provider.newSuspendTransaction {
            PunishmentTable.insert {
                it[PunishmentTable.id] = snowflake
                it[target] = user
                it[PunishmentTable.reason] = reason
                it[PunishmentTable.type] = type
                it[PunishmentTable.duration] =
                    if (duration.isInfinite()) null else duration.toJavaDuration()
                it[server] = config.server.name
            }
        }
        messenger.publish(
            PunishmentMessage(
                author, PunishmentMessage.Type.CREATE, snowflake, config.server.name, metadata),
            local = true)
        return snowflake
    }

    override suspend fun pardon(
        author: Identity,
        punishment: Snowflake,
        reason: String
    ): PardonResult =
        provider
            .newSuspendTransaction {
                val data =
                    PunishmentTable.select(PunishmentTable.pardonTimestamp)
                        .where { PunishmentTable.id eq punishment }
                        .firstOrNull()
                        ?: return@newSuspendTransaction PardonResult.NOT_FOUND

                if (data[PunishmentTable.pardonTimestamp] != null) {
                    return@newSuspendTransaction PardonResult.ALREADY_PARDONED
                }

                PunishmentTable.update({ PunishmentTable.id eq punishment }) {
                    it[pardonTimestamp] = Instant.now()
                    it[pardonReason] = reason
                }

                return@newSuspendTransaction PardonResult.SUCCESS
            }
            .also {
                messenger.publish(
                    PunishmentMessage(
                        author,
                        PunishmentMessage.Type.PARDON,
                        punishment,
                        config.server.name,
                        Punishment.Metadata.None),
                    local = true)
            }

    private fun ResultRow.toPunishment(): Punishment {
        val pardon =
            if (this[PunishmentTable.pardonTimestamp] == null) {
                null
            } else {
                Punishment.Pardon(
                    this[PunishmentTable.pardonTimestamp]!!, this[PunishmentTable.pardonReason]!!)
            }
        return Punishment(
            snowflake = this[PunishmentTable.id].value,
            target = this[PunishmentTable.target].value,
            reason = this[PunishmentTable.reason],
            type = this[PunishmentTable.type],
            duration = this[PunishmentTable.duration]?.toKotlinDuration() ?: Duration.INFINITE,
            pardon = pardon,
            server = this[PunishmentTable.server])
    }
}
