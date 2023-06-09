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
package com.xpdustry.foundation.common.configuration

import com.google.inject.Provider
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addPathSource
import com.sksamuel.hoplite.yaml.YamlParser
import com.xpdustry.foundation.common.application.FoundationMetadata
import jakarta.inject.Inject

class FoundationConfigProvider @Inject constructor(metadata: FoundationMetadata) : Provider<FoundationConfig> {

    private val loader = ConfigLoaderBuilder.default()
        .addFileExtensionMapping("yaml", YamlParser())
        .addPathSource(metadata.directory.resolve("config.yaml"))
        .build()

    override fun get(): FoundationConfig {
        return loader.loadConfigOrThrow()
    }
}
