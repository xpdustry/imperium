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
package com.xpdustry.foundation.mindustry

import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.xpdustry.foundation.common.annotation.FoundationDir
import com.xpdustry.foundation.common.application.FoundationMetadata
import com.xpdustry.foundation.common.application.FoundationPlatform
import com.xpdustry.foundation.common.application.KotlinAbstractModule
import com.xpdustry.foundation.common.config.FoundationConfig
import com.xpdustry.foundation.common.network.ServerInfo
import com.xpdustry.foundation.common.version.FoundationVersion
import com.xpdustry.foundation.mindustry.chat.ChatMessagePipeline
import com.xpdustry.foundation.mindustry.chat.SimpleChatMessagePipeline
import com.xpdustry.foundation.mindustry.command.FoundationPluginCommandManager
import com.xpdustry.foundation.mindustry.history.BlockHistory
import com.xpdustry.foundation.mindustry.history.SimpleBlockHistory
import com.xpdustry.foundation.mindustry.placeholder.PlaceholderPipeline
import com.xpdustry.foundation.mindustry.placeholder.SimplePlaceholderManager
import com.xpdustry.foundation.mindustry.verification.SimpleVerificationPipeline
import com.xpdustry.foundation.mindustry.verification.VerificationPipeline
import java.nio.file.Path

class FoundationMindustryModule(private val plugin: com.xpdustry.foundation.mindustry.FoundationPlugin) : KotlinAbstractModule() {
    override fun configure() {
        bind(ServerInfo::class)
            .provider(com.xpdustry.foundation.mindustry.MindustryServerInfoProvider::class)
            .singleton()

        bind(BlockHistory::class)
            .implementation(SimpleBlockHistory::class)
            .singleton()
    }

    @Provides @Singleton @FoundationDir
    fun provideDataDir(): Path = plugin.directory

    @Provides @Singleton
    fun provideMetadata(config: FoundationConfig): FoundationMetadata = FoundationMetadata(
        config.mindustry.serverName.lowercase().replace(" ", "-"),
        FoundationPlatform.MINDUSTRY,
        FoundationVersion.parse(plugin.descriptor.version),
    )

    @Provides
    @Named("client")
    fun provideClientCommandManager(): FoundationPluginCommandManager = plugin.clientCommandManager

    @Provides
    @Named("server")
    fun provideServerCommandManager(): FoundationPluginCommandManager = plugin.serverCommandManager

    @Provides @Singleton
    fun provideVerificationPipeline(): VerificationPipeline = SimpleVerificationPipeline()

    @Provides @Singleton
    fun provideChatPipeline(): ChatMessagePipeline = SimpleChatMessagePipeline()

    @Provides @Singleton
    fun providePlaceholderService(): PlaceholderPipeline = SimplePlaceholderManager()
}
