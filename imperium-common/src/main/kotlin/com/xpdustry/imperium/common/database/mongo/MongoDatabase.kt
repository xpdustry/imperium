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
package com.xpdustry.imperium.common.database.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.connection.SslSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.application.ImperiumMetadata
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.Account
import com.xpdustry.imperium.common.database.AccountManager
import com.xpdustry.imperium.common.database.Achievement
import com.xpdustry.imperium.common.database.Database
import com.xpdustry.imperium.common.database.LegacyAccount
import com.xpdustry.imperium.common.database.LegacyAccountManager
import com.xpdustry.imperium.common.database.Punishment
import com.xpdustry.imperium.common.database.PunishmentManager
import com.xpdustry.imperium.common.database.User
import com.xpdustry.imperium.common.database.UserManager
import com.xpdustry.imperium.common.misc.toValueFlux
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.Conventions
import org.bson.codecs.pojo.PojoCodecProvider
import java.time.Duration

class MongoDatabase(private val config: ImperiumConfig, private val metadata: ImperiumMetadata) : Database, ImperiumApplication.Listener {

    private lateinit var client: MongoClient

    override lateinit var users: UserManager
    override lateinit var punishments: PunishmentManager
    override lateinit var accounts: AccountManager
    override lateinit var legacyAccounts: LegacyAccountManager

    override fun onImperiumInit() {
        val settings = MongoClientSettings.builder()
            .applicationName("imperium-${metadata.name}")
            .applyToClusterSettings { cluster ->
                cluster.hosts(listOf(ServerAddress(config.mongo.host, config.mongo.port)))
            }
            .also { settings ->
                if (config.mongo.username.isBlank()) {
                    return@also
                }
                settings.credential(
                    MongoCredential.createCredential(
                        config.mongo.username,
                        config.mongo.authDatabase,
                        config.mongo.password.value.toCharArray(),
                    ),
                )
            }
            .serverApi(
                ServerApi.builder()
                    .version(ServerApiVersion.V1)
                    .strict(true)
                    .deprecationErrors(true)
                    .build(),
            )
            .applyToSslSettings { ssl ->
                ssl.applySettings(SslSettings.builder().enabled(config.mongo.ssl).build())
            }
            .codecRegistry(
                CodecRegistries.fromProviders(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    PojoCodecProvider.builder()
                        .register(Account::class.java)
                        .register(Account.Friend::class.java)
                        .register(Account.Session::class.java)
                        .register(LegacyAccount::class.java)
                        .register(User::class.java)
                        .register(Punishment::class.java)
                        .register(Achievement.Progression::class.java)
                        .conventions(
                            listOf(
                                Conventions.CLASS_AND_PROPERTY_CONVENTION,
                                Conventions.ANNOTATION_CONVENTION,
                                Conventions.SET_PRIVATE_FIELDS_CONVENTION,
                                SnakeCaseConvention,
                                UnsafeInstanciationConvention,
                            ),
                        )
                        .build(),
                    CodecRegistries.fromCodecs(
                        DurationCodec(),
                        HashCodec(),
                    ),
                    InetAddressCodecProvider(),
                ),
            )
            .build()

        client = MongoClients.create(settings)

        // Check if client is correctly authenticated
        try {
            client.listDatabaseNames().toValueFlux()
                .collectList()
                .block(Duration.ofSeconds(5L))
        } catch (e: Exception) {
            throw RuntimeException("Failed to authenticate to MongoDB", e)
        }

        val database = client.getDatabase(config.mongo.database)
        users = MongoUserManager(database.getCollection("users", User::class.java))
        punishments = MongoPunishmentManager(database.getCollection("punishments", Punishment::class.java))
        accounts = MongoAccountManager(database.getCollection("accounts", Account::class.java))
        legacyAccounts = MongoLegacyAccountManager(database.getCollection("accounts_legacy", LegacyAccount::class.java))
    }

    override fun onImperiumExit() {
        client.close()
    }
}
