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
package com.xpdustry.foundation.common.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.foundation.common.inject.InstanceFactory
import com.xpdustry.foundation.common.inject.InstanceManager
import com.xpdustry.foundation.common.inject.get
import com.xpdustry.foundation.common.misc.LoggerDelegate
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

class FoundationConfigFactory : InstanceFactory<FoundationConfig> {
    override fun create(instances: InstanceManager): FoundationConfig {
        val file = instances.get<Path>("directory").resolve("config.yaml")
        if (file.notExists()) {
            logger.warn("Config file not found, please create one at ${file.absolutePathString()}")
            return FoundationConfig()
        }
        return ConfigLoaderBuilder.empty()
            .withClassLoader(FoundationConfigFactory::class.java.classLoader)
            .addDefaultDecoders()
            .addDefaultPreprocessors()
            .addDefaultParamMappers()
            .addDefaultPropertySources()
            .addDefaultParsers() // YamlParser is loaded via ServiceLoader here
            .addPathSource(file)
            .addDecoder(ColorDecoder())
            .addDecoder(HiddenStringDecoder())
            .strict()
            .build()
            .loadConfigOrThrow()
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
