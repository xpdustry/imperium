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
import com.mongodb.reactivestreams.client.MongoDatabase
import com.xpdustry.foundation.common.application.FoundationListener
import com.xpdustry.foundation.common.configuration.FoundationConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class SimpleMongoProvider @Inject constructor(private val config: FoundationConfig) : MongoProvider,
    FoundationListener {

    override lateinit var database: MongoDatabase
    private lateinit var client: MongoClient

    override fun onFoundationInit() {
        client = MongoClients.create(MongoClientSettings.builder()
            .applicationName("Foundation")
            .applyToClusterSettings {
                it.hosts(listOf(ServerAddress(config.mongo.host, config.mongo.port)))
            }
            .credential(
                MongoCredential.createCredential(
                    config.mongo.username,
                    config.mongo.authDatabase,
                    config.mongo.password.value.toCharArray(),
                ),
            )
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
            .build())

        // Check if client is correctly authenticated
        Flux.from(client.listDatabaseNames())
            .filter { it == config.mongo.database }
            .switchIfEmpty(Mono.error(IllegalStateException("MongoDB authentication failed")))
            .blockFirst()

        database = client.getDatabase(config.mongo.database)
    }

    override fun onFoundationExit() {
        client.close()
    }
}