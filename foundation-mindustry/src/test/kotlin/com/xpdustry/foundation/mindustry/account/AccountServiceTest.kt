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
package com.xpdustry.foundation.mindustry.account

import com.google.inject.Provides
import com.google.inject.Singleton
import com.xpdustry.foundation.common.FoundationCommonModule
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.application.FoundationPlatform
import com.xpdustry.foundation.common.application.KotlinAbstractModule
import com.xpdustry.foundation.common.application.SimpleFoundationApplication
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.config.MongoConfig
import com.xpdustry.foundation.common.database.Database
import com.xpdustry.foundation.common.misc.ExitStatus
import com.xpdustry.foundation.common.network.ServerInfo
import com.xpdustry.foundation.common.version.FoundationVersion
import fr.xpdustry.distributor.api.util.MUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import reactor.test.StepVerifier
import java.util.Base64
import kotlin.random.Random

@Testcontainers
class AccountServiceTest {

    private lateinit var application: SimpleFoundationApplication
    private lateinit var accountService: AccountService

    @BeforeEach
    fun init() {
        application = SimpleFoundationApplication(
            common = FoundationCommonModule(),
            production = false,
            implementation = object : KotlinAbstractModule() {
                override fun configure() {
                    bind(FoundationConfig::class).instance(FoundationConfig(mongo = MongoConfig(port = mongo.getMappedPort(27017))))
                    bind(FoundationMetadata::class).instance(FoundationMetadata("test", FoundationPlatform.MINDUSTRY, FoundationVersion(1, 1, 1)))
                }

                @Provides @Singleton
                fun provideServerInfo(metadata: FoundationMetadata): ServerInfo = ServerInfo(metadata, null)
            },
        )
        accountService = application.instance(SimpleAccountService::class)
        application.init()
    }

    @AfterEach
    fun cleanup() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun test_account_registration() {
        val muuid = randomMUUID()

        StepVerifier.create(accountService.register(muuid, "test1256!".toCharArray()))
            .expectNext(RegisterResult.Success)
            .verifyComplete()

        StepVerifier.create(application.instance(Database::class).accounts.findByUuid(muuid.uuid))
            .expectNextMatches { it.uuids.contains(muuid.uuid) }
            .verifyComplete()
    }

    private fun randomMUUID(): MUUID {
        val uuidBytes = ByteArray(16)
        Random.nextBytes(uuidBytes)
        val usidBytes = ByteArray(8)
        Random.nextBytes(usidBytes)
        return MUUID.of(Base64.getEncoder().encodeToString(uuidBytes), Base64.getEncoder().encodeToString(usidBytes))
    }

    companion object {
        @Container
        private val mongo = MongoDBContainer(DockerImageName.parse("mongo:6"))
    }
}
