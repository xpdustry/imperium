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
package com.xpdustry.imperium.migrator

import ch.qos.logback.classic.Level
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.misc.toInetAddressOrNull
import com.xpdustry.imperium.common.misc.toShortMuuid
import com.xpdustry.imperium.common.snowflake.SimpleSnowflakeGenerator
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserAddressTable
import com.xpdustry.imperium.common.user.UserNameTable
import com.xpdustry.imperium.common.user.UserTable
import de.mkammerer.snowflakeid.SnowflakeIdGenerator
import de.mkammerer.snowflakeid.options.Options
import de.mkammerer.snowflakeid.time.MonotonicTimeSource
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.time.Instant
import kotlin.math.min
import org.bson.BsonDocument
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// Beware, spaghetti code lies ahead

const val MONGO_DATABASE_URL_SOURCE = ""
const val SQL_DATABASE_URL_SOURCE = ""
const val SQL_DATABASE_URL_TARGET = ""
// For testing the migrator
const val MIGRATE_LIMIT: Int = 5000

object LegacyUuidTable : Table("uuid") {
    val uuid = varchar("uuid", 24)
    val firstJoin = long("firstJoin")
    val lastIp = varchar("lastIp", 64)
    val ips = binary("ips", 512)
    val lastName = varchar("lastName", 40)
    val names = varchar("names", 4096)
    val joins = integer("joins")
}

class DynamicMonotonicTimeSource : MonotonicTimeSource(SimpleSnowflakeGenerator.IMPERIUM_EPOCH) {
    var ticks: Instant = Instant.now()

    override fun getTicks(): Long = ticks.toEpochMilli()
}

typealias UserWithNamesAndAddresses = Pair<User, User.NamesAndAddresses>

val LOGGER: Logger = LoggerFactory.getLogger("imperium-migrator")

fun main() {
    if (SQL_DATABASE_URL_SOURCE.isBlank() || SQL_DATABASE_URL_TARGET.isBlank()) {
        error("Please, insert a proper jdbc url.")
    }

    (LoggerFactory.getLogger("ROOT") as ch.qos.logback.classic.Logger).level = Level.INFO

    val sqlSourceDatabase = Database.connect(SQL_DATABASE_URL_SOURCE)
    val sqlTargetDatabase = Database.connect(SQL_DATABASE_URL_TARGET)
    val mongoClient = MongoClients.create(MONGO_DATABASE_URL_SOURCE)
    val mongoSourceDatabase = mongoClient.getDatabase("nucleus")

    migrateUsers(sqlSourceDatabase, sqlTargetDatabase, mongoSourceDatabase)

    mongoClient.close()
}

