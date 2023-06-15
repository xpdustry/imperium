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
package com.xpdustry.foundation.common.database.mongo

import com.google.inject.Inject
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.connection.SslSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.database.model.Punishment
import com.xpdustry.foundation.common.database.model.PunishmentManager
import com.xpdustry.foundation.common.database.model.User
import com.xpdustry.foundation.common.database.model.UserManager
import com.xpdustry.foundation.common.database.mongo.codec.DurationCodec
import com.xpdustry.foundation.common.database.mongo.codec.InetAddressCodec
import com.xpdustry.foundation.common.database.mongo.convention.SnakeCaseConvention
import com.xpdustry.foundation.common.database.mongo.convention.UnsafeInstanciationConvention
import com.xpdustry.foundation.common.misc.toValueFlux
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.Conventions
import org.bson.codecs.pojo.PojoCodecProvider
import java.time.Duration

class MongoDatabase @Inject constructor(private val config: FoundationConfig, private val metadata: FoundationMetadata) : Database, FoundationListener {

    private lateinit var client: MongoClient
    override lateinit var users: UserManager
    override lateinit var punishments: PunishmentManager

    override fun onFoundationInit() {
        client = MongoClients.create(
            MongoClientSettings.builder()
                .applicationName("foundation-${metadata.name}")
                .applyToClusterSettings {
                    it.hosts(listOf(ServerAddress(config.mongo.host, config.mongo.port)))
                }
                .also {
                    if (config.mongo.username.isBlank()) {
                        return@also
                    }
                    it.credential(
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
                .applyToSslSettings {
                    it.applySettings(SslSettings.builder().enabled(config.mongo.ssl).build())
                }
                .codecRegistry(
                    CodecRegistries.fromProviders(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        PojoCodecProvider.builder()
                            .register(User::class.java)
                            .register(Punishment::class.java)
                            .conventions(
                                Conventions.DEFAULT_CONVENTIONS + listOf(
                                    SnakeCaseConvention,
                                    Conventions.SET_PRIVATE_FIELDS_CONVENTION,
                                    UnsafeInstanciationConvention,
                                ),
                            )
                            .build(),
                        CodecRegistries.fromCodecs(
                            DurationCodec(),
                            InetAddressCodec(),
                        ),
                    ),
                )
                .build(),
        )

        // Check if client is correctly authenticated
        client.listDatabaseNames().toValueFlux()
            .onErrorMap { IllegalStateException("MongoDB authentication failed", it) }
            .collectList()
            .block(Duration.ofSeconds(5L))

        val database = client.getDatabase(config.mongo.database)
        users = MongoUserManager(database.getCollection("users", User::class.java))
        punishments = MongoPunishmentManager(database.getCollection("punishments", Punishment::class.java))
    }

    override fun onFoundationExit() {
        client.close()
    }
}
