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
package com.xpdustry.foundation.mindustry.core

import com.google.inject.Provides
import com.google.inject.Singleton
import com.xpdustry.foundation.common.annotation.FoundationDir
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.application.FoundationPlatform
import com.xpdustry.foundation.common.application.KotlinAbstractModule
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.network.ServerInfo
import com.xpdustry.foundation.common.version.FoundationVersion
import com.xpdustry.foundation.mindustry.core.annotation.ClientSide
import com.xpdustry.foundation.mindustry.core.annotation.ServerSide
import com.xpdustry.foundation.mindustry.core.command.FoundationPluginCommandManager
import java.nio.file.Path

class FoundationMindustryModule(private val plugin: FoundationPlugin) : KotlinAbstractModule() {
    override fun configure() {
        bind(ServerInfo::class)
            .provider(MindustryServerInfoProvider::class)
            .singleton()

        bind(FoundationPluginCommandManager::class)
            .annotated(ClientSide::class)
            .instance(plugin.clientCommandManager)

        bind(FoundationPluginCommandManager::class)
            .annotated(ServerSide::class)
            .instance(plugin.serverCommandManager)
    }

    @Provides @Singleton @FoundationDir
    fun provideDataDir(): Path = plugin.directory

    @Provides @Singleton
    fun provideMetadata(config: FoundationConfig): FoundationMetadata = FoundationMetadata(
        config.mindustry.serverName.replace(" ", "-"),
        FoundationPlatform.MINDUSTRY,
        FoundationVersion.parse(plugin.descriptor.version),
    )
}