fun migrateUsers(
    sqlSourceDatabase: Database,
    sqlTargetDatabase: Database,
    mongoSourceDatabase: MongoDatabase
) {
    val dynamicTimeSource = DynamicMonotonicTimeSource()
    val generator =
        SnowflakeIdGenerator.createCustom(
            SimpleSnowflakeGenerator.STRUCTURE.maxGenerators() - 1,
            dynamicTimeSource,
            SimpleSnowflakeGenerator.STRUCTURE,
            Options(Options.SequenceOverflowStrategy.THROW_EXCEPTION))

    val users = HashMap<MindustryUUID, UserWithNamesAndAddresses>(100_000)

    transaction(sqlTargetDatabase) {
        SchemaUtils.create(UserTable, UserNameTable, UserAddressTable)
        if (UserTable.selectAll().firstOrNull() != null) {
            LOGGER.error("The target must be blank to avoid collisions.")
        }
    }

    transaction(sqlSourceDatabase) {
        for (row in
            LegacyUuidTable.selectAll()
                .orderBy(LegacyUuidTable.firstJoin, SortOrder.ASC)
                .limit(MIGRATE_LIMIT)) {
            val uuid = row[LegacyUuidTable.uuid]
            try {
                if (uuid.toShortMuuid().toCRC32Muuid() != uuid) {
                    LOGGER.error("Found invalid uuid $uuid in legacy database, skipping.")
                    continue
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to check uuid $uuid, skipping.")
                continue
            }
            val lastName = row[LegacyUuidTable.lastName].trim()
            if (lastName.isBlank()) {
                LOGGER.error("User with uuid $uuid has empty username, skipping.")
                continue
            }
            val lastAddress = row[LegacyUuidTable.lastIp].toInetAddressOrNull()
            if (lastAddress == null) {
                LOGGER.error("User with uuid $uuid has invalid last address, skipping.")
                continue
            }
            val firstJoin = Instant.ofEpochMilli(row[LegacyUuidTable.firstJoin])
            if (firstJoin < SimpleSnowflakeGenerator.IMPERIUM_EPOCH) {
                LOGGER.error("User with uuid $uuid is older than the server ???")
                continue
            }
            dynamicTimeSource.ticks = firstJoin
            val snowflake = generator.next()
            val user =
                User(
                    snowflake = snowflake,
                    uuid = uuid,
                    lastName = lastName,
                    lastAddress = lastAddress,
                    timesJoined = row[LegacyUuidTable.joins],
                    lastJoin = SimpleSnowflakeGenerator.IMPERIUM_EPOCH)

            val names =
                row[LegacyUuidTable.names]
                    .split("\n")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .toSet()
            val addresses = mutableSetOf<InetAddress>()
            val stream = ByteArrayInputStream(row[LegacyUuidTable.ips])
            while (stream.available() > 4) {
                val address =
                    try {
                        InetAddress.getByAddress(stream.readNBytes(4))
                    } catch (e: Exception) {
                        LOGGER.error("Failed to parse address of $uuid, skipping.")
                        continue
                    }
                addresses += address
            }

            users[user.uuid] = user to User.NamesAndAddresses(names, addresses)
            if (users.size % 500 == 0) {
                LOGGER.info("Loaded ${users.size} chaotic users.")
            }
        }

        LOGGER.info("Recovered ${users.size} from chaotic.")
    }

    val chaoticUserCount = users.size

    for (document in
        mongoSourceDatabase
            .getCollection("users", BsonDocument::class.java)
            .find(Filters.gte("times_joined", 2))
            .limit(MIGRATE_LIMIT)) {
        try {
            // This shit is so cursed
            dynamicTimeSource.ticks = Instant.now()
            var snowflake: Snowflake
            while (true) {
                try {
                    snowflake = generator.next()
                    break
                } catch (e: Exception) {
                    dynamicTimeSource.ticks = dynamicTimeSource.ticks.plusMillis(1)
                }
            }
            val lastName = document["last_name"]!!.asString().value.trim()
            if (lastName.isBlank()) {
                LOGGER.warn("ailed to migrate xpdustry user due to blank username: $document")
            }
            val user =
                User(
                    snowflake = snowflake,
                    uuid = document["_id"]!!.asString().value,
                    lastName = lastName,
                    lastAddress = document["last_address"]!!.asString().value.toInetAddress(),
                    timesJoined = document["times_joined"]!!.asInt32().value,
                    lastJoin = SimpleSnowflakeGenerator.IMPERIUM_EPOCH)

            val data =
                User.NamesAndAddresses(
                    names =
                        document["names"]!!
                            .asArray()
                            .map { it.asString().value }
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .toSet(),
                    addresses =
                        document["addresses"]!!
                            .asArray()
                            .mapNotNull { it.asString().value.toInetAddressOrNull() }
                            .toSet())

            if (user.uuid in users) {
                val (previousUser, previousData) = users[user.uuid]!!
                users[user.uuid] =
                    previousUser.copy(
                        snowflake = min(previousUser.snowflake, user.snowflake),
                        timesJoined = previousUser.timesJoined + user.timesJoined) to
                        previousData.copy(
                            previousData.names + data.names,
                            previousData.addresses + data.addresses)
            } else {
                users[user.uuid] = user to data
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to migrate xpdustry user: $document", e)
        }
    }

    LOGGER.info("Recovered ${users.size - chaoticUserCount} from xpdustry.")
    LOGGER.info("Loaded ${users.size} users in total, proceeding to insert.")

    transaction(sqlTargetDatabase) {
        UserTable.batchInsert(users.values) { (user, _) ->
            this[UserTable.id] = user.snowflake
            this[UserTable.uuid] = user.uuid.toShortMuuid()
            this[UserTable.lastName] = user.lastName.stripMindustryColors()
            this[UserTable.lastAddress] = user.lastAddress.address
            this[UserTable.timesJoined] = user.timesJoined
            this[UserTable.lastJoin] = user.lastJoin
        }

        UserNameTable.batchUpsert(
            users.values.asSequence().flatMap { (user, data) ->
                data.names.map { user.snowflake to it }
            }) { (snowflake, name) ->
                this[UserNameTable.user] = snowflake
                this[UserNameTable.name] = name.stripMindustryColors()
            }

        UserAddressTable.batchUpsert(
            users.values.asSequence().flatMap { (user, data) ->
                data.addresses.map { user.snowflake to it }
            }) { (snowflake, address) ->
                this[UserAddressTable.user] = snowflake
                this[UserAddressTable.address] = address.address
            }
    }

    LOGGER.info("FINISHED MIGRATING USERS, YAY")
}
