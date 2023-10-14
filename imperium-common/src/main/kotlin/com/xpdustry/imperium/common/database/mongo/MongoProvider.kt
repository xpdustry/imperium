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
import com.mongodb.ReadConcern
import com.mongodb.ServerAddress
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.WriteConcern
import com.mongodb.connection.SslSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.DatabaseConfig
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.Entity
import com.xpdustry.imperium.common.serialization.InetAddressCodecProvider
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.BsonConfiguration
import org.bson.codecs.kotlinx.KotlinSerializerCodec
import org.bson.codecs.kotlinx.defaultSerializersModule

internal interface MongoProvider {
    fun <E : Entity<I>, I : Any> getCollection(
        name: String,
        klass: KClass<E>
    ): MongoEntityCollection<E, I>
}

internal class SimpleMongoProvider(private val config: ImperiumConfig) :
    MongoProvider, ImperiumApplication.Listener {
    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase

    override fun onImperiumInit() {
        if (config.database !is DatabaseConfig.Mongo) {
            throw IllegalStateException("The current database configuration is not Mongo")
        }
        @OptIn(ExperimentalSerializationApi::class)
        val kotlinSerializationCodec =
            object : CodecProvider {
                private val configuration =
                    BsonConfiguration(
                        explicitNulls = true,
                        encodeDefaults = true,
                        classDiscriminator = "_java_class",
                    )

                override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? =
                    KotlinSerializerCodec.create(
                        clazz.kotlin, defaultSerializersModule, configuration)
            }
        val settings =
            MongoClientSettings.builder()
                .applicationName("imperium-${config.server.name}")
                .applyToClusterSettings { cluster ->
                    cluster.hosts(listOf(ServerAddress(config.database.host, config.database.port)))
                    cluster.serverSelectionTimeout(5, TimeUnit.SECONDS)
                }
                .also { settings ->
                    if (config.database.username.isBlank()) {
                        return@also
                    }
                    settings.credential(
                        MongoCredential.createCredential(
                            config.database.username,
                            config.database.authDatabase,
                            config.database.password.value.toCharArray(),
                        ),
                    )
                }
                .writeConcern(WriteConcern.MAJORITY)
                .readConcern(ReadConcern.MAJORITY)
                .serverApi(
                    ServerApi.builder()
                        .version(ServerApiVersion.V1)
                        .deprecationErrors(true)
                        .build(),
                )
                .applyToSslSettings { ssl ->
                    ssl.applySettings(SslSettings.builder().enabled(config.database.ssl).build())
                }
                .codecRegistry(
                    CodecRegistries.fromProviders(
                        kotlinSerializationCodec,
                        // The kotlin serialization codec covers InetAddress, not its subclasses
                        // directly
                        InetAddressCodecProvider,
                        MongoClientSettings.getDefaultCodecRegistry(),
                    ),
                )
                .build()

        client = MongoClient.create(settings)

        try {
            runBlocking { client.listDatabaseNames().collect() }
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to the mongo database", e)
        }

        database = client.getDatabase(config.database.database)
    }

    override fun onImperiumExit() {
        client.close()
    }

    override fun <E : Entity<I>, I : Any> getCollection(
        name: String,
        klass: KClass<E>
    ): MongoEntityCollection<E, I> = MongoEntityCollection(database.getCollection(name, klass.java))
}
