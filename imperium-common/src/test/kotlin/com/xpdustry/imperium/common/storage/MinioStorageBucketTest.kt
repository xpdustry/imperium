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
package com.xpdustry.imperium.common.storage

import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.application.SimpleImperiumApplication
import com.xpdustry.imperium.common.commonModule
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.StorageConfig
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

// TODO Add more tests + cleanup
@Testcontainers
class MinioStorageBucketTest {
    private lateinit var application: SimpleImperiumApplication
    private lateinit var storage: MinioStorageBucket

    @BeforeEach
    fun init() {
        application = SimpleImperiumApplication(MODULE)
        storage = application.instances.get<StorageBucket>() as MinioStorageBucket
        application.init()
    }

    @Test
    fun `test s3object simple`() = runTest {
        val object1 = storage.getObject("test")
        Assertions.assertFalse(object1.exists)

        object1.putData("test".byteInputStream())

        val object2 = storage.getObject("test")
        Assertions.assertTrue(object2.exists)
        Assertions.assertEquals("test", object2.name)
        Assertions.assertEquals("test", object2.getData().bufferedReader().readText())

        val objects = storage.listObjects().toList()
        Assertions.assertEquals(1, objects.size)
        Assertions.assertEquals("test", objects[0].name)

        storage.getObject("test").delete()

        Assertions.assertTrue(storage.listObjects().toList().isEmpty())
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    companion object {
        // Stolen from testcontainers-dotnet, hehe...
        @Container
        private val MINIO_CONTAINER = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withCommand("server", "/data")
            .waitingFor(
                Wait.forHttp("/minio/health/live").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)),
            )
        private val MODULE = module("minio-storage-test") {
            include(commonModule())
            single<ImperiumConfig> {
                ImperiumConfig(storage = StorageConfig.Minio(port = MINIO_CONTAINER.firstMappedPort))
            }
        }
    }
}
