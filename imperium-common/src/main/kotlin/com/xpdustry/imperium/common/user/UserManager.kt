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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.buildAsyncCache
import com.xpdustry.imperium.common.misc.getSuspending
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toLongMuuid
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.snowflake.SnowflakeGenerator
import java.net.InetAddress
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

interface UserManager {

    suspend fun getByIdentity(identity: Identity.Mindustry): User

    suspend fun findBySnowflake(snowflake: Long): User?

    suspend fun findByUuid(uuid: MindustryUUID): User?

    suspend fun findByLastAddress(address: InetAddress): List<User>

    suspend fun findNamesAndAddressesBySnowflake(snowflake: Long): User.NamesAndAddresses

    suspend fun searchUserByName(query: String): List<User>

    suspend fun incrementJoins(identity: Identity.Mindustry)

    suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean

    suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean>

    suspend fun setSetting(identity: Identity.Mindustry, setting: User.Setting, value: Boolean)

    suspend fun setSettings(identity: Identity.Mindustry, settings: Map<User.Setting, Boolean>)
}

class SimpleUserManager(
    private val provider: SQLProvider,
    private val generator: SnowflakeGenerator,
    private val messenger: Messenger
) : UserManager, ImperiumApplication.Listener {
    private val userCreateMutex = Mutex()
    private val settingsCache =
        buildAsyncCache<MindustryUUID, Map<User.Setting, Boolean>>(
            expireAfterAccess = 5.minutes, maximumSize = 1000)

    override fun onImperiumInit() {
        provider.newTransaction {
            SchemaUtils.create(UserTable, UserNameTable, UserAddressTable, UserSettingTable)
        }

        messenger.consumer<UserSettingChangeMessage> { (uuid) -> invalidateSettings(uuid, false) }
    }

    override suspend fun getByIdentity(identity: Identity.Mindustry): User =
        userCreateMutex.withLock {
            provider.newSuspendTransaction {
                val user =
                    UserTable.selectAll()
                        .where { UserTable.uuid eq identity.uuid.toLongMuuid() }
                        .firstOrNull()
                        ?.toUser()
                if (user != null) {
                    return@newSuspendTransaction user
                }

                val snowflake = generator.generate()

                UserTable.insert {
                    it[id] = snowflake
                    it[uuid] = identity.uuid.toLongMuuid()
                    it[lastName] = identity.name.stripMindustryColors()
                    it[lastAddress] = identity.address.address
                }

                UserNameTable.insert {
                    it[UserNameTable.user] = snowflake
                    it[name] = identity.name.stripMindustryColors()
                }

                UserAddressTable.insert {
                    it[UserAddressTable.user] = snowflake
                    it[address] = identity.address.address
                }

                User(
                    snowflake = snowflake,
                    uuid = identity.uuid,
                    lastName = identity.name.stripMindustryColors(),
                    lastAddress = identity.address,
                    lastJoin = Instant.EPOCH)
            }
        }

    override suspend fun findBySnowflake(snowflake: Long): User? =
        provider.newSuspendTransaction {
            UserTable.selectAll().where { UserTable.id eq snowflake }.firstOrNull()?.toUser()
        }

    override suspend fun findByUuid(uuid: MindustryUUID): User? =
        provider.newSuspendTransaction {
            UserTable.selectAll()
                .where { UserTable.uuid eq uuid.toLongMuuid() }
                .firstOrNull()
                ?.toUser()
        }

    override suspend fun findByLastAddress(address: InetAddress): List<User> =
        provider.newSuspendTransaction {
            UserTable.selectAll()
                .where { UserTable.lastAddress eq address.address }
                .map { it.toUser() }
        }

    override suspend fun findNamesAndAddressesBySnowflake(snowflake: Long): User.NamesAndAddresses =
        provider.newSuspendTransaction {
            val names =
                UserNameTable.selectAll()
                    .where { UserNameTable.user eq snowflake }
                    .mapTo(mutableSetOf()) { it[UserNameTable.name] }
            val addresses =
                UserAddressTable.selectAll()
                    .where { UserAddressTable.user eq snowflake }
                    .mapTo(mutableSetOf()) {
                        InetAddress.getByAddress(it[UserAddressTable.address])
                    }
            User.NamesAndAddresses(names, addresses)
        }

    override suspend fun searchUserByName(query: String): List<User> =
        provider.newSuspendTransaction {
            (UserTable leftJoin UserNameTable)
                .selectAll()
                .where { UserNameTable.name like "%${query}%" }
                .groupBy(UserTable.id)
                .map { it.toUser() }
        }

    override suspend fun incrementJoins(identity: Identity.Mindustry): Unit =
        provider.newSuspendTransaction {
            val user = getByIdentity(identity)

            UserTable.update({ UserTable.id eq user.snowflake }) {
                it[lastName] = identity.name.stripMindustryColors()
                it[lastAddress] = identity.address.address
                it[timesJoined] = timesJoined.plus(1)
                it[lastJoin] = Instant.now()
            }

            UserNameTable.insertIgnore {
                it[UserNameTable.user] = user.snowflake
                it[name] = identity.name.stripMindustryColors()
            }

            UserAddressTable.insertIgnore {
                it[UserAddressTable.user] = user.snowflake
                it[address] = identity.address.address
            }
        }

    override suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean =
        getSettings0(uuid)[setting] ?: setting.default

    override suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean> =
        getSettings0(uuid)

    private suspend fun getSettings0(uuid: MindustryUUID): Map<User.Setting, Boolean> =
        settingsCache.getSuspending(uuid) {
            provider.newSuspendTransaction {
                (UserSettingTable leftJoin UserTable)
                    .select(UserSettingTable.setting, UserSettingTable.value)
                    .where { UserTable.uuid eq uuid.toLongMuuid() }
                    .associate { it[UserSettingTable.setting] to it[UserSettingTable.value] }
            }
        }

    override suspend fun setSetting(
        identity: Identity.Mindustry,
        setting: User.Setting,
        value: Boolean
    ): Unit =
        provider.newSuspendTransaction {
            val user = getByIdentity(identity)
            UserSettingTable.upsert {
                it[UserSettingTable.user] = user.snowflake
                it[UserSettingTable.setting] = setting
                it[UserSettingTable.value] = value
            }
            invalidateSettings(identity.uuid, true)
        }

    override suspend fun setSettings(
        identity: Identity.Mindustry,
        settings: Map<User.Setting, Boolean>
    ): Unit =
        provider.newSuspendTransaction {
            val user = getByIdentity(identity)
            UserSettingTable.batchUpsert(settings.entries) { (setting, value) ->
                this[UserSettingTable.user] = user.snowflake
                this[UserSettingTable.setting] = setting
                this[UserSettingTable.value] = value
            }
            invalidateSettings(identity.uuid, true)
        }

    private fun ResultRow.toUser() =
        User(
            snowflake = this[UserTable.id].value,
            uuid = this[UserTable.uuid].toCRC32Muuid(),
            lastName = this[UserTable.lastName],
            lastAddress = InetAddress.getByAddress(this[UserTable.lastAddress]),
            timesJoined = this[UserTable.timesJoined],
            lastJoin = this[UserTable.lastJoin])

    private suspend fun invalidateSettings(uuid: MindustryUUID, send: Boolean) {
        settingsCache.synchronous().invalidate(uuid)
        if (send) messenger.publish(UserSettingChangeMessage(uuid))
    }

    @Serializable private data class UserSettingChangeMessage(val uuid: MindustryUUID) : Message
}
