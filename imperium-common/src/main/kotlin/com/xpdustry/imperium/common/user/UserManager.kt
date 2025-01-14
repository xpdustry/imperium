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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.SQLProvider
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.isCRC32Muuid
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toLongMuuid
import com.xpdustry.imperium.common.security.Identity
import java.net.InetAddress
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert

interface UserManager {

    suspend fun getByIdentity(identity: Identity.Mindustry): User

    suspend fun findById(id: Int): User?

    suspend fun findByUuid(uuid: MindustryUUID): User?

    suspend fun findByLastAddress(address: InetAddress): List<User>

    suspend fun findNamesAndAddressesById(id: Int): User.NamesAndAddresses

    suspend fun searchUserByName(query: String): List<User>

    suspend fun incrementJoins(identity: Identity.Mindustry)

    suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean

    suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean>

    suspend fun setSetting(uuid: MindustryUUID, setting: User.Setting, value: Boolean)
}

class SimpleUserManager(private val provider: SQLProvider, private val messenger: Messenger) :
    UserManager, ImperiumApplication.Listener {
    private val userCreateMutex = Mutex()

    override fun onImperiumInit() {
        provider.newTransaction { SchemaUtils.create(UserTable, UserNameTable, UserAddressTable, UserSettingTable) }
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

                val now = Instant.now()
                val identifier =
                    UserTable.insertAndGetId {
                            it[uuid] = identity.uuid.toLongMuuid()
                            it[lastName] = identity.name.stripMindustryColors()
                            it[lastAddress] = identity.address.address
                            it[firstJoin] = now
                        }
                        .value

                UserNameTable.insert {
                    it[UserNameTable.user] = identifier
                    it[name] = identity.name.stripMindustryColors()
                }

                UserAddressTable.insert {
                    it[UserAddressTable.user] = identifier
                    it[address] = identity.address.address
                }

                User(
                    id = identifier,
                    uuid = identity.uuid,
                    lastName = identity.name.stripMindustryColors(),
                    lastAddress = identity.address,
                    lastJoin = Instant.EPOCH,
                    firstJoin = now,
                )
            }
        }

    override suspend fun findById(id: Int): User? =
        provider.newSuspendTransaction { UserTable.selectAll().where { UserTable.id eq id }.firstOrNull()?.toUser() }

    override suspend fun findByUuid(uuid: MindustryUUID): User? {
        if (!uuid.isCRC32Muuid()) return null
        return provider.newSuspendTransaction {
            UserTable.selectAll().where { UserTable.uuid eq uuid.toLongMuuid() }.firstOrNull()?.toUser()
        }
    }

    override suspend fun findByLastAddress(address: InetAddress): List<User> =
        provider.newSuspendTransaction {
            UserTable.selectAll().where { UserTable.lastAddress eq address.address }.map { it.toUser() }
        }

    override suspend fun findNamesAndAddressesById(id: Int): User.NamesAndAddresses =
        provider.newSuspendTransaction {
            val names =
                UserNameTable.selectAll()
                    .where { UserNameTable.user eq id }
                    .mapTo(mutableSetOf()) { it[UserNameTable.name] }
            val addresses =
                UserAddressTable.selectAll()
                    .where { UserAddressTable.user eq id }
                    .mapTo(mutableSetOf()) { InetAddress.getByAddress(it[UserAddressTable.address]) }
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

            UserTable.update({ UserTable.id eq user.id }) {
                it[lastName] = identity.name.stripMindustryColors()
                it[lastAddress] = identity.address.address
                it[timesJoined] = timesJoined.plus(1)
                it[lastJoin] = Instant.now()
            }

            UserNameTable.insertIgnore {
                it[UserNameTable.user] = user.id
                it[name] = identity.name.stripMindustryColors()
            }

            UserAddressTable.insertIgnore {
                it[UserAddressTable.user] = user.id
                it[address] = identity.address.address
            }
        }

    override suspend fun getSetting(uuid: MindustryUUID, setting: User.Setting): Boolean =
        getSettings0(uuid)[setting] ?: setting.default

    override suspend fun getSettings(uuid: MindustryUUID): Map<User.Setting, Boolean> = getSettings0(uuid)

    private suspend fun getSettings0(uuid: MindustryUUID): Map<User.Setting, Boolean> =
        provider.newSuspendTransaction {
            (UserSettingTable leftJoin UserTable)
                .select(UserSettingTable.setting, UserSettingTable.value)
                .where { UserTable.uuid eq uuid.toLongMuuid() }
                .associate { it[UserSettingTable.setting] to it[UserSettingTable.value] }
        }

    override suspend fun setSetting(uuid: MindustryUUID, setting: User.Setting, value: Boolean): Unit =
        provider.newSuspendTransaction {
            val user = findByUuid(uuid) ?: return@newSuspendTransaction
            UserSettingTable.upsert {
                it[UserSettingTable.user] = user.id
                it[UserSettingTable.setting] = setting
                it[UserSettingTable.value] = value
            }
        }

    private fun ResultRow.toUser() =
        User(
            id = this[UserTable.id].value,
            uuid = this[UserTable.uuid].toCRC32Muuid(),
            lastName = this[UserTable.lastName],
            lastAddress = InetAddress.getByAddress(this[UserTable.lastAddress]),
            timesJoined = this[UserTable.timesJoined],
            lastJoin = this[UserTable.lastJoin],
            firstJoin = this[UserTable.firstJoin],
        )
}
