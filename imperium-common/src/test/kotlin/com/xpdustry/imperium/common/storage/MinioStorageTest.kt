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
class MinioStorageTest {
    private lateinit var application: SimpleImperiumApplication
    private lateinit var storage: MinioStorage

    @BeforeEach
    fun init() {
        application = SimpleImperiumApplication(MODULE)
        storage = application.instances.get<Storage>() as MinioStorage
        application.init()
    }

    @Test
    fun `test bucket methods`() = runTest {
        Assertions.assertNull(storage.getBucket("test"))

        val bucket1 = storage.getBucket("test", create = true)
        Assertions.assertNotNull(bucket1)
        Assertions.assertEquals("test", bucket1!!.name)

        val bucket2 = storage.getBucket("test")
        Assertions.assertNotNull(bucket2)
        Assertions.assertEquals("test", bucket2!!.name)

        val buckets = storage.listBuckets()
        Assertions.assertEquals(1, buckets.size)
        Assertions.assertEquals("test", buckets[0].name)

        storage.deleteBucket("test")

        Assertions.assertTrue(storage.listBuckets().isEmpty())
    }

    @Test
    fun `test object methods`() = runTest {
        val bucket = storage.getBucket("test", create = true)!!
        val object1 = bucket.getObject("test")
        Assertions.assertNull(object1)

        bucket.putObject("test", "test".byteInputStream())

        val object2 = bucket.getObject("test")!!
        Assertions.assertEquals("test", object2.name)
        Assertions.assertEquals("test", object2.getStream().bufferedReader().readText())

        val objects = bucket.listObjects()
        Assertions.assertEquals(1, objects.size)
        Assertions.assertEquals("test", objects[0].name)

        bucket.deleteObject("test")

        Assertions.assertTrue(bucket.listObjects().toList().isEmpty())
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
