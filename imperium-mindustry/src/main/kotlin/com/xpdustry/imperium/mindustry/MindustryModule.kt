/*
 * Imperium, the software collection powering the Xpdustry network.
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
 *
 */
package com.xpdustry.imperium.mindustry

import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.imperium.common.CommonModule
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.module
import com.xpdustry.imperium.common.inject.single
import com.xpdustry.imperium.common.localization.LocalizationSource
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.bridge.MindustryPlayerTracker
import com.xpdustry.imperium.mindustry.chat.ChatMessagePipeline
import com.xpdustry.imperium.mindustry.chat.SimpleChatMessagePipeline
import com.xpdustry.imperium.mindustry.history.BlockHistory
import com.xpdustry.imperium.mindustry.history.SimpleBlockHistory
import com.xpdustry.imperium.mindustry.localization.DistributorLocalisationSource
import com.xpdustry.imperium.mindustry.misc.getMindustryServerInfo
import com.xpdustry.imperium.mindustry.placeholder.PlaceholderPipeline
import com.xpdustry.imperium.mindustry.placeholder.SimplePlaceholderPipeline
import com.xpdustry.imperium.mindustry.security.FreezeManager
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.SimpleFreezeManager
import com.xpdustry.imperium.mindustry.security.SimpleGatekeeperPipeline
import java.nio.file.Path
import java.util.function.Supplier

@Suppress("FunctionName")
fun MindustryModule(plugin: ImperiumPlugin) =
    module("mindustry") {
        include(CommonModule())

        single<BlockHistory> { SimpleBlockHistory(get()) }

        single<MindustryPlugin> { plugin }

        single<GatekeeperPipeline> { SimpleGatekeeperPipeline() }

        single<ChatMessagePipeline> { SimpleChatMessagePipeline() }

        single<PlaceholderPipeline> { SimplePlaceholderPipeline() }

        single<Path>("directory") { plugin.directory }

        single<ServerConfig.Mindustry> {
            get<ImperiumConfig>().server as? ServerConfig.Mindustry
                ?: error("The current server configuration is not Mindustry")
        }

        single<LocalizationSource> { DistributorLocalisationSource(get()) }

        single<Supplier<Discovery.Data>>("discovery") { Supplier(::getMindustryServerInfo) }

        single<ImperiumVersion> { ImperiumVersion.parse(get<MindustryPlugin>().metadata.version) }

        single<PlayerTracker> { MindustryPlayerTracker(get(), get(), get()) }

        single<FreezeManager> { SimpleFreezeManager(get(), get(), get(), get()) }
    }
