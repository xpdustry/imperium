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
package com.xpdustry.imperium.common.storage

import com.xpdustry.imperium.common.application.BaseImperiumApplication
import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.StorageConfig
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.registerCommonModule
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class MinioStorageBucketTest {

    @Container private var minio = MinIOContainer(DockerImageName.parse("minio/minio:latest"))
    private lateinit var application: BaseImperiumApplication
    private lateinit var storage: MinioStorageBucket

    @BeforeEach
    fun init() {
        application = BaseImperiumApplication(LoggerFactory.getLogger(this::class.java))
        application.instances.registerCommonModule()
        application.instances.registerStorageTestModule()
        storage = application.instances.get<StorageBucket>() as MinioStorageBucket
        application.init()
    }

    @AfterEach
    fun exit() {
        application.exit(ExitStatus.EXIT)
    }

    @Test
    fun `test object`() = runTest {
        val obj = storage.getObject("test")
        assertFalse(obj.exists)

        obj.putData("content".byteInputStream())
        assertTrue(obj.exists)
        assertTrue(storage.getObject("test").exists)
        assertEquals("test", obj.name)
        assertEquals("content", obj.getData().bufferedReader().readText())

        obj.delete()
        assertFalse(obj.exists)
        assertFalse(storage.getObject("test").exists)
    }

    private fun MutableInstanceManager.registerStorageTestModule() {
        provider<ImperiumConfig> { ImperiumConfig(storage = StorageConfig.Minio(port = minio.firstMappedPort)) }
    }
}
