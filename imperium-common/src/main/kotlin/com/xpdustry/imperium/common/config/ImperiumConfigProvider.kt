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
package com.xpdustry.imperium.common.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.KebabCaseParamMapper
import com.sksamuel.hoplite.addPathSource
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.InstanceProvider
import com.xpdustry.imperium.common.inject.get
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

class ImperiumConfigProvider : InstanceProvider<ImperiumConfig> {
    override fun create(instances: InstanceManager): ImperiumConfig {
        val file = instances.get<Path>("directory").resolve("config.yaml")
        if (file.notExists()) {
            error("A config file is required. Please create one at ${file.absolutePathString()}")
        }
        return ConfigLoaderBuilder.empty()
            .withClassLoader(ImperiumConfigProvider::class.java.classLoader)
            .addDefaultDecoders()
            .addDefaultPreprocessors()
            .addDefaultParamMappers()
            .addParameterMapper(KebabCaseParamMapper)
            .addDefaultPropertySources()
            .addDefaultParsers() // YamlParser is loaded via ServiceLoader here
            .addPathSource(file)
            .addDecoder(ColorDecoder())
            .strict()
            .build()
            .loadConfigOrThrow()
    }
}
